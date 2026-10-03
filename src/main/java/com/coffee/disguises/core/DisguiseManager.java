package com.coffee.disguises.core;

import com.coffee.disguises.DisguisesMod;
import com.coffee.disguises.disguise.Disguise;
import com.coffee.disguises.event.DisguiseEvents;
import com.coffee.disguises.packet.PacketInterceptor;
import com.google.gson.*;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry: entity UUID → active Disguise.
 *
 * All public methods are expected to be called on the server thread.
 * The internal map is ConcurrentHashMap for safety, but packet operations
 * must occur on the server thread regardless.
 *
 * Access the singleton via DisguiseManager.INSTANCE.
 *
 * ── Self-view ─────────────────────────────────────────────────────────────────
 * When a Disguise has isSelfDisguise() == true, applyDisguise() calls
 * PacketInterceptor.applySelfView() immediately after storing the disguise so
 * the player sees the disguise packets for their own entity.
 * removeDisguise() calls PacketInterceptor.removeSelfView() if the removed
 * disguise had selfDisguise set, restoring the vanilla entity registry entry.
 */
public class DisguiseManager {

    public static final DisguiseManager INSTANCE = new DisguiseManager();

    /** Active disguises: entity UUID → Disguise. */
    private final ConcurrentHashMap<UUID, Disguise> activeDisguises = new ConcurrentHashMap<>();

    /**
     * Observer-specific disguise overrides.
     * entityUUID → (observerUUID → Disguise)
     *
     * When an entity is disguised, each observer first checks this map for a
     * per-observer override before falling back to the default disguise.
     * Setting an override for an observer who is in range triggers an immediate
     * re-spawn of the entity for that observer only.
     */
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Disguise>> observerDisguises =
            new ConcurrentHashMap<>();

    /**
     * Players whose self-view preference is ON.  This persists across disguise changes
     * and undisguise so that re-disguising automatically re-enables the puppet.
     * Updated by: applyDisguise (carry-over), removeDisguise (save on undisguise),
     * and the /disguise viewself command (explicit toggle).
     */
    private final java.util.Set<UUID> selfViewPrefs = ConcurrentHashMap.newKeySet();

    private static final Path PERSIST_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("disguises-persisted.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private DisguiseManager() {}

    // =========================================================================
    // Read
    // =========================================================================

    /** Returns the active disguise for the entity, or null. */
    public Disguise getDisguise(Entity entity) {
        return activeDisguises.get(entity.getUUID());
    }

    /** Returns the active disguise for a UUID, or null. */
    public Disguise getDisguise(UUID uuid) {
        return activeDisguises.get(uuid);
    }

    public boolean isDisguised(Entity entity) {
        return activeDisguises.containsKey(entity.getUUID());
    }

    public boolean isDisguised(UUID uuid) {
        return activeDisguises.containsKey(uuid);
    }

    public Collection<UUID> getAllDisguisedUUIDs() {
        return Collections.unmodifiableSet(activeDisguises.keySet());
    }

    // =========================================================================
    // Observer-specific disguise
    // =========================================================================

    /**
     * Returns the observer-specific disguise for this entity+observer pair, or null
     * if no override is set (fall back to the default disguise).
     */
    public Disguise getDisguiseForObserver(Entity entity, UUID observerUUID) {
        ConcurrentHashMap<UUID, Disguise> map = observerDisguises.get(entity.getUUID());
        return map != null ? map.get(observerUUID) : null;
    }

    /**
     * Sets an observer-specific disguise override for one observer.
     * Call {@link PacketInterceptor#refreshForObserver} afterward to push the change.
     */
    public void setObserverDisguise(Entity entity, UUID observerUUID, Disguise disguise) {
        observerDisguises.computeIfAbsent(entity.getUUID(), k -> new ConcurrentHashMap<>())
                .put(observerUUID, disguise);
    }

    /**
     * Removes the observer-specific override for one observer.
     * Call {@link PacketInterceptor#refreshForObserver} afterward to restore the default view.
     */
    public void removeObserverDisguise(Entity entity, UUID observerUUID) {
        ConcurrentHashMap<UUID, Disguise> map = observerDisguises.get(entity.getUUID());
        if (map == null) return;
        map.remove(observerUUID);
        if (map.isEmpty()) observerDisguises.remove(entity.getUUID());
    }

    /** Clears ALL observer-specific overrides for an entity. */
    public void clearObserverDisguises(Entity entity) {
        clearObserverDisguisesAndGetObservers(entity);
    }

    /**
     * Clears all overrides for an entity and returns the affected observer UUIDs
     * so callers can immediately refresh their client-side view.
     */
    public Set<UUID> clearObserverDisguisesAndGetObservers(Entity entity) {
        ConcurrentHashMap<UUID, Disguise> removed = observerDisguises.remove(entity.getUUID());
        return removed == null ? Set.of() : Set.copyOf(removed.keySet());
    }

    /** Removes every override owned by an observer that is leaving the server. */
    public void removeObserverDisguises(UUID observerUUID) {
        observerDisguises.forEach((entityUUID, overrides) -> {
            overrides.remove(observerUUID);
            if (overrides.isEmpty()) observerDisguises.remove(entityUUID, overrides);
        });
    }

    // =========================================================================
    // Apply
    // =========================================================================

    /**
     * Apply a disguise to an entity.
     * Fires BEFORE_DISGUISE (cancellable).
     * If allowed, stores the disguise and refreshes all nearby players.
     * If the disguise has isSelfDisguise() == true, also applies the self-view
     * immediately so the player sees their own entity as the disguise type.
     *
     * @return true if the disguise was applied, false if cancelled.
     */
    /**
     * Gets or sets the per-player self-view preference (survives disguise change / undisguise).
     * Called by the /disguise viewself command when the player explicitly toggles self-view.
     */
    public void setSelfViewPref(UUID uuid, boolean enabled) {
        if (enabled) selfViewPrefs.add(uuid);
        else selfViewPrefs.remove(uuid);
    }

    public boolean applyDisguise(Entity entity, Disguise disguise) {
        // Fire before-event (cancellable)
        if (!DisguiseEvents.BEFORE_DISGUISE.invoker().onBeforeDisguise(entity, disguise)) {
            return false;
        }

        // ── Carry over self-view preference ──────────────────────────────────
        // If the player previously had self-view on (either in their last disguise or stored
        // as a preference), automatically enable it on the new disguise so the puppet stays
        // visible when changing disguise or re-disguising after /undisguise.
        if (entity instanceof ServerPlayer player) {
            UUID uuid = player.getUUID();
            Disguise prev = activeDisguises.get(uuid);
            boolean pref = selfViewPrefs.contains(uuid)
                    || (prev != null && prev.isSelfDisguise());
            if (pref && !disguise.isSelfDisguise()) {
                disguise.setSelfDisguise(true);
            }
            // Keep pref in sync with the effective self-view setting of the new disguise
            setSelfViewPref(uuid, disguise.isSelfDisguise());
        }

        activeDisguises.put(entity.getUUID(), disguise);

        // Refresh for all nearby players (includes self-view puppet via refreshForNearbyPlayers)
        PacketInterceptor.refreshForNearbyPlayers(entity, disguise);

        // Show action bar to the disguised player
        if (entity instanceof ServerPlayer player && DisguisesMod.CONFIG.showDisguiseActionBar) {
            sendDisguiseActionBar(player);
        }

        DisguiseEvents.AFTER_DISGUISE.invoker().onAfterDisguise(entity, disguise);
        DisguisesMod.LOGGER.debug("Applied disguise {} to entity {}",
                disguise.getType().getId(), entity.getUUID());
        return true;
    }

    // =========================================================================
    // Remove
    // =========================================================================

    /**
     * Remove a disguise from an entity.
     * Fires BEFORE_UNDISGUISE (cancellable).
     * If the removed disguise had isSelfDisguise() == true, also removes the
     * self-view so the player's own entity registry entry reverts to vanilla.
     *
     * @param sendVanillaRespawn if true, sends vanilla entity packets to nearby players.
     * @return true if the disguise was removed, false if cancelled or no disguise was active.
     */
    public boolean removeDisguise(Entity entity, boolean sendVanillaRespawn) {
        return removeDisguiseInternal(entity, sendVanillaRespawn, true, true);
    }

    /**
     * Permanently tears down state for a destroyed entity. Lifecycle cleanup must
     * not be vetoed by BEFORE_UNDISGUISE listeners.
     */
    public boolean removeDisguiseForLifecycle(Entity entity) {
        return removeDisguiseInternal(entity, false, false, true);
    }

    /**
     * Detaches the in-memory disguise on disconnect after it has been persisted.
     * The persisted entry is intentionally retained for the next login.
     */
    public boolean detachDisguiseOnDisconnect(ServerPlayer player) {
        return removeDisguiseInternal(player, false, false, false);
    }

    private boolean removeDisguiseInternal(Entity entity, boolean sendVanillaRespawn,
                                            boolean fireBeforeEvent, boolean removePersisted) {
        Disguise existing = activeDisguises.get(entity.getUUID());
        if (existing == null) {
            if (!fireBeforeEvent) {
                observerDisguises.remove(entity.getUUID());
                PacketInterceptor.cleanupForRemovedEntity(serverFor(entity), entity.getUUID());
                if (removePersisted) {
                    removePersistedDisguise(entity.getUUID());
                }
            }
            return false;
        }

        // Fire before-event (cancellable)
        if (fireBeforeEvent
                && !DisguiseEvents.BEFORE_UNDISGUISE.invoker().onBeforeUndisguise(entity, existing)) {
            return false;
        }

        boolean wasSelfDisguise = existing.isSelfDisguise();

        // Remove FIRST so the ServerEntityMixin sees no disguise on re-track
        activeDisguises.remove(entity.getUUID());
        observerDisguises.remove(entity.getUUID());

        // Destroy + respawn vanilla for all nearby OTHER players
        if (sendVanillaRespawn) {
            PacketInterceptor.refreshForNearbyPlayers(entity, null);
        }

        // Always clear injected fake tab profiles and per-entity sync state across
        // ALL observers, including out-of-range / cross-dim observers that
        // refreshForNearbyPlayers skips.  Required so leftover entries (especially
        // for showDisguiseInTab=true, where no deferred remove was queued) don't
        // persist after disconnect/dim-change/undisguise.
        net.minecraft.server.MinecraftServer mcServer = serverFor(entity);
        PacketInterceptor.cleanupForRemovedEntity(mcServer, entity.getUUID());

        // Restore vanilla self-view if it was active; save the preference so it is
        // automatically re-applied the next time the player disguises.
        if (entity instanceof ServerPlayer player) {
            setSelfViewPref(player.getUUID(), wasSelfDisguise);
            if (wasSelfDisguise) {
                PacketInterceptor.removeSelfView(player);
            }
        }

        DisguiseEvents.AFTER_UNDISGUISE.invoker().onAfterUndisguise(entity);
        DisguisesMod.LOGGER.debug("Removed disguise from entity {}", entity.getUUID());
        // Not gated on persistDisguises: an entry left behind while persistence is
        // off would resurrect this disguise the moment persistence is switched on.
        // Costs nothing when there is no entry.
        if (removePersisted) {
            removePersistedDisguise(entity.getUUID());
        }
        return true;
    }

    private static MinecraftServer serverFor(Entity entity) {
        if (entity.level() instanceof net.minecraft.server.level.ServerLevel level) {
            return level.getServer();
        }
        return null;
    }

    // =========================================================================
    // Persistence
    // =========================================================================
    //
    // disguises-persisted.json is mirrored in memory and written at most once every
    // PERSIST_FLUSH_INTERVAL_TICKS.  Each mutation used to re-read and re-write the
    // entire file inline on the server thread, so `/undisguise radius 256` cost one
    // full parse + serialise per matched entity — a stall that scaled with how many
    // entities happened to be disguised.  The same command now dirties the in-memory
    // map N times and hits the disk once.

    /** Minimum ticks between writes of the persisted-disguise file. */
    private static final int PERSIST_FLUSH_INTERVAL_TICKS = 20;

    /** In-memory mirror of {@link #PERSIST_PATH}; loaded on first use. */
    private JsonObject persistedRoot;
    private boolean persistDirty;
    private long lastPersistFlushTick;

    /** Persist a player's current disguise. Called on disconnect before active state is removed. */
    public void saveDisguise(ServerPlayer player) {
        Disguise disguise = getDisguise(player);
        if (disguise == null) {
            removePersistedDisguise(player.getUUID());
            return;
        }

        persistedRoot().add(player.getUUID().toString(), serializeDisguise(disguise));
        persistDirty = true;
        DisguisesMod.LOGGER.debug("Persisted disguise for {}", player.getUUID());
    }

    /** Persist all active disguises to disk. Called on server stop. */
    public void persistAll(MinecraftServer server) {
        JsonObject root = persistedRoot();
        for (Map.Entry<UUID, Disguise> entry : activeDisguises.entrySet()) {
            root.add(entry.getKey().toString(), serializeDisguise(entry.getValue()));
        }
        // An online player with no active disguise must not keep an old entry:
        // it would bring back a disguise they no longer have on the next start.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!activeDisguises.containsKey(player.getUUID())) {
                root.remove(player.getUUID().toString());
            }
        }
        persistDirty = true;
        flushPersisted();
        DisguisesMod.LOGGER.info("Persisted {} active disguises to disk.", activeDisguises.size());
    }

    /**
     * Writes pending persistence changes if enough ticks have passed.
     * Called every tick from {@code DisguisesMod}; cheap and returns immediately
     * when nothing has changed.
     */
    public void flushPersistedIfDue(MinecraftServer server) {
        if (!persistDirty) return;
        long tick = server.getTickCount();
        if (tick - lastPersistFlushTick < PERSIST_FLUSH_INTERVAL_TICKS) return;
        lastPersistFlushTick = tick;
        flushPersisted();
    }

    private void flushPersisted() {
        if (!persistDirty || persistedRoot == null) return;
        writePersistedRoot(persistedRoot);
        persistDirty = false;
    }

    /** Load persisted disguises from disk. Called on server start. */
    public void loadPersistedDisguises(MinecraftServer server) {
        JsonObject root = readPersistedRoot();
        persistedRoot = root;
        persistDirty = false;

        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                JsonObject obj = entry.getValue().getAsJsonObject();
                deserializeDisguise(obj).ifPresent(disguise -> {
                    activeDisguises.put(uuid, disguise);
                    fetchPersistedSkin(server, uuid, disguise);
                });
            } catch (Exception ex) {
                DisguisesMod.LOGGER.warn("Failed to load persisted disguise entry: {}",
                        entry.getKey());
            }
        }
        DisguisesMod.LOGGER.info("Loaded {} persisted disguises.", activeDisguises.size());
    }

    /**
     * Re-applies a player's persisted disguise when they join.  Called from the
     * JOIN event when persistence is on.
     *
     * Loading only at SERVER_STARTED was not enough: disconnect detaches the active
     * disguise but keeps the persisted entry, so relogging within the same session
     * came back undisguised — and the disguise then reappeared after the next
     * restart.  Going through applyDisguise also brings back the self-view puppet,
     * which pairing alone never did.
     */
    public void restorePersistedDisguise(ServerPlayer player) {
        UUID uuid = player.getUUID();
        // Loaded at SERVER_STARTED if the entry predates this session; take it out
        // of the active map so applyDisguise treats this as a fresh apply.
        Disguise disguise = activeDisguises.remove(uuid);
        if (disguise == null) {
            JsonElement saved = persistedRoot().get(uuid.toString());
            if (saved == null || !saved.isJsonObject()) return;
            disguise = deserializeDisguise(saved.getAsJsonObject()).orElse(null);
            if (disguise == null) {
                removePersistedDisguise(uuid);
                return;
            }
            fetchPersistedSkin(serverFor(player), uuid, disguise);
        }

        if (!applyDisguise(player, disguise)) {
            // A listener vetoed it; don't keep offering it on every login.
            removePersistedDisguise(uuid);
        }
    }

    /**
     * Skins are not persisted, only the name, so a restored player disguise shows
     * the default skin until this fetch completes and respawns it.
     */
    private void fetchPersistedSkin(MinecraftServer server, UUID uuid, Disguise disguise) {
        if (server == null) return;
        if (!(disguise instanceof com.coffee.disguises.disguise.PlayerDisguise pd) || pd.hasSkin()) return;
        com.coffee.disguises.packet.SkinFetcher.fetchByName(pd.getDisguiseName(), server, profile -> {
            if (profile == null || activeDisguises.get(uuid) != pd) return;
            pd.setSkinProfile(profile);
            for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) {
                    PacketInterceptor.refreshForNearbyPlayers(entity, pd);
                    return;
                }
            }
        });
    }

    private void removePersistedDisguise(UUID uuid) {
        if (persistedRoot().remove(uuid.toString()) != null) {
            persistDirty = true;
        }
    }

    /**
     * The in-memory mirror, read from disk on first touch.  Loading lazily rather
     * than only at SERVER_STARTED matters because persistDisguises can be switched
     * on by `/disguises reload` mid-session — without this, the first flush after
     * that would overwrite the file with an empty document.
     */
    private JsonObject persistedRoot() {
        if (persistedRoot == null) persistedRoot = readPersistedRoot();
        return persistedRoot;
    }

    private static JsonObject readPersistedRoot() {
        if (!Files.exists(PERSIST_PATH)) return new JsonObject();
        try (Reader r = Files.newBufferedReader(PERSIST_PATH)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            return root != null ? root : new JsonObject();
        } catch (IOException | JsonParseException e) {
            DisguisesMod.LOGGER.error("Failed to read persisted disguises", e);
            return new JsonObject();
        }
    }

    private static void writePersistedRoot(JsonObject root) {
        try {
            com.coffee.disguises.util.JsonFiles.write(PERSIST_PATH, GSON, root);
        } catch (IOException e) {
            DisguisesMod.LOGGER.error("Failed to persist disguises", e);
        }
    }

    // Package-private (with deserializeDisguise) for DisguisePersistenceTest.
    static JsonObject serializeDisguise(Disguise disguise) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", disguise.getType().getId());
        obj.addProperty("selfDisguise", disguise.isSelfDisguise());
        obj.addProperty("showName", disguise.isShowName());
        if (disguise instanceof com.coffee.disguises.disguise.PlayerDisguise pd) {
            obj.addProperty("playerName", pd.getDisguiseName());
        }
        obj.add("watcher", serializeWatcher(disguise.getWatcher()));
        return obj;
    }

    static Optional<Disguise> deserializeDisguise(JsonObject obj) {
        if (obj == null || !obj.has("type")) return Optional.empty();
        String typeId = obj.get("type").getAsString();
        return com.coffee.disguises.disguise.DisguiseType.fromId(typeId).map(type -> {
            com.coffee.disguises.watcher.FlagWatcher watcher = type.createDefaultWatcher();
            if (obj.has("watcher") && obj.get("watcher").isJsonObject()) {
                applyWatcherFields(watcher, obj.getAsJsonObject("watcher"));
            }

            boolean selfDisguise = obj.has("selfDisguise") && obj.get("selfDisguise").getAsBoolean();
            boolean showName = !obj.has("showName") || obj.get("showName").getAsBoolean();

            if (type == com.coffee.disguises.disguise.DisguiseType.PLAYER) {
                String name = obj.has("playerName") ? obj.get("playerName").getAsString() : "Player";
                return com.coffee.disguises.disguise.PlayerDisguise.builder(name)
                        .watcher(watcher)
                        .selfDisguise(selfDisguise)
                        .showName(showName)
                        .build();
            }

            return Disguise.builder(type)
                    .watcher(watcher)
                    .selfDisguise(selfDisguise)
                    .showName(showName)
                    .build();
        });
    }

    private static JsonObject serializeWatcher(com.coffee.disguises.watcher.FlagWatcher watcher) {
        JsonObject root = new JsonObject();
        for (Field field : watcherFields(watcher.getClass())) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            try {
                field.setAccessible(true);
                Object value = field.get(watcher);
                JsonElement encoded = encodeWatcherValue(value);
                if (encoded != null) root.add(watcherFieldKey(field), encoded);
            } catch (Exception e) {
                DisguisesMod.LOGGER.debug("Skipping watcher field {} during persist: {}",
                        field.getName(), e.getMessage());
            }
        }
        return root;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyWatcherFields(com.coffee.disguises.watcher.FlagWatcher watcher, JsonObject values) {
        for (Field field : watcherFields(watcher.getClass())) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            JsonElement value = values.get(watcherFieldKey(field));
            if (value == null) value = values.get(field.getName());
            if (value == null) continue;
            try {
                field.setAccessible(true);
                Object decoded = decodeWatcherValue(field.getType(), value);
                // null from a non-null JSON value means "could not decode" (unknown
                // item/block, unsupported type): keep the watcher's default rather
                // than writing a null the watcher never expects.
                if (decoded != null || (value.isJsonNull() && !field.getType().isPrimitive())) {
                    field.set(watcher, decoded);
                }
            } catch (Exception e) {
                DisguisesMod.LOGGER.debug("Skipping persisted watcher field {}: {}",
                        field.getName(), e.getMessage());
            }
        }
    }

    private static List<Field> watcherFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> c = type;
        while (c != null && c != Object.class) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
            c = c.getSuperclass();
        }
        return fields;
    }

    private static String watcherFieldKey(Field field) {
        return field.getDeclaringClass().getSimpleName() + "." + field.getName();
    }

    private static JsonElement encodeWatcherValue(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Number n) return new JsonPrimitive(n);
        if (value instanceof String s) return new JsonPrimitive(s);
        if (value instanceof Enum<?> e) return new JsonPrimitive(e.name());
        if (value instanceof net.minecraft.core.Rotations r) {
            JsonArray arr = new JsonArray();
            arr.add(r.x());
            arr.add(r.y());
            arr.add(r.z());
            return arr;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject obj = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof EquipmentSlot slot && entry.getValue() instanceof ItemStack stack
                        && !stack.isEmpty()) {
                    obj.addProperty(slot.name(), itemId(stack.getItem()));
                }
            }
            return obj;
        }
        if (value instanceof BlockState state) {
            // The full state string ("minecraft:oak_stairs[facing=north,...]"), not
            // Block.getId: numeric state ids follow registry order, so they point at
            // a different block as soon as the game version or mod list changes.
            return new JsonPrimitive(BlockStateParser.serialize(state));
        }
        if (value instanceof ItemStack stack) {
            JsonObject obj = new JsonObject();
            if (!stack.isEmpty()) {
                obj.addProperty("item", itemId(stack.getItem()));
                obj.addProperty("count", stack.getCount());
            }
            return obj;
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object decodeWatcherValue(Class<?> type, JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (type == boolean.class || type == Boolean.class) return element.getAsBoolean();
        if (type == byte.class || type == Byte.class) return element.getAsByte();
        if (type == int.class || type == Integer.class) return element.getAsInt();
        if (type == long.class || type == Long.class) return element.getAsLong();
        if (type == float.class || type == Float.class) return element.getAsFloat();
        if (type == double.class || type == Double.class) return element.getAsDouble();
        if (type == String.class) return element.getAsString();
        if (type.isEnum()) return Enum.valueOf((Class<? extends Enum>) type, element.getAsString());
        if (type == net.minecraft.core.Rotations.class && element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            if (arr.size() >= 3) {
                return new net.minecraft.core.Rotations(
                        arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat());
            }
        }
        if (type == BlockState.class) {
            return parseBlockState(element);
        }
        if (Map.class.isAssignableFrom(type) && element.isJsonObject()) {
            EnumMap<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
            JsonObject obj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                try {
                    EquipmentSlot slot = EquipmentSlot.valueOf(entry.getKey());
                    Item item = parseItem(entry.getValue().getAsString());
                    if (item != null) equipment.put(slot, new ItemStack(item));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return equipment.isEmpty() ? null : equipment;
        }
        if (type == ItemStack.class && element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (!obj.has("item")) return ItemStack.EMPTY;
            Item item = parseItem(obj.get("item").getAsString());
            int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
            return item != null ? new ItemStack(item, Math.max(1, count)) : ItemStack.EMPTY;
        }
        return null;
    }

    private static String itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /**
     * Resolves a persisted item id through the registry.  This used to reflect on
     * {@code Items} field names, which only exist under their Mojang names in dev and
     * on unobfuscated targets — on the remapped 1.21.11 jar every lookup failed and
     * saved equipment silently vanished — and it dropped the namespace, so modded
     * items could never resolve.  Bare names from older files get "minecraft:".
     */
    private static Item parseItem(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Identifier id = Identifier.tryParse(raw.trim().toLowerCase(Locale.ROOT));
        if (id == null) return null;
        return BuiltInRegistries.ITEM.getOptional(id)
                .filter(item -> item != Items.AIR)
                .orElse(null);
    }

    /** Accepts the current state-string format and the numeric ids older files used. */
    private static BlockState parseBlockState(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return net.minecraft.world.level.block.Block.stateById(element.getAsInt());
        }
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, element.getAsString(), false)
                    .blockState();
        } catch (CommandSyntaxException e) {
            DisguisesMod.LOGGER.warn("Ignoring unknown persisted block state '{}': {}",
                    element.getAsString(), e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // Action bar
    // =========================================================================

    public void sendDisguiseActionBar(ServerPlayer player) {
        Disguise disguise = getDisguise(player);
        if (disguise == null) return;
        String selfTag = disguise.isSelfDisguise() ? " §8[self-view]" : "";
        net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal(
                "§7Disguised as §e" + capitalize(disguise.getType().getId()) + selfTag);
        player.sendSystemMessage(msg, true);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).replace('_', ' ');
    }
}
