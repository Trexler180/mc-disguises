package com.coffee.disguises.packet;

import com.coffee.disguises.DisguisesMod;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.Proxy;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Fetches GameProfiles with skin texture data for player disguises.
 *
 * Uses authlib's YggdrasilAuthenticationService (Apache HttpClient internally)
 * to avoid Fabric/Knot's URLStreamHandlerFactory blocking raw Java HTTP calls.
 */
public class SkinFetcher {

    private static final int CACHE_MAX = 200;

    private static final YggdrasilAuthenticationService AUTH_SERVICE =
            new YggdrasilAuthenticationService(Proxy.NO_PROXY);

    private static final GameProfileRepository PROFILE_REPO =
            AUTH_SERVICE.createProfileRepository();

    private static final MinecraftSessionService SESSION_SERVICE =
            AUTH_SERVICE.createMinecraftSessionService();

    private static final Executor EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "disguises-skin-fetcher");
        t.setDaemon(true);
        return t;
    });

    private static final Map<String, GameProfile> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, GameProfile> e) {
                    return size() > CACHE_MAX;
                }
            });

    // -----------------------------------------------------------------------

    public static void fetchByName(String name, MinecraftServer server, Consumer<GameProfile> cb) {
        String key = name.toLowerCase(Locale.ROOT);
        GameProfile hit = CACHE.get(key);
        if (hit != null) { server.execute(() -> cb.accept(hit)); return; }

        CompletableFuture.<GameProfile>supplyAsync(() -> resolveByName(name, server), EXECUTOR)
                .thenAccept(p -> server.execute(() -> {
                    if (p != null) CACHE.put(key, p);
                    cb.accept(p);
                }));
    }

    public static void fetchByUUID(UUID uuid, MinecraftServer server, Consumer<GameProfile> cb) {
        String key = "uuid:" + uuid;
        GameProfile hit = CACHE.get(key);
        if (hit != null) { server.execute(() -> cb.accept(hit)); return; }

        CompletableFuture.<GameProfile>supplyAsync(() -> resolveByUUID(uuid, server), EXECUTOR)
                .thenAccept(p -> server.execute(() -> {
                    if (p != null) CACHE.put(key, p);
                    cb.accept(p);
                }));
    }

    public static void clearCache() { CACHE.clear(); }

    // -----------------------------------------------------------------------

    private static GameProfile resolveByName(String name, MinecraftServer server) {
        // 1. Already online — free and instant
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            DisguisesMod.LOGGER.debug("Disguises: skin for '{}' from online player.", name);
            return online.getGameProfile();
        }

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
                return null;
            }

            // 3. UUID → full profile with skin textures
            GameProfile filled = fillProfile(found[0]);
            if (filled != null) {
                DisguisesMod.LOGGER.info("Disguises: skin for '{}' fetched via authlib (UUID {}).",
                        name, found[0]);
                return filled;
            }

            DisguisesMod.LOGGER.warn("Disguises: could not fill skin properties for '{}', using stub.", name);
            return new GameProfile(found[0], name);

        } catch (Exception e) {
            DisguisesMod.LOGGER.warn("Disguises: skin fetch for '{}' failed: {} - {}",
                    name, e.getClass().getSimpleName(), e.getMessage());
            DisguisesMod.LOGGER.debug("Disguises: stack trace:", e);
            return null;
        }
    }

    private static GameProfile resolveByUUID(UUID uuid, MinecraftServer server) {
        // 1. Already online
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (p.getUUID().equals(uuid)) return p.getGameProfile();

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