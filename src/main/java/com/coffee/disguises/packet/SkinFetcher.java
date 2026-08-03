package com.coffee.disguises.packet;

import com.coffee.disguises.DisguisesMod;
import com.coffee.disguises.util.BoundedCache;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.Proxy;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Fetches GameProfiles with skin texture data for player disguises.
 *
 * Uses authlib's YggdrasilAuthenticationService (Apache HttpClient internally)
 * to avoid Fabric/Knot's URLStreamHandlerFactory blocking raw Java HTTP calls.
 */
public class SkinFetcher {

    private static final int CACHE_MAX = 200;
    private static final int BEDROCK_CACHE_MAX = CACHE_MAX * 2;
    private static final String GEYSER_API_BASE = "https://api.geysermc.org/v2";

    private static final YggdrasilAuthenticationService AUTH_SERVICE =
            new YggdrasilAuthenticationService(Proxy.NO_PROXY);

    private static final GameProfileRepository PROFILE_REPO =
            AUTH_SERVICE.createProfileRepository();

    private static final MinecraftSessionService SESSION_SERVICE =
            AUTH_SERVICE.createMinecraftSessionService();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "disguises-skin-fetcher");
        t.setDaemon(true);
        return t;
    });

    private static final ConcurrentHashMap<String, CompletableFuture<GameProfile>> IN_FLIGHT =
            new ConcurrentHashMap<>();
    private static final BoundedCache<String, BedrockSkin> BEDROCK_SKINS =
            new BoundedCache<>(BEDROCK_CACHE_MAX);
    private static final AtomicBoolean FLOODGATE_SKIN_LISTENER_REGISTERED =
            new AtomicBoolean(false);

    private static final BoundedCache<String, GameProfile> CACHE =
            new BoundedCache<>(CACHE_MAX);

    private record BedrockIdentity(String xuid, String name) {}
    private record BedrockSkin(String value, String signature) {}
    private record OnlineIdentity(UUID uuid, String name) {}

    // -----------------------------------------------------------------------

    public static void init() {
        tryRegisterFloodgateSkinListener();
    }

    public static void fetchByName(String name, MinecraftServer server, Consumer<GameProfile> cb) {
        runOnServerThread(server, () -> startNameFetch(name, server, cb));
    }

    private static void startNameFetch(
            String name, MinecraftServer server, Consumer<GameProfile> cb) {
        tryRegisterFloodgateSkinListener();
        String key = name.toLowerCase(Locale.ROOT);
        GameProfile hit = CACHE.get(key);
        if (hit != null && hasTextures(hit)) { server.execute(() -> cb.accept(hit)); return; }
        if (hit != null) CACHE.remove(key);

        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        OnlineIdentity onlineIdentity = null;
        if (online != null) {
            GameProfile onlineProfile = online.getGameProfile();
            if (hasTextures(onlineProfile)) {
                CACHE.put(key, onlineProfile);
                server.execute(() -> cb.accept(onlineProfile));
                return;
            }
            onlineIdentity = new OnlineIdentity(online.getUUID(), onlineProfile.name());
        }
        OnlineIdentity snapshot = onlineIdentity;

        IN_FLIGHT.computeIfAbsent(key, k ->
                        CompletableFuture.supplyAsync(() -> resolveByName(name, snapshot), EXECUTOR)
                                .orTimeout(20, TimeUnit.SECONDS)
                                .exceptionally(e -> {
                                    DisguisesMod.LOGGER.warn("Disguises: skin fetch for '{}' failed: {}",
                                            name, rootMessage(e));
                                    return null;
                                })
                                .whenComplete((p, e) -> IN_FLIGHT.remove(k)))
                .thenAccept(p -> server.execute(() -> {
                    if (hasTextures(p)) CACHE.put(key, p);
                    cb.accept(p);
                }));
    }

    public static void fetchByUUID(UUID uuid, MinecraftServer server, Consumer<GameProfile> cb) {
        runOnServerThread(server, () -> startUuidFetch(uuid, server, cb));
    }

    private static void startUuidFetch(
            UUID uuid, MinecraftServer server, Consumer<GameProfile> cb) {
        tryRegisterFloodgateSkinListener();
        String key = "uuid:" + uuid;
        GameProfile hit = CACHE.get(key);
        if (hit != null && hasTextures(hit)) { server.execute(() -> cb.accept(hit)); return; }
        if (hit != null) CACHE.remove(key);

        OnlineIdentity onlineIdentity = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.getUUID().equals(uuid)) continue;
            GameProfile onlineProfile = player.getGameProfile();
            if (hasTextures(onlineProfile)) {
                CACHE.put(key, onlineProfile);
                server.execute(() -> cb.accept(onlineProfile));
                return;
            }
            onlineIdentity = new OnlineIdentity(player.getUUID(), onlineProfile.name());
            break;
        }
        OnlineIdentity snapshot = onlineIdentity;

        IN_FLIGHT.computeIfAbsent(key, k ->
                        CompletableFuture.supplyAsync(() -> resolveByUUID(uuid, snapshot), EXECUTOR)
                                .orTimeout(20, TimeUnit.SECONDS)
                                .exceptionally(e -> {
                                    DisguisesMod.LOGGER.warn("Disguises: skin fetch for UUID {} failed: {}",
                                            uuid, rootMessage(e));
                                    return null;
                                })
                                .whenComplete((p, e) -> IN_FLIGHT.remove(k)))
                .thenAccept(p -> server.execute(() -> {
                    if (hasTextures(p)) CACHE.put(key, p);
                    cb.accept(p);
                }));
    }

    public static void clearCache() {
        CACHE.clear();
        BEDROCK_SKINS.clear();
    }

    private static void runOnServerThread(MinecraftServer server, Runnable action) {
        if (server.isSameThread()) action.run();
        else server.execute(action);
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) cur = cur.getCause();
        String message = cur.getMessage();
        return cur.getClass().getSimpleName() + (message != null ? ": " + message : "");
    }

    private static boolean hasTextures(GameProfile profile) {
        return profile != null && profile.properties().containsKey("textures");
    }

    // -----------------------------------------------------------------------

    private static GameProfile resolveByName(String name, OnlineIdentity online) {
        // 1. Already online — free and instant
        if (online != null) {
            GameProfile bedrockProfile = resolveOnlineBedrock(online.uuid(), online.name());
            if (bedrockProfile != null) return bedrockProfile;
            DisguisesMod.LOGGER.debug(
                    "Disguises: online profile for '{}' has no textures; resolving full profile.",
                    name);
        }

        GameProfile onlineBedrockProfile = resolveOnlineBedrockByName(name);
        if (onlineBedrockProfile != null) return onlineBedrockProfile;

        // 2. Name → UUID via Mojang API (authlib/Apache HTTP, not Java URL)
        try {
            UUID[] found = new UUID[1];

            PROFILE_REPO.findProfilesByNames(new String[]{name}, new ProfileLookupCallback() {
                @Override
                public void onProfileLookupSucceeded(String profileName, UUID uuid) {
                    found[0] = uuid;
                }

                @Override
                public void onProfileLookupFailed(String profileName, Exception e) {
                    DisguisesMod.LOGGER.warn("Disguises: Mojang profile lookup failed for '{}': {}",
                            profileName, e.getMessage());
                }
            });

            if (found[0] == null) {
                DisguisesMod.LOGGER.warn("Disguises: no Mojang account found for '{}'.", name);
            } else {
                // 3. UUID → full profile with skin textures
                GameProfile filled = fillProfile(found[0]);
                if (filled != null) {
                    DisguisesMod.LOGGER.info("Disguises: skin for '{}' fetched via authlib (UUID {}).",
                            name, found[0]);
                    return filled;
                }

                DisguisesMod.LOGGER.warn("Disguises: could not fill skin properties for '{}', using stub.", name);
                return new GameProfile(found[0], name);
            }

        } catch (Exception e) {
            DisguisesMod.LOGGER.warn("Disguises: skin fetch for '{}' failed: {} - {}",
                    name, e.getClass().getSimpleName(), e.getMessage());
            DisguisesMod.LOGGER.debug("Disguises: stack trace:", e);
        }

        // 4. Bedrock gamertag/XUID via Geyser/Floodgate's converted skin API.
        return resolveBedrockByNameOrXuid(name);
    }

    private static GameProfile resolveByUUID(UUID uuid, OnlineIdentity online) {
        // 1. Already online
        if (online != null) {
            GameProfile bedrockProfile = resolveOnlineBedrock(online.uuid(), online.name());
            if (bedrockProfile != null) return bedrockProfile;
        }

        // 2. UUID → full profile with skin textures
        try {
            GameProfile filled = fillProfile(uuid);
            if (filled != null) return filled;
            DisguisesMod.LOGGER.warn("Disguises: could not fetch profile for UUID {}.", uuid);
            return null;
        } catch (Exception e) {
            DisguisesMod.LOGGER.warn("Disguises: skin fetch for UUID {} failed: {} - {}",
                    uuid, e.getClass().getSimpleName(), e.getMessage());
            DisguisesMod.LOGGER.debug("Disguises: stack trace:", e);
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Bedrock/Geyser/Floodgate skin resolution
    // -----------------------------------------------------------------------

    private static GameProfile resolveOnlineBedrock(UUID playerUuid, String fallbackName) {
        BedrockIdentity identity = getFloodgateIdentity(playerUuid, fallbackName);
        if (identity == null) identity = getGeyserIdentity(playerUuid, fallbackName);
        if (identity == null) return null;
        GameProfile profile = fetchBedrockSkin(identity);
        if (profile != null) {
            DisguisesMod.LOGGER.info("Disguises: skin for Bedrock player '{}' fetched via Geyser API.",
                    identity.name());
        }
        return profile;
    }

    private static GameProfile resolveOnlineBedrockByName(String name) {
        GameProfile geyserProfile = resolveOnlineGeyserByName(name);
        if (geyserProfile != null) return geyserProfile;

        Object api = getFloodgateApi();
        if (api == null) return null;

        try {
            Object players = invoke(api, "getPlayers");
            if (!(players instanceof Collection<?> collection)) return null;

            for (Object player : collection) {
                if (!matchesBedrockName(name, player)) continue;

                BedrockIdentity identity = identityFromFloodgatePlayer(player, name);
                GameProfile profile = fetchBedrockSkin(identity);
                if (profile != null) {
                    DisguisesMod.LOGGER.info(
                            "Disguises: skin for online Bedrock player '{}' fetched via Geyser API.",
                            identity.name());
                }
                return profile;
            }
        } catch (Exception e) {
            DisguisesMod.LOGGER.debug("Disguises: Floodgate online player lookup failed: {}",
                    e.getMessage());
        }
        return null;
    }

    private static GameProfile resolveOnlineGeyserByName(String name) {
        Object api = getGeyserApi();
        if (api == null) return null;

        try {
            Object connections = invoke(api, "onlineConnections");
            if (!(connections instanceof Collection<?> collection)) return null;

            for (Object connection : collection) {
                if (!matchesGeyserName(name, connection)) continue;

                BedrockIdentity identity = identityFromGeyserConnection(connection, name);
                GameProfile profile = fetchBedrockSkin(identity);
                if (profile != null) {
                    DisguisesMod.LOGGER.info(
                            "Disguises: skin for online Geyser player '{}' fetched via Geyser API.",
                            identity.name());
                }
                return profile;
            }
        } catch (Exception e) {
            DisguisesMod.LOGGER.debug("Disguises: Geyser online connection lookup failed: {}",
                    e.getMessage());
        }
        return null;
    }

    private static GameProfile resolveBedrockByNameOrXuid(String name) {
        for (String candidate : bedrockNameCandidates(name)) {
            BedrockSkin cached = BEDROCK_SKINS.get("name:" + normalizeName(candidate));
            if (cached != null && cached.value() != null && !cached.value().isBlank()) {
                return buildBedrockProfile("cached:" + candidate, candidate,
                        cached.value(), cached.signature());
            }

            String xuid = candidate.matches("\\d+") ? candidate : resolveXuid(candidate);
            if (xuid == null || xuid.isBlank()) continue;

            GameProfile profile = fetchBedrockSkin(new BedrockIdentity(xuid, candidate));
            if (profile != null) {
                DisguisesMod.LOGGER.info(
                        "Disguises: skin for Bedrock player '{}' fetched via Geyser API (XUID {}).",
                        candidate, xuid);
                return profile;
            }
        }
        return null;
    }

    private static BedrockIdentity getFloodgateIdentity(UUID uuid, String fallbackName) {
        Object api = getFloodgateApi();
        if (api == null) return null;

        try {
            Object player = invoke(api, "getPlayer", UUID.class, uuid);
            return identityFromFloodgatePlayer(player, fallbackName);
        } catch (Exception e) {
            DisguisesMod.LOGGER.debug("Disguises: Floodgate player lookup failed for {}: {}",
                    uuid, e.getMessage());
            return null;
        }
    }

    private static BedrockIdentity getGeyserIdentity(UUID uuid, String fallbackName) {
        Object api = getGeyserApi();
        if (api == null) return null;

        try {
            Object connection = invoke(api, "connectionByUuid", UUID.class, uuid);
            return identityFromGeyserConnection(connection, fallbackName);
        } catch (Exception e) {
            DisguisesMod.LOGGER.debug("Disguises: Geyser connection lookup failed for {}: {}",
                    uuid, e.getMessage());
            return null;
        }
    }

    private static BedrockIdentity identityFromFloodgatePlayer(Object player, String fallbackName) {
        if (player == null) return null;

        String xuid = stringValue(invokeQuietly(player, "getXuid"));
        if (xuid == null || xuid.isBlank()) return null;

        String realName = firstNonBlank(
                stringValue(invokeQuietly(player, "getUsername")),
                stringValue(invokeQuietly(player, "getCorrectUsername")),
                stringValue(invokeQuietly(player, "getJavaUsername")),
                fallbackName
        );
        return new BedrockIdentity(xuid, realName);
    }

    private static boolean matchesBedrockName(String requested, Object floodgatePlayer) {
        String req = normalizeName(requested);
        for (String candidate : new String[]{
                stringValue(invokeQuietly(floodgatePlayer, "getUsername")),
                stringValue(invokeQuietly(floodgatePlayer, "getJavaUsername")),
                stringValue(invokeQuietly(floodgatePlayer, "getCorrectUsername"))
        }) {
            if (candidate != null && normalizeName(candidate).equals(req)) return true;
        }
        return false;
    }

    private static BedrockIdentity identityFromGeyserConnection(Object connection, String fallbackName) {
        if (connection == null) return null;

        String xuid = stringValue(invokeQuietly(connection, "xuid"));
        if (xuid == null || xuid.isBlank()) return null;

        String realName = firstNonBlank(
                stringValue(invokeQuietly(connection, "bedrockUsername")),
                stringValue(invokeQuietly(connection, "javaUsername")),
                fallbackName
        );
        return new BedrockIdentity(xuid, realName);
    }

    private static boolean matchesGeyserName(String requested, Object connection) {
        String req = normalizeName(requested);
        for (String candidate : new String[]{
                stringValue(invokeQuietly(connection, "bedrockUsername")),
                stringValue(invokeQuietly(connection, "javaUsername"))
        }) {
            if (candidate != null && normalizeName(candidate).equals(req)) return true;
        }
        return false;
    }

    private static Object getFloodgateApi() {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            return apiClass.getMethod("getInstance").invoke(null);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Exception e) {
            DisguisesMod.LOGGER.debug("Disguises: Floodgate API unavailable: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void tryRegisterFloodgateSkinListener() {
        if (FLOODGATE_SKIN_LISTENER_REGISTERED.get()) return;

        Object api = getFloodgateApi();
        if (api == null) return;

        try {
            Object eventBus = invoke(api, "getEventBus");
            Class<?> eventClass = Class.forName(
                    "org.geysermc.floodgate.api.event.skin.SkinApplyEvent");
            Method subscribe = eventBus.getClass().getMethod(
                    "subscribe", Class.class, java.util.function.Consumer.class);
            subscribe.setAccessible(true);

            Consumer<Object> consumer = SkinFetcher::cacheFloodgateSkinEvent;
            subscribe.invoke(eventBus, eventClass, consumer);
            FLOODGATE_SKIN_LISTENER_REGISTERED.set(true);
            DisguisesMod.LOGGER.info("Disguises: registered Floodgate skin cache listener.");
        } catch (Exception e) {
            DisguisesMod.LOGGER.debug("Disguises: could not register Floodgate skin listener: {}",
                    e.getMessage());
        }
    }

    private static void cacheFloodgateSkinEvent(Object event) {
        try {
            Object player = invoke(event, "player");
            Object skin = invoke(event, "newSkin");
            if (skin == null) skin = invoke(event, "currentSkin");

            String xuid = stringValue(invokeQuietly(player, "getXuid"));
            String name = firstNonBlank(
                    stringValue(invokeQuietly(player, "getUsername")),
                    stringValue(invokeQuietly(player, "getCorrectUsername")),
                    stringValue(invokeQuietly(player, "getJavaUsername"))
            );
            String value = stringValue(invokeQuietly(skin, "value"));
            String signature = stringValue(invokeQuietly(skin, "signature"));

            if (xuid == null || xuid.isBlank() || value == null || value.isBlank()) return;
            cacheBedrockSkin(new BedrockIdentity(xuid, name), value, signature);
            DisguisesMod.LOGGER.debug("Disguises: cached Floodgate skin for Bedrock player '{}'.",
                    name);
        } catch (Exception e) {
            DisguisesMod.LOGGER.debug("Disguises: could not cache Floodgate skin event: {}",
                    e.getMessage());
        }
    }

    private static Object getGeyserApi() {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            return apiClass.getMethod("api").invoke(null);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Exception e) {
            DisguisesMod.LOGGER.debug("Disguises: Geyser API unavailable: {}", e.getMessage());
            return null;
        }
    }

    private static String resolveXuid(String gamertag) {
        String xuid = resolveXuidWithFloodgate(gamertag);
        if (xuid != null) return xuid;
        return resolveXuidWithGlobalApi(gamertag);
    }

    private static String resolveXuidWithFloodgate(String gamertag) {
        Object api = getFloodgateApi();
        if (api == null) return null;

        try {
            Object futureObj = invoke(api, "getXuidFor", String.class, gamertag);
            if (futureObj instanceof CompletableFuture<?> future) {
                return stringValue(future.get(5, TimeUnit.SECONDS));
            }
        } catch (Exception e) {
            DisguisesMod.LOGGER.debug("Disguises: Floodgate XUID lookup failed for '{}': {}",
                    gamertag, e.getMessage());
        }
        return null;
    }

    private static String resolveXuidWithGlobalApi(String gamertag) {
        try {
            JsonObject obj = getJson(GEYSER_API_BASE + "/xbox/xuid/" + encodePath(gamertag));
            if (obj == null || !obj.has("xuid")) return null;
            return obj.get("xuid").getAsString();
        } catch (Exception e) {
            DisguisesMod.LOGGER.debug("Disguises: Geyser XUID lookup failed for '{}': {}",
                    gamertag, e.getMessage());
            return null;
        }
    }

    private static GameProfile fetchBedrockSkin(BedrockIdentity identity) {
        if (identity == null || identity.xuid() == null || identity.xuid().isBlank()) return null;

        BedrockSkin cached = BEDROCK_SKINS.get("xuid:" + identity.xuid());
        if (cached != null && cached.value() != null && !cached.value().isBlank()) {
            return buildBedrockProfile(identity.xuid(), identity.name(),
                    cached.value(), cached.signature());
        }

        try {
            JsonObject obj = getJson(GEYSER_API_BASE + "/skin/" + encodePath(identity.xuid()));
            if (obj == null || !obj.has("value")) return null;

            String value = obj.get("value").getAsString();
            String signature = obj.has("signature") && !obj.get("signature").isJsonNull()
                    ? obj.get("signature").getAsString()
                    : null;
            if (value == null || value.isBlank()) return null;

            cacheBedrockSkin(identity, value, signature);
            return buildBedrockProfile(identity.xuid(), identity.name(), value, signature);
        } catch (Exception e) {
            DisguisesMod.LOGGER.debug("Disguises: Geyser skin lookup failed for XUID {}: {}",
                    identity.xuid(), e.getMessage());
            return null;
        }
    }

    private static void cacheBedrockSkin(BedrockIdentity identity, String value, String signature) {
        if (identity == null || value == null || value.isBlank()) return;

        BedrockSkin skin = new BedrockSkin(value, signature);
        if (identity.xuid() != null && !identity.xuid().isBlank()) {
            BEDROCK_SKINS.put("xuid:" + identity.xuid(), skin);
        }
        if (identity.name() != null && !identity.name().isBlank()) {
            BEDROCK_SKINS.put("name:" + normalizeName(identity.name()), skin);
        }
    }

    private static GameProfile buildBedrockProfile(String xuid, String name, String value,
                                                   String signature) {
        PropertyMap props = new PropertyMap(com.google.common.collect.LinkedHashMultimap.create());
        Property textures = signature != null && !signature.isBlank()
                ? new Property("textures", value, signature)
                : new Property("textures", value);
        props.put("textures", textures);

        UUID uuid = UUID.nameUUIDFromBytes(("DisguisesBedrock:" + xuid).getBytes(StandardCharsets.UTF_8));
        return new GameProfile(uuid, name, props);
    }

    private static JsonObject getJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .header("User-Agent", "DisguisesMod/" + DisguisesMod.MOD_ID)
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()) {
            return null;
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static Set<String> bedrockNameCandidates(String name) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addBedrockNameCandidate(candidates, name);

        String prefix = getFloodgatePrefix();
        if (prefix != null && !prefix.isEmpty() && name.startsWith(prefix)) {
            addBedrockNameCandidate(candidates, name.substring(prefix.length()));
        }
        prefix = getGeyserPrefix();
        if (prefix != null && !prefix.isEmpty() && name.startsWith(prefix)) {
            addBedrockNameCandidate(candidates, name.substring(prefix.length()));
        }
        if (name.startsWith(".")) addBedrockNameCandidate(candidates, name.substring(1));
        if (name.startsWith("*")) addBedrockNameCandidate(candidates, name.substring(1));
        if (name.contains("_")) addBedrockNameCandidate(candidates, name.replace('_', ' '));

        return candidates;
    }

    private static void addBedrockNameCandidate(Set<String> candidates, String value) {
        if (value != null && !value.isBlank()) candidates.add(value.trim());
    }

    private static String getFloodgatePrefix() {
        Object api = getFloodgateApi();
        if (api == null) return null;
        try {
            return stringValue(invoke(api, "getPlayerPrefix"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getGeyserPrefix() {
        Object api = getGeyserApi();
        if (api == null) return null;
        try {
            return stringValue(invoke(api, "usernamePrefix"));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.replace('_', ' ').trim().toLowerCase(Locale.ROOT);
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static Object invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object invoke(Object target, String name, Class<?> paramType, Object arg)
            throws Exception {
        Method method = target.getClass().getMethod(name, paramType);
        method.setAccessible(true);
        return method.invoke(target, arg);
    }

    private static Object invokeQuietly(Object target, String name) {
        try {
            return invoke(target, name);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.map(SkinFetcher::stringValue).orElse(null);
        }
        return value != null ? value.toString() : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    /**
     * Fetches a full GameProfile (with skin texture properties) for a UUID
     * via Mojang's session server using authlib's Apache HTTP stack.
     */
    private static GameProfile fillProfile(UUID uuid) {
        ProfileResult result = SESSION_SERVICE.fetchProfile(uuid, true);
        if (result == null) return null;
        return result.profile();
    }
}
