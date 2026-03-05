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
    public boolean applyDisguise(Entity entity, Disguise disguise) {
        // Fire before-event (cancellable)
        if (!DisguiseEvents.BEFORE_DISGUISE.invoker().onBeforeDisguise(entity, disguise)) {
            return false;
        }

        activeDisguises.put(entity.getUUID(), disguise);

        // Refresh for all other nearby players
        PacketInterceptor.refreshForNearbyPlayers(entity, disguise);

        // Apply self-view if the disguised entity is a player and self-view is requested.
        // refreshForNearbyPlayers already skips the player themselves (player == entity),
        // so we handle the self case explicitly here.
        if (disguise.isSelfDisguise() && entity instanceof ServerPlayer player) {
            PacketInterceptor.applySelfView(player, disguise);
        }

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

        // Restore vanilla self-view if it was active
        if (wasSelfDisguise && entity instanceof ServerPlayer player) {
            PacketInterceptor.removeSelfView(player);
        }

        DisguiseEvents.AFTER_UNDISGUISE.invoker().onAfterUndisguise(entity);
        DisguisesMod.LOGGER.debug("Removed disguise from entity {}", entity.getUUID());
        return true;
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    /** Queue saving a player's disguise (called on disconnect). Batch is handled at server stop. */
    public void saveDisguise(ServerPlayer player) {
        DisguisesMod.LOGGER.debug("Queuing disguise persist for {}", player.getUUID());
    }

    /** Persist all active disguises to disk. Called on server stop. */
    public void persistAll(MinecraftServer server) {
        JsonObject root = new JsonObject();
        for (Map.Entry<UUID, Disguise> entry : activeDisguises.entrySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", entry.getValue().getType().getId());
            obj.addProperty("selfDisguise", entry.getValue().isSelfDisguise());
            obj.addProperty("showName", entry.getValue().isShowName());
            // TODO: serialize FlagWatcher fields per-type (add toJson() to each watcher)
            root.add(entry.getKey().toString(), obj);
        }
        try (Writer w = Files.newBufferedWriter(PERSIST_PATH)) {
            GSON.toJson(root, w);
            DisguisesMod.LOGGER.info("Persisted {} disguises to disk.", activeDisguises.size());
        } catch (IOException e) {
            DisguisesMod.LOGGER.error("Failed to persist disguises", e);
        }
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
                    String typeId = obj.get("type").getAsString();
                    com.coffee.disguises.disguise.DisguiseType.fromId(typeId).ifPresent(type -> {
                        Disguise disguise = Disguise.builder(type)
                                .selfDisguise(obj.has("selfDisguise") && obj.get("selfDisguise").getAsBoolean())
                                .showName(obj.has("showName") && obj.get("showName").getAsBoolean())
                                .build();
                        activeDisguises.put(uuid, disguise);
                    });
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

    // =========================================================================
    // Action bar
    // =========================================================================

    public void sendDisguiseActionBar(ServerPlayer player) {
        Disguise disguise = getDisguise(player);
        if (disguise == null) return;
        String selfTag = disguise.isSelfDisguise() ? " §8[self-view]" : "";
        net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal(
                "§7Disguised as §e" + capitalize(disguise.getType().getId()) + selfTag);
        player.displayClientMessage(msg, true);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).replace('_', ' ');
    }
}