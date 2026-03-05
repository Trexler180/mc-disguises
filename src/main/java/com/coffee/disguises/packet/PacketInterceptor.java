package com.coffee.disguises.packet;

import com.coffee.disguises.DisguisesMod;
import com.coffee.disguises.compat.VanishCompat;
import com.coffee.disguises.disguise.Disguise;
import com.coffee.disguises.disguise.DisguiseType;
import com.coffee.disguises.disguise.PlayerDisguise;
import com.coffee.disguises.event.DisguiseEvents;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Builds and sends the disguised spawn packet sequence to individual observers.
 *
 * ─── MOB DISGUISES ────────────────────────────────────────────────────────────
 * Sequence: AddEntity → SetEntityData → SetEquipment → [delayed SetEquipment]
 *
 * ─── INANIMATE DISGUISES ──────────────────────────────────────────────────────
 * Boats, minecarts, falling blocks, etc. are not LivingEntities.
 * Only AddEntity is sent.  ARMOR_STAND is the exception (it IS a LivingEntity).
 *
 * ─── PLAYER DISGUISES ─────────────────────────────────────────────────────────
 * 1. ClientboundPlayerInfoUpdatePacket  (ADD_PLAYER + UPDATE_LISTED)
 * 2. ClientboundAddEntityPacket          (EntityType.PLAYER)
 * 3. ClientboundSetEntityDataPacket      (metadata / skin flags)
 * 4. ClientboundSetEquipmentPacket
 * 5. [delayed] ClientboundPlayerInfoRemovePacket
 *
 * ─── SELF-VIEW ────────────────────────────────────────────────────────────────
 * When Disguise.isSelfDisguise() is true the disguised player also receives the
 * disguise packets for their own entity ID.  This updates the client-side entity
 * registry so that interaction / nametag / particle lookups reflect the disguise
 * type.  Full third-person visual self-rendering requires a companion client mod
 * (the LocalPlayer is rendered directly, not via the entity registry), but the
 * self-view packets still provide partial feedback and future-proof the system.
 *
 * applySelfView()  — call after storing the disguise in DisguiseManager
 * removeSelfView() — call before removing from DisguiseManager (or pass null disguise
 *                    to refreshForNearbyPlayers which now includes the player themselves)
 */
public class PacketInterceptor {

    // ── Deferred queues ───────────────────────────────────────────────────────

    /**
     * Set to true while PacketInterceptor is sending a disguise metadata packet.
     * EntityDataUpdateMixin checks this flag: when true, the packet originated from
     * MetadataBuilder (correct types for the disguise) and must NOT be filtered.
     *
     * Without this, EntityDataUpdateMixin strips type-specific indices (≥15) from
     * our own packets — sheep color (index 17), baby (index 16), etc. would never
     * reach the client even though they are correct for the disguise entity type.
     *
     * ThreadLocal because the server thread sets/clears it synchronously around
     * send(), and the mixin's @Inject(HEAD) runs synchronously inside that same call.
     */
    public static final ThreadLocal<Boolean> SENDING_DISGUISE_METADATA =
            ThreadLocal.withInitial(() -> false);

    private record PendingEquipment(long tick, ServerPlayer observer, Entity entity) {}
    private record PendingTabRemove(long tick, ServerPlayer observer, UUID fakeUUID) {}
    private record PendingEntityDataResend(long tick, ServerPlayer observer, Entity entity) {}

    private static final ConcurrentLinkedQueue<PendingEquipment>       pendingEquipment  = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<PendingTabRemove>       pendingTabRemove  = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<PendingEntityDataResend> pendingDataResend = new ConcurrentLinkedQueue<>();

    /** Called every tick from DisguisesMod.END_SERVER_TICK. */
    public static void flushPendingEquipment(MinecraftServer server) {
        long tick = server.getTickCount();

        pendingEquipment.removeIf(pe -> {
            if (pe.tick() > tick) return false;
            if (!pe.observer().isAlive() || pe.observer().isRemoved()) return true;
            if (!pe.entity().isAlive()) return true;
            sendEquipmentPacket(pe.observer(), pe.entity());
            return true;
        });

        pendingTabRemove.removeIf(tr -> {
            if (tr.tick() > tick) return false;
            if (!tr.observer().isAlive() || tr.observer().isRemoved()) return true;
            tr.observer().connection.send(new ClientboundPlayerInfoRemovePacket(List.of(tr.fakeUUID())));
            return true;
        });

        pendingDataResend.removeIf(dr -> {
            if (dr.tick() > tick) return false;
            if (!dr.observer().isAlive() || dr.observer().isRemoved()) return true;
            if (!dr.entity().isAlive()) return true;

            com.coffee.disguises.disguise.Disguise disguise =
                    com.coffee.disguises.core.DisguiseManager.INSTANCE.getDisguise(dr.entity());

            List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values;
            if (disguise != null
                    && disguise.getType() == com.coffee.disguises.disguise.DisguiseType.PLAYER
                    && !(dr.entity() instanceof ServerPlayer)) {
                // For mob-as-player, copy Player-specific data values (skin parts, etc.)
                // from any real online player so the outer layers render correctly.
                values = null;
                for (ServerPlayer rp : server.getPlayerList().getPlayers()) {
                    if (rp == dr.observer()) continue;
                    List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> rpValues =
                            rp.getEntityData().getNonDefaultValues();
                    if (rpValues != null && !rpValues.isEmpty()) {
                        values = rpValues.stream()
                                .filter(v -> v.id() > 14)
                                .collect(java.util.stream.Collectors.toList());
                        break;
                    }
                }
            } else {
                values = dr.entity().getEntityData().getNonDefaultValues();
            }

            if (values != null && !values.isEmpty()) {
                dr.observer().connection.send(
                        new ClientboundSetEntityDataPacket(dr.entity().getId(), values));
            }
            return true;
        });
    }

    // =========================================================================
    // Self-view — public API
    // =========================================================================

    /**
     * Sends the disguised entity packets to the player for their OWN entity ID.
     *
     * This updates the client-side entity registry entry for the player so that
     * effects relying on entity-type lookups (name tags, particles, loot beams)
     * use the disguise type.  The LocalPlayer object itself is unaffected, so
     * first-person hands and HUD remain correct.
     *
     * Must be called AFTER DisguiseManager has stored the disguise.
     * Called automatically by DisguiseManager.applyDisguise when isSelfDisguise().
     */
    public static void applySelfView(ServerPlayer player, Disguise disguise) {
        // Step 1: destroy existing registry entry for own entity ID
        player.connection.send(new ClientboundRemoveEntitiesPacket(player.getId()));

        Vec3 vel = player.getDeltaMovement();

        if (disguise instanceof PlayerDisguise pd) {
            // Player-as-player self-view: spawn with own UUID to avoid tab-list confusion
            player.connection.send(new ClientboundAddEntityPacket(
                    player.getId(), player.getUUID(),
                    player.getX(), player.getY(), player.getZ(),
                    player.getXRot(), player.getYRot(),
                    net.minecraft.world.entity.EntityType.PLAYER,
                    0, vel, player.getYHeadRot()
            ));
        } else {
            // Mob / inanimate self-view
            int data = getAddEntityData(disguise, player);
            player.connection.send(new ClientboundAddEntityPacket(
                    player.getId(), player.getUUID(),
                    player.getX(), player.getY(), player.getZ(),
                    player.getXRot(), player.getYRot(),
                    disguise.getType().getEntityType(),
                    data, vel, player.getYHeadRot()
            ));
        }

        // Send metadata so disguise-specific state (size, color, etc.) is visible
        sendMetadataPacket(player, player, disguise);

        // Only send equipment for living-type disguises
        if (!disguise.getType().isInanimate()) {
            sendEquipmentPacket(player, player);
        }
    }

    /**
     * Removes the self-view disguise, restoring the vanilla entity registry entry.
     * Called automatically by DisguiseManager.removeDisguise when isSelfDisguise() was true.
     */
    public static void removeSelfView(ServerPlayer player) {
        player.connection.send(new ClientboundRemoveEntitiesPacket(player.getId()));
        sendVanillaRespawn(player, player);
    }

    // =========================================================================
    // Main entry point
    // =========================================================================

    public static void sendDisguisedSpawn(ServerPlayer observer, Entity entity, Disguise disguise) {
        if (VanishCompat.isVanishedFrom(entity, observer)) return;
        if (!DisguiseEvents.BEFORE_SEND_SPAWN.invoker().onBeforeSendSpawn(observer, entity, disguise)) return;

        if (disguise instanceof PlayerDisguise pd) {
            sendPlayerDisguiseSpawn(observer, entity, pd);
        } else if (disguise.getType().isInanimate()) {
            sendInanimateDisguiseSpawn(observer, entity, disguise);
        } else {
            sendMobDisguiseSpawn(observer, entity, disguise);
        }
    }

    // =========================================================================
    // Spawn helpers
    // =========================================================================

    private static void sendMobDisguiseSpawn(ServerPlayer observer, Entity entity,
                                             Disguise disguise) {
        Vec3 vel = entity.getDeltaMovement();
        observer.connection.send(new ClientboundAddEntityPacket(
                entity.getId(), entity.getUUID(),
                entity.getX(), entity.getY(), entity.getZ(),
                entity.getXRot(), entity.getYRot(),
                disguise.getType().getEntityType(),
                0, vel, entity.getYHeadRot()
        ));
        sendMetadataPacket(observer, entity, disguise);
        sendEquipmentPacket(observer, entity);
        enqueueEquipmentResend(observer, entity);
    }

    private static void sendInanimateDisguiseSpawn(ServerPlayer observer, Entity entity,
                                                   Disguise disguise) {
        Vec3 vel = entity.getDeltaMovement();
        int data = getAddEntityData(disguise, entity);

        observer.connection.send(new ClientboundAddEntityPacket(
                entity.getId(), entity.getUUID(),
                entity.getX(), entity.getY(), entity.getZ(),
                entity.getXRot(), entity.getYRot(),
                disguise.getType().getEntityType(),
                data, vel, entity.getYHeadRot()
        ));

        // Armor stand is inanimate by classification but IS a LivingEntity —
        // send metadata and equipment so held/worn items appear correctly.
        if (disguise.getType() == DisguiseType.ARMOR_STAND) {
            sendMetadataPacket(observer, entity, disguise);
            sendEquipmentPacket(observer, entity);
            enqueueEquipmentResend(observer, entity);
            return;
        }

        // Minecarts: send metadata so the custom display block DataTracker fields
        // (DISPLAY_BLOCK_STATE_ID, DISPLAY_OFFSET_ID, CUSTOM_DISPLAY_ID) reach the client.
        if (disguise.getWatcher() instanceof com.coffee.disguises.watcher.MinecartWatcher) {
            sendMetadataPacket(observer, entity, disguise);
            return;
        }

        // BlockDisplay: send metadata so the block state and scale fields reach the client.
        // Without metadata the display entity has a zero scale and is invisible.
        if (disguise.getType() == DisguiseType.BLOCK_DISPLAY) {
            sendMetadataPacket(observer, entity, disguise);
        }
    }

    private static void sendPlayerDisguiseSpawn(ServerPlayer observer, Entity entity,
                                                PlayerDisguise pd) {
        GameProfile skinSource = pd.hasSkin()
                ? pd.getSkinProfile()
                : buildDefaultProfile(pd.getDisguiseName());

        // Always use a randomly-generated UUID for the fake tab entry to avoid
        // collisions when the observer IS the impersonated player.
        UUID fakeUUID = UUID.randomUUID();

        com.mojang.authlib.properties.PropertyMap props =
                new com.mojang.authlib.properties.PropertyMap(
                        com.google.common.collect.HashMultimap.create(skinSource.properties()));
        GameProfile fakeProfile;
        try {
            java.lang.reflect.Constructor<GameProfile> ctor = GameProfile.class.getDeclaredConstructor(
                    UUID.class, String.class, com.mojang.authlib.properties.PropertyMap.class);
            ctor.setAccessible(true);
            fakeProfile = ctor.newInstance(fakeUUID, skinSource.name(), props);
        } catch (Exception e) {
            DisguisesMod.LOGGER.warn("[Disguises] 3-arg GameProfile constructor failed: {} — skin may not load.",
                    e.getMessage());
            fakeProfile = new GameProfile(fakeUUID, skinSource.name());
        }

        Vec3 vel = entity.getDeltaMovement();

        // Inject fake profile into client tab list so the skin texture is known
        observer.connection.send(buildRawPlayerInfoPacket(fakeProfile));

        observer.connection.send(new ClientboundAddEntityPacket(
                entity.getId(), fakeUUID,
                entity.getX(), entity.getY(), entity.getZ(),
                entity.getXRot(), entity.getYRot(),
                net.minecraft.world.entity.EntityType.PLAYER,
                0, vel, entity.getYHeadRot()
        ));

        sendMetadataPacket(observer, entity, pd);
        sendEquipmentPacket(observer, entity);
        enqueueEquipmentResend(observer, entity);

        // Resend full entity data after 3 ticks to deliver skin-customisation byte
        if (entity.level() instanceof ServerLevel level && level.getServer() != null) {
            pendingDataResend.add(new PendingEntityDataResend(
                    level.getServer().getTickCount() + 3, observer, entity));
        }

        // Remove fake entry from tab list after configured delay
        if (entity.level() instanceof ServerLevel level && level.getServer() != null) {
            int delay = DisguisesMod.CONFIG.tabRemoveDelayTicks;
            if (!DisguisesMod.CONFIG.showDisguiseInTab && delay >= 0) {
                pendingTabRemove.add(new PendingTabRemove(
                        level.getServer().getTickCount() + delay, observer, fakeUUID));
            }
        }
    }

    // =========================================================================
    // data field for AddEntityPacket
    // =========================================================================

    /**
     * Returns the AddEntityPacket {@code data} integer for entity types that
     * require a non-zero value in that field.
     *
     * ── FallingBlock ──────────────────────────────────────────────────────────
     * Encodes a block-state ID.  We default to sand.  If the state ID changes
     * in a future MC version the visual falls back to air (invisible) — harmless.
     *
     * ── Painting ──────────────────────────────────────────────────────────────
     * The client's AddEntity handler for paintings does:
     *   Validate.isTrue(variantId != 0)
     * where variantId is the {@code data} field of the packet.  Vanilla encodes
     * the painting variant as {@code rawRegistryId + 1} so that 0 can represent
     * "null / missing", which would crash the client.  We look up the first
     * available variant from the server's dynamic registry and encode it the
     * same way.  The painting will render with that variant's artwork.
     *
     * ── All others ────────────────────────────────────────────────────────────
     * 0 is the vanilla default / unused.
     *
     * @param disguise the active disguise
     * @param entity   the disguised entity (used for registry/level access)
     */
    private static int getAddEntityData(Disguise disguise, Entity entity) {
        if (disguise.getType() == DisguiseType.FALLING_BLOCK) {
            // If the watcher specifies a block, use it; otherwise fall back to SAND.
            if (disguise.getWatcher() instanceof com.coffee.disguises.watcher.FallingBlockWatcher fbw) {
                return fbw.getBlockId();
            }
            try {
                return net.minecraft.world.level.block.Block.getId(
                        net.minecraft.world.level.block.Blocks.SAND.defaultBlockState());
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    // =========================================================================
    // PlayerInfoUpdatePacket construction
    // =========================================================================

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ClientboundPlayerInfoUpdatePacket buildRawPlayerInfoPacket(GameProfile fakeProfile) {
        var actions = java.util.EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED
        );
        ClientboundPlayerInfoUpdatePacket.Entry entry = buildInfoEntry(fakeProfile);

        // Strategy 1: internal (EnumSet, List<Entry>) constructor
        for (java.lang.reflect.Constructor<?> ctor :
                ClientboundPlayerInfoUpdatePacket.class.getDeclaredConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length == 2 && p[0] == java.util.EnumSet.class
                    && (p[1] == List.class || p[1] == java.util.Collection.class)) {
                try {
                    ctor.setAccessible(true);
                    return (ClientboundPlayerInfoUpdatePacket) ctor.newInstance(actions, List.of(entry));
                } catch (Exception e) {
                    DisguisesMod.LOGGER.debug("[Disguises] Packet ctor strategy 1 failed: {}", e.getMessage());
                }
            }
        }

        // Strategy 2: public ctor + field inject
        try {
            var packet = new ClientboundPlayerInfoUpdatePacket(actions,
                    java.util.Collections.<ServerPlayer>emptyList());
            for (java.lang.reflect.Field f : ClientboundPlayerInfoUpdatePacket.class.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    f.set(packet, List.of(entry));
                    return packet;
                }
            }
        } catch (Exception e) {
            DisguisesMod.LOGGER.error("[Disguises] Strategy 2 failed: {}", e.getMessage(), e);
        }

        throw new RuntimeException("[Disguises] Could not build ClientboundPlayerInfoUpdatePacket.");
    }

    private static ClientboundPlayerInfoUpdatePacket.Entry buildInfoEntry(GameProfile fakeProfile) {
        for (java.lang.reflect.Constructor<?> ctor :
                ClientboundPlayerInfoUpdatePacket.Entry.class.getDeclaredConstructors()) {
            ctor.setAccessible(true);
            Class<?>[] params = ctor.getParameterTypes();
            boolean hasUUID = false, hasProfile = false;
            for (Class<?> t : params) {
                if (t == UUID.class) hasUUID = true;
                if (t == GameProfile.class) hasProfile = true;
            }
            if (!hasUUID || !hasProfile) continue;
            try {
                Object[] args = new Object[params.length];
                boolean firstBool = true;
                for (int i = 0; i < params.length; i++) {
                    Class<?> t = params[i];
                    if (t == UUID.class) args[i] = fakeProfile.id();
                    else if (t == GameProfile.class) args[i] = fakeProfile;
                    else if (t == boolean.class) { args[i] = firstBool; firstBool = false; }
                    else if (t == int.class) args[i] = 0;
                    else if (t == net.minecraft.world.level.GameType.class)
                        args[i] = net.minecraft.world.level.GameType.SURVIVAL;
                    else args[i] = null;
                }
                return (ClientboundPlayerInfoUpdatePacket.Entry) ctor.newInstance(args);
            } catch (Exception e) {
                DisguisesMod.LOGGER.warn("[Disguises] Entry ctor ({} params) failed: {}", params.length, e.getMessage());
            }
        }
        throw new RuntimeException("[Disguises] Could not construct ClientboundPlayerInfoUpdatePacket.Entry.");
    }

    private static GameProfile buildDefaultProfile(String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("DisguisesFake:" + name).getBytes());
        return new GameProfile(uuid, name);
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================

    private static void sendMetadataPacket(ServerPlayer observer, Entity entity,
                                           Disguise disguise) {
        try {
            List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values =
                    MetadataBuilder.build(disguise.getWatcher(), disguise.getType());
            if (!values.isEmpty()) {
                // Set bypass flag so EntityDataUpdateMixin knows this packet came from
                // MetadataBuilder (correct types for the disguise) and must not be filtered.
                SENDING_DISGUISE_METADATA.set(true);
                try {
                    observer.connection.send(new ClientboundSetEntityDataPacket(entity.getId(), values));
                } finally {
                    SENDING_DISGUISE_METADATA.set(false);
                }
            }
        } catch (Exception e) {
            DisguisesMod.LOGGER.warn("Failed to send disguise metadata for {}: {}",
                    entity.getUUID(), e.getMessage());
        }
    }

    private static void sendEquipmentPacket(ServerPlayer observer, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return;
        List<com.mojang.datafixers.util.Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            equipment.add(com.mojang.datafixers.util.Pair.of(slot, living.getItemBySlot(slot).copy()));
        }
        observer.connection.send(new ClientboundSetEquipmentPacket(entity.getId(), equipment));
    }

    private static void enqueueEquipmentResend(ServerPlayer observer, Entity entity) {
        if (entity.level() instanceof ServerLevel level && level.getServer() != null) {
            pendingEquipment.add(new PendingEquipment(
                    level.getServer().getTickCount() + 1, observer, entity));
        }
    }

    // =========================================================================
    // Refresh helpers (on apply / change / remove)
    // =========================================================================

    /**
     * Sends Remove + (disguised or vanilla) spawn packets to all players in range.
     * Also refreshes the disguised player themselves if isSelfDisguise is set.
     *
     * @param entity  the entity whose disguise changed
     * @param disguise the new disguise, or null to restore vanilla appearance
     */
    public static void refreshForNearbyPlayers(Entity entity, Disguise disguise) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        if (server == null) return;
        double rangeSq = 128.0 * 128.0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean isSelf = player == entity;

            // For the player themselves:
            //   - if selfDisguise is on, apply self-view separately (applySelfView / removeSelfView)
            //   - standard entity refresh path skips self to avoid corrupting the client's
            //     LocalPlayer object
            if (isSelf) continue;

            if (!player.level().dimension().equals(level.dimension())) continue;
            if (player.distanceToSqr(entity) > rangeSq) continue;

            player.connection.send(new ClientboundRemoveEntitiesPacket(entity.getId()));
            if (disguise != null) {
                sendDisguisedSpawn(player, entity, disguise);
            } else {
                sendVanillaRespawn(player, entity);
            }
        }

        // Refresh self-view separately (isSelf path above was skipped)
        if (entity instanceof ServerPlayer selfPlayer) {
            if (disguise != null && disguise.isSelfDisguise()) {
                applySelfView(selfPlayer, disguise);
            } else if (disguise == null) {
                // Check if the previous disguise had selfDisguise set.
                // DisguiseManager calls removeSelfView directly before refreshForNearbyPlayers(null),
                // so we only need to handle it if somehow reached here without that call.
                // No-op: removeSelfView is called from DisguiseManager.removeDisguise.
            }
        }
    }

    public static void sendVanillaRespawn(ServerPlayer observer, Entity entity) {
        Vec3 vel = entity.getDeltaMovement();
        observer.connection.send(new ClientboundAddEntityPacket(
                entity.getId(), entity.getUUID(),
                entity.getX(), entity.getY(), entity.getZ(),
                entity.getXRot(), entity.getYRot(),
                entity.getType(), 0, vel, entity.getYHeadRot()
        ));
        List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values =
                entity.getEntityData().getNonDefaultValues();
        if (values != null && !values.isEmpty()) {
            observer.connection.send(new ClientboundSetEntityDataPacket(entity.getId(), values));
        }
        if (entity instanceof LivingEntity) {
            sendEquipmentPacket(observer, entity);
        }
    }
}