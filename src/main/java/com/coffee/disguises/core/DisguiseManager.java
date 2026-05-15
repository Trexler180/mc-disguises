package com.coffee.disguises.core;

import com.coffee.disguises.DisguisesMod;
import com.coffee.disguises.disguise.Disguise;
import com.coffee.disguises.event.DisguiseEvents;
import com.coffee.disguises.packet.PacketInterceptor;
import com.google.gson.*;
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
        observerDisguises.remove(entity.getUUID());
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
        if (!isDisguised(entity)) return false;

        Disguise existing = activeDisguises.get(entity.getUUID());

        // Fire before-event (cancellable)
        if (!DisguiseEvents.BEFORE_UNDISGUISE.invoker().onBeforeUndisguise(entity, existing)) {
            return false;
        }

        boolean wasSelfDisguise = existing.isSelfDisguise();

        // Remove FIRST so the ServerEntityMixin sees no disguise on re-track
        activeDisguises.remove(entity.getUUID());

        // Destroy + respawn vanilla for all nearby OTHER players
        if (sendVanillaRespawn) {
            PacketInterceptor.refreshForNearbyPlayers(entity, null);
        }

        // Always clear injected fake tab profiles and per-entity sync state across
        // ALL observers, including out-of-range / cross-dim observers that
        // refreshForNearbyPlayers skips.  Required so leftover entries (especially
        // for showDisguiseInTab=true, where no deferred remove was queued) don't
        // persist after disconnect/dim-change/undisguise.
        net.minecraft.server.MinecraftServer mcServer = null;
        if (entity.level() instanceof net.minecraft.server.level.ServerLevel level) {
            mcServer = level.getServer();
        }
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
        if (sendVanillaRespawn && DisguisesMod.CONFIG.persistDisguises) {
            removePersistedDisguise(entity.getUUID());
        }
        return true;
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    /** Persist a player's current disguise immediately. Called on disconnect before active state is removed. */
    public void saveDisguise(ServerPlayer player) {
        Disguise disguise = getDisguise(player);
        if (disguise == null) {
            removePersistedDisguise(player.getUUID());
            return;
        }

        JsonObject root = readPersistedRoot();
        root.add(player.getUUID().toString(), serializeDisguise(disguise));
        writePersistedRoot(root);
        DisguisesMod.LOGGER.debug("Persisted disguise for {}", player.getUUID());
    }

    /** Persist all active disguises to disk. Called on server stop. */
    public void persistAll(MinecraftServer server) {
        JsonObject root = readPersistedRoot();
        for (Map.Entry<UUID, Disguise> entry : activeDisguises.entrySet()) {
            root.add(entry.getKey().toString(), serializeDisguise(entry.getValue()));
        }
        writePersistedRoot(root);
        DisguisesMod.LOGGER.info("Persisted {} active disguises to disk.", activeDisguises.size());
    }

    /** Load persisted disguises from disk. Called on server start. */
    public void loadPersistedDisguises(MinecraftServer server) {
        if (!Files.exists(PERSIST_PATH)) return;
        try (Reader r = Files.newBufferedReader(PERSIST_PATH)) {
            JsonObject root = GSON.fromJson(r, JsonObject.class);
            if (root == null) return;
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    JsonObject obj = entry.getValue().getAsJsonObject();
                    deserializeDisguise(obj).ifPresent(disguise -> activeDisguises.put(uuid, disguise));
                } catch (Exception ex) {
                    DisguisesMod.LOGGER.warn("Failed to load persisted disguise entry: {}",
                            entry.getKey());
                }
            }
            DisguisesMod.LOGGER.info("Loaded {} persisted disguises.", activeDisguises.size());
        } catch (IOException e) {
            DisguisesMod.LOGGER.error("Failed to load persisted disguises", e);
        }
    }

    private void removePersistedDisguise(UUID uuid) {
        JsonObject root = readPersistedRoot();
        if (root.remove(uuid.toString()) != null) {
            writePersistedRoot(root);
        }
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
        try (Writer w = Files.newBufferedWriter(PERSIST_PATH)) {
            GSON.toJson(root, w);
        } catch (IOException e) {
            DisguisesMod.LOGGER.error("Failed to persist disguises", e);
        }
    }

    private static JsonObject serializeDisguise(Disguise disguise) {
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

    private static Optional<Disguise> deserializeDisguise(JsonObject obj) {
        if (obj == null || !obj.has("type")) return Optional.empty();
        String typeId = obj.get("type").getAsString();
        return com.coffee.disguises.disguise.DisguiseType.fromId(typeId).map(type -> {
            com.coffee.disguises.watcher.FlagWatcher watcher = type.createDefaultWatcher();
            if (obj.has("watcher") && obj.get("watcher").isJsonObject()) {
                applyWatcherFields(watcher, obj.getAsJsonObject("watcher"));
            }

            boolean selfDisguise = obj.has("selfDisguise") && obj.get("selfDisguise").getAsBoolean();
            boolean showName = obj.has("showName") && obj.get("showName").getAsBoolean();

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
                if (decoded != null || !field.getType().isPrimitive()) {
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
                    obj.addProperty(slot.name(), stack.getItem().toString());
                }
            }
            return obj;
        }
        if (value instanceof BlockState state) {
            int id = net.minecraft.world.level.block.Block.getId(state);
            return new JsonPrimitive(id);
        }
        if (value instanceof ItemStack stack) {
            JsonObject obj = new JsonObject();
            if (!stack.isEmpty()) {
                obj.addProperty("item", stack.getItem().toString());
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
            return net.minecraft.world.level.block.Block.stateById(element.getAsInt());
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

    private static Item parseItem(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String name = raw.toLowerCase(Locale.ROOT);
        int colon = name.indexOf(':');
        if (colon >= 0) name = name.substring(colon + 1);
        String fieldName = name.toUpperCase(Locale.ROOT);
        try {
            Field field = Items.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(null);
            return value instanceof Item item ? item : null;
        } catch (ReflectiveOperationException e) {
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
