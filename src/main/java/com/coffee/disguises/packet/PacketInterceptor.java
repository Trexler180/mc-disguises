package com.coffee.disguises.packet;

import com.coffee.disguises.DisguisesMod;
import com.coffee.disguises.compat.VanishCompat;
import com.coffee.disguises.core.DisguiseManager;
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
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

    /**
     * Set to true while PacketInterceptor is intentionally sending a
     * ClientboundRemoveEntitiesPacket for a disguised entity.
     * EntityDataUpdateMixin checks this and allows our own controlled removes through,
     * while suppressing removes that originate from vanish mods.
     */
    public static final ThreadLocal<Boolean> SENDING_DISGUISE_REMOVE =
            ThreadLocal.withInitial(() -> false);

    /**
     * Set to true while the EntityDataUpdateMixin is re-sending a rotation packet with
     * the Ender Dragon 180° yRot offset applied.  Prevents the mixin from intercepting
     * its own re-sent packet and recursing infinitely (→ StackOverflowError).
     */
    public static final ThreadLocal<Boolean> SENDING_DRAGON_ROTATION =
            ThreadLocal.withInitial(() -> false);

    private record PendingEquipment(long tick, ServerPlayer observer, Entity entity) {}
    private record PendingTabRemove(long tick, ServerPlayer observer, UUID fakeUUID) {}
    private record PendingEntityDataResend(long tick, ServerPlayer observer, Entity entity) {}

    /**
     * Tracks an active self-view puppet entity.
     *
     * @param puppetId     fake entity ID sent to the player's own client (1_000_000_000 + player.getId())
     * @param tabUUID      UUID used for the fake tab-list entry (null for non-player disguises)
     * @param selfTeamName scoreboard team name used to hide the puppet's nametag (null if not set)
     */
    private record SelfViewPuppet(int puppetId, UUID tabUUID, String selfTeamName) {}

    private static final ConcurrentLinkedQueue<PendingEquipment>       pendingEquipment  = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<PendingTabRemove>       pendingTabRemove  = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<PendingEntityDataResend> pendingDataResend = new ConcurrentLinkedQueue<>();

    /** Active self-view puppets: player UUID → puppet state. */
    private static final ConcurrentHashMap<UUID, SelfViewPuppet> selfViewPuppets = new ConcurrentHashMap<>();

    /**
     * Last-sent packed state for each puppet: bits 0–7 = entity flags byte, bits 8–15 = Pose ordinal.
     * Used to suppress redundant metadata updates in syncSelfViewPuppets.
     */
    private static final ConcurrentHashMap<UUID, long[]> lastPuppetState = new ConcurrentHashMap<>();

    /** Cached reflection field for ClientboundAnimatePacket.action (int). */
    private static volatile java.lang.reflect.Field ANIMATE_ACTION_FIELD;

    /** Last fixed-point position sent per disguised entity per observer, for movement sync. */
    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, long[]>> lastSyncPos =
            new ConcurrentHashMap<>();

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
                // Use the SENDING_DISGUISE_METADATA bypass flag so EntityDataUpdateMixin
                // does not strip the high-index player-layout fields we are intentionally
                // forwarding here (skin parts, main hand, etc.).  Without it, the mixin
                // would see entity=mob / disguise=PLAYER and cap at MAX_LIVING_ENTITY_ID=14,
                // stripping everything we just built and leaving the disguise skinless.
                SENDING_DISGUISE_METADATA.set(true);
                try {
                    dr.observer().connection.send(
                            new ClientboundSetEntityDataPacket(dr.entity().getId(), values));
                } finally {
                    SENDING_DISGUISE_METADATA.set(false);
                }
            }
            return true;
        });
    }

    // =========================================================================
    // Self-view — public API (puppet entity approach)
    // =========================================================================

    /**
     * Spawns a puppet entity on the player's own client to show their disguise appearance.
     *
     * Instead of replacing the player's own entity ID (which corrupts the LocalPlayer
     * object and causes freezing), we spawn a completely separate fake entity at a
     * collision-free ID ({@code 1_000_000_000 + player.getId()}).  The puppet is kept
     * in sync every tick by {@link #syncSelfViewPuppets}.  The player's own entity and
     * LocalPlayer remain completely untouched.
     *
     * Must be called AFTER DisguiseManager has stored the disguise.
     */
    public static void applySelfView(ServerPlayer player, Disguise disguise) {
        // Remove any stale puppet first (e.g. rapid re-disguise)
        removeSelfViewPuppetIfPresent(player);

        int puppetId = 1_000_000_000 + player.getId();
        Vec3 vel = player.getDeltaMovement();
        UUID tabUUID = null;
        String selfTeamName = null;
        // The string that represents this puppet in the scoreboard/team system.
        // For player puppets this is the fake profile name; for others it is a
        // unique synthetic name that we set as a hidden custom-name on the entity
        // so that the client can resolve team membership via getScoreboardName().
        String puppetMemberName = null;

        if (disguise instanceof PlayerDisguise pd) {
            // ── Player disguise: inject skin into tab list using a random fake UUID ──
            GameProfile skinSource = pd.hasSkin()
                    ? pd.getSkinProfile()
                    : buildDefaultProfile(pd.getDisguiseName());
            tabUUID = UUID.randomUUID();
            GameProfile fakeProfile = buildSkinProfile(tabUUID, skinSource);

            player.connection.send(buildRawPlayerInfoPacket(fakeProfile));
            player.connection.send(new ClientboundAddEntityPacket(
                    puppetId, tabUUID,
                    player.getX(), player.getY(), player.getZ(),
                    player.getXRot(), player.getYRot(),
                    net.minecraft.world.entity.EntityType.PLAYER,
                    0, vel, player.getYHeadRot()
            ));
            sendMetadataPacketToId(player, puppetId, disguise);
            sendEquipmentPacketToId(player, puppetId, player);

            puppetMemberName = fakeProfile.name();

            // DO NOT schedule pendingTabRemove for self-view puppet tab entries.
            //
            // The PLAYER entity type requires the AddEntity UUID (tabUUID) to
            // match a live tab-list entry so the client can resolve the skin.
            // When the tab entry is removed via ClientboundPlayerInfoRemovePacket,
            // the client's handlePlayerInfoRemove() automatically despawns the
            // RemotePlayer entity whose UUID matches — killing the puppet.
            //
            // We manage the tab-entry lifetime explicitly: it stays alive for as
            // long as the puppet exists, and is removed together with the puppet
            // in removeSelfViewPuppetIfPresent() / syncSelfViewPuppets().

        } else if (disguise.getType().isInanimate()) {
            // Derive a unique UUID — the client entity registry rejects (or corrupts) a second
            // entity whose UUID matches an existing entry, so we must NOT reuse player.getUUID().
            UUID puppetEntityUUID = new UUID(
                    player.getUUID().getMostSignificantBits()  ^ 0xDEADBEEFCAFEBABEL,
                    player.getUUID().getLeastSignificantBits() ^ 0x0123456789ABCDEL);
            int data = getAddEntityData(disguise, player);
            player.connection.send(new ClientboundAddEntityPacket(
                    puppetId, puppetEntityUUID,
                    player.getX(), player.getY(), player.getZ(),
                    player.getXRot(), player.getYRot(),
                    disguise.getType().getEntityType(),
                    data, vel, player.getYHeadRot()
            ));
            sendMetadataPacketToId(player, puppetId, disguise);
            // Armor stand is inanimate-classified but is a LivingEntity — send equipment
            if (disguise.getType() == DisguiseType.ARMOR_STAND) {
                sendEquipmentPacketToId(player, puppetId, player);
            }
            // Entity.getScoreboardName() returns stringUUID (the UUID string), so use that
            // as the team member name so team collision rules apply to this puppet.
            puppetMemberName = puppetEntityUUID.toString();

        } else {
            // ── Mob disguise ──────────────────────────────────────────────────
            // Derive a unique UUID for the same reason as above.
            UUID puppetEntityUUID = new UUID(
                    player.getUUID().getMostSignificantBits()  ^ 0xDEADBEEFCAFEBABEL,
                    player.getUUID().getLeastSignificantBits() ^ 0x0123456789ABCDEL);
            player.connection.send(new ClientboundAddEntityPacket(
                    puppetId, puppetEntityUUID,
                    player.getX(), player.getY(), player.getZ(),
                    player.getXRot(), player.getYRot(),
                    disguise.getType().getEntityType(),
                    0, vel, player.getYHeadRot()
            ));
            sendMetadataPacketToId(player, puppetId, disguise);
            sendEquipmentPacketToId(player, puppetId, player);
            // Entity.getScoreboardName() returns stringUUID (the UUID string), so use that
            // as the team member name so team collision rules apply to this puppet.
            puppetMemberName = puppetEntityUUID.toString();
        }

        // Scale only applies to living-entity puppets.  Inanimate entity types (boat, minecart,
        // falling block, etc.) are not LivingEntities on the client and the attribute handler
        // will crash with "Server tried to update attributes of a non-living entity".
        // ARMOR_STAND is classified as inanimate here but IS a LivingEntity — include it.
        if (!disguise.getType().isInanimate() || disguise.getType() == DisguiseType.ARMOR_STAND) {
            sendScaleAttributeToId(player, puppetId, 1.0 / 3.0);
        }

        // Send the player's current pose and movement flags immediately so the puppet starts
        // in the right state (e.g. already crouching).  syncSelfViewPuppets will keep it in
        // sync on subsequent ticks, but the first-tick "changed from -1" fallback is redundant
        // when the player is in the default standing state — sending it here is explicit.
        sendInitialPoseToPuppet(player, puppetId, disguise);

        // ── Collision suppression team ────────────────────────────────────────────
        // A client-side scoreboard team with collisionRule=NEVER prevents the puppet
        // from pushing the player (and vice versa). For player-type disguises the team
        // also hides the floating nametag above the puppet's head.
        //
        // Non-player puppets need a scoreboard name to join the team. We assign one by
        // sending a hidden custom-name metadata packet (DATA_CUSTOM_NAME_VISIBLE=false).
        // The client's Entity.getScoreboardName() then returns that string, allowing the
        // team system to recognise the entity as a member.
        //
        // Both the puppet AND the player are added to the same team so that the NEVER
        // rule is applied when the game checks whether they should push each other.
        selfTeamName = "dsg" + puppetId;
        try {
            net.minecraft.world.scores.Scoreboard tempSb = new net.minecraft.world.scores.Scoreboard();
            net.minecraft.world.scores.PlayerTeam team =
                    new net.minecraft.world.scores.PlayerTeam(tempSb, selfTeamName);
            team.setCollisionRule(net.minecraft.world.scores.Team.CollisionRule.NEVER);
            if (disguise instanceof PlayerDisguise) {
                // Also suppress the floating nametag above the player-type puppet.
                team.setNameTagVisibility(net.minecraft.world.scores.Team.Visibility.NEVER);
            }
            if (puppetMemberName != null) {
                team.getPlayers().add(puppetMemberName);          // puppet entity
            }
            team.getPlayers().add(player.getScoreboardName());    // player themselves
            player.connection.send(
                    net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
                            .createAddOrModifyPacket(team, true));
        } catch (Exception e) {
            DisguisesMod.LOGGER.debug("[Disguises] Could not create puppet team: {}", e.getMessage());
            selfTeamName = null;
        }

        selfViewPuppets.put(player.getUUID(), new SelfViewPuppet(puppetId, tabUUID, selfTeamName));
    }

    /**
     * Removes the self-view puppet entity from the player's client.
     * The player's own entity is completely unaffected (it was never touched).
     */
    public static void removeSelfView(ServerPlayer player) {
        removeSelfViewPuppetIfPresent(player);
    }

    /** Internal: despawn the puppet for this player, if one exists, and clear the map entry. */
    private static void removeSelfViewPuppetIfPresent(ServerPlayer player) {
        SelfViewPuppet puppet = selfViewPuppets.remove(player.getUUID());
        if (puppet == null) return;

        // 1. Explicitly remove the puppet entity first.
        //    This must come BEFORE removing the tab entry (step 2): when the client
        //    receives ClientboundPlayerInfoRemovePacket for a UUID that belongs to a
        //    live RemotePlayer entity, it automatically despawns that entity in
        //    handlePlayerInfoRemove().  Sending the explicit RemoveEntities first
        //    gives us controlled teardown order and avoids a double-remove race.
        SENDING_DISGUISE_REMOVE.set(true);
        try { player.connection.send(new ClientboundRemoveEntitiesPacket(puppet.puppetId())); }
        finally { SENDING_DISGUISE_REMOVE.set(false); }

        // 2. Clean up the fake tab-list entry that was keeping the puppet's skin alive.
        //    Cancel any deferred removal first (pendingTabRemove should be empty for
        //    self-view puppets since we stopped scheduling it, but guard anyway).
        if (puppet.tabUUID() != null) {
            UUID tu = puppet.tabUUID();
            pendingTabRemove.removeIf(tr -> tr.fakeUUID().equals(tu));
            player.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(tu)));
        }

        // 3. Remove the nametag-hide team so it does not persist on the client.
        if (puppet.selfTeamName() != null) {
            try {
                net.minecraft.world.scores.Scoreboard tempSb = new net.minecraft.world.scores.Scoreboard();
                net.minecraft.world.scores.PlayerTeam team =
                        new net.minecraft.world.scores.PlayerTeam(tempSb, puppet.selfTeamName());
                player.connection.send(
                        net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
                                .createRemovePacket(team));
            } catch (Exception e) {
                DisguisesMod.LOGGER.debug("[Disguises] Could not remove puppet nametag team: {}", e.getMessage());
            }
        }

        lastPuppetState.remove(player.getUUID());
    }

    // =========================================================================
    // Self-view puppet tick sync
    // =========================================================================

    /**
     * Called every tick from DisguisesMod.END_SERVER_TICK.
     *
     * Keeps each active self-view puppet in sync with its owner's position and
     * head rotation.  Also cleans up stale entries (player offline, disguise removed).
     */
    public static void syncSelfViewPuppets(MinecraftServer server) {
        selfViewPuppets.entrySet().removeIf(e -> {
            UUID playerUUID = e.getKey();
            SelfViewPuppet puppet = e.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
            if (player == null || !player.isAlive()) {
                // Player offline — no need to send remove (client is gone)
                lastPuppetState.remove(playerUUID);
                return true;
            }

            Disguise disguise = com.coffee.disguises.core.DisguiseManager.INSTANCE.getDisguise(player);
            if (disguise == null || !disguise.isSelfDisguise()) {
                // Disguise removed or self-view turned off — despawn puppet.
                // Remove the entity first, then the tab entry (same ordering rationale
                // as removeSelfViewPuppetIfPresent).
                SENDING_DISGUISE_REMOVE.set(true);
                try { player.connection.send(new ClientboundRemoveEntitiesPacket(puppet.puppetId())); }
                finally { SENDING_DISGUISE_REMOVE.set(false); }
                if (puppet.tabUUID() != null) {
                    pendingTabRemove.removeIf(tr -> tr.fakeUUID().equals(puppet.tabUUID()));
                    player.connection.send(new ClientboundPlayerInfoRemovePacket(
                            List.of(puppet.tabUUID())));
                }
                if (puppet.selfTeamName() != null) {
                    try {
                        net.minecraft.world.scores.Scoreboard tempSb = new net.minecraft.world.scores.Scoreboard();
                        net.minecraft.world.scores.PlayerTeam team =
                                new net.minecraft.world.scores.PlayerTeam(tempSb, puppet.selfTeamName());
                        player.connection.send(
                                net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket
                                        .createRemovePacket(team));
                    } catch (Exception ignored) {}
                }
                lastPuppetState.remove(playerUUID);
                return true;
            }

            // Sync puppet position + head rotation to follow the player
            player.connection.send(new ClientboundEntityPositionSyncPacket(
                    puppet.puppetId(), PositionMoveRotation.of(player), player.onGround()));

            byte headYRot = (byte) ((int) (player.getYHeadRot() * 256.0F / 360.0F));
            ClientboundRotateHeadPacket rotPkt = buildRotateHeadPacket(puppet.puppetId(), headYRot);
            if (rotPkt != null) player.connection.send(rotPkt);

            // ── Sync entity flags + pose ──────────────────────────────────────
            // Combines the disguise's static flags (on-fire, invisible, glowing) with the
            // player's real-time movement state (crouching, sprinting, swimming, elytra).
            byte staticFlags = (byte) (disguise.getWatcher().buildSharedFlags()
                    & (byte) (0x01 | 0x20 | 0x40)); // onFire | invisible | glowing
            byte dynamicFlags = 0;
            net.minecraft.world.entity.Pose pose = player.getPose();
            if (player.isShiftKeyDown())  dynamicFlags |= 0x02;  // sneak/crouch flag
            if (player.isSprinting())     dynamicFlags |= 0x08;
            if (player.isSwimming())      dynamicFlags |= 0x10;
            if (player.isFallFlying())    dynamicFlags |= (byte) 0x80;
            byte entityFlags = (byte) (staticFlags | dynamicFlags);

            long packed = ((long) (entityFlags & 0xFF)) | ((long) pose.ordinal() << 8);
            long[] lastState = lastPuppetState.computeIfAbsent(playerUUID, k -> new long[]{-1L});
            if (lastState[0] != packed) {
                lastState[0] = packed;
                java.util.List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> stateUpdate =
                        MetadataBuilder.buildEntityStateUpdate(entityFlags, pose);
                if (!stateUpdate.isEmpty()) {
                    SENDING_DISGUISE_METADATA.set(true);
                    try {
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(
                                puppet.puppetId(), stateUpdate));
                    } finally {
                        SENDING_DISGUISE_METADATA.set(false);
                    }
                }
            }

            return false;
        });
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
        // Ender Dragon model forward direction is 180° off from standard convention.
        // Offset yRot and yHeadRot by 180° so the client sees the dragon facing correctly.
        boolean isEnderDragon = disguise.getType() == DisguiseType.ENDER_DRAGON;
        observer.connection.send(new ClientboundAddEntityPacket(
                entity.getId(), entity.getUUID(),
                entity.getX(), entity.getY(), entity.getZ(),
                entity.getXRot(),
                isEnderDragon ? entity.getYRot() + 180.0f : entity.getYRot(),
                disguise.getType().getEntityType(),
                0, vel,
                isEnderDragon ? entity.getYHeadRot() + 180.0f : entity.getYHeadRot()
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
        GameProfile fakeProfile = buildSkinProfile(fakeUUID, skinSource);

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
    // Puppet / self-view helpers
    // =========================================================================

    /**
     * Sends the player's current movement state (crouching, sprinting, etc.) to a
     * newly spawned puppet so it is correct from the very first frame.
     */
    private static void sendInitialPoseToPuppet(ServerPlayer player, int puppetId, Disguise disguise) {
        byte staticFlags = (byte) (disguise.getWatcher().buildSharedFlags() & (byte) (0x01 | 0x20 | 0x40));
        byte dynamicFlags = 0;
        if (player.isShiftKeyDown()) dynamicFlags |= 0x02;
        if (player.isSprinting())    dynamicFlags |= 0x08;
        if (player.isSwimming())     dynamicFlags |= 0x10;
        if (player.isFallFlying())   dynamicFlags |= (byte) 0x80;
        byte entityFlags = (byte) (staticFlags | dynamicFlags);

        java.util.List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> update =
                MetadataBuilder.buildEntityStateUpdate(entityFlags, player.getPose());
        if (update.isEmpty()) return;
        SENDING_DISGUISE_METADATA.set(true);
        try {
            player.connection.send(new ClientboundSetEntityDataPacket(puppetId, update));
        } finally {
            SENDING_DISGUISE_METADATA.set(false);
        }
    }

    /**
     * Forwards Entity base-field data updates (indices 0–7) from the player's own entity
     * to the self-view puppet.  Called by EntityDataUpdateMixin when the player receives
     * their own {@code ClientboundSetEntityDataPacket} (via vanilla's broadcastAndSend).
     *
     * This is the primary mechanism for syncing crouching, sprinting, swimming, elytra,
     * and other pose/flag changes to the puppet in real-time — no polling required.
     * Only indices 0–7 are forwarded: these are the universal Entity base fields
     * (shared flags, air, custom name, pose, etc.) that are valid for any entity type.
     */
    public static void forwardEntityDataToPuppet(ServerPlayer player,
                                                 ClientboundSetEntityDataPacket original) {
        SelfViewPuppet puppet = selfViewPuppets.get(player.getUUID());
        if (puppet == null) return;

        List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> toForward =
                original.packedItems().stream()
                        .filter(v -> v.id() <= 7)
                        .toList();
        if (toForward.isEmpty()) return;

        SENDING_DISGUISE_METADATA.set(true);
        try {
            player.connection.send(new ClientboundSetEntityDataPacket(puppet.puppetId(), toForward));
        } finally {
            SENDING_DISGUISE_METADATA.set(false);
        }
    }

    /**
     * Forwards an arm-swing animation to the self-view puppet for the given player.
     * Called from LivingEntitySwingMixin when a player begins a new swing, so the
     * puppet mirrors the arm animation in third-person view.
     *
     * @param player the swinging player
     * @param action one of {@code ClientboundAnimatePacket.SWING_MAIN_HAND} (0) or
     *               {@code ClientboundAnimatePacket.SWING_OFF_HAND} (3)
     */
    public static void forwardAnimationToPuppet(ServerPlayer player, int action) {
        SelfViewPuppet puppet = selfViewPuppets.get(player.getUUID());
        if (puppet == null) return;
        // ClientboundAnimatePacket handler unconditionally casts to LivingEntity on the client.
        // Sending it to an inanimate puppet (TNT, Boat, etc.) causes a ClassCastException crash.
        Disguise animDisguise = DisguiseManager.INSTANCE.getDisguise(player);
        if (animDisguise != null && animDisguise.getType().isInanimate()) return;
        try {
            // ClientboundAnimatePacket's public constructor requires an Entity, so we
            // build the packet by writing raw bytes and decoding via STREAM_CODEC.
            net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(10));
            buf.writeVarInt(puppet.puppetId());
            buf.writeVarInt(action);
            ClientboundAnimatePacket pkt = ClientboundAnimatePacket.STREAM_CODEC.decode(buf);
            buf.release();
            player.connection.send(pkt);
        } catch (Exception e) {
            DisguisesMod.LOGGER.debug("[Disguises] forwardAnimationToPuppet failed: {}", e.getMessage());
        }
    }

    /**
     * Sends a metadata packet for a puppet entity (arbitrary int ID, not in server registry).
     * Uses {@link #SENDING_DISGUISE_METADATA} bypass so the mixin does not strip it.
     */
    private static void sendMetadataPacketToId(ServerPlayer observer, int entityId, Disguise disguise) {
        try {
            List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> values =
                    MetadataBuilder.build(disguise.getWatcher(), disguise.getType());
            if (!values.isEmpty()) {
                SENDING_DISGUISE_METADATA.set(true);
                try {
                    observer.connection.send(new ClientboundSetEntityDataPacket(entityId, values));
                } finally {
                    SENDING_DISGUISE_METADATA.set(false);
                }
            }
        } catch (Exception e) {
            DisguisesMod.LOGGER.warn("[Disguises] Failed to send puppet metadata (id={}): {}",
                    entityId, e.getMessage());
        }
    }

    /**
     * Sends equipment for a puppet entity (arbitrary int ID).
     * Reads slot contents from {@code source} but addresses the packet to {@code entityId}.
     */
    private static void sendEquipmentPacketToId(ServerPlayer observer, int entityId,
                                                LivingEntity source) {
        List<com.mojang.datafixers.util.Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            equipment.add(com.mojang.datafixers.util.Pair.of(slot, source.getItemBySlot(slot).copy()));
        }
        observer.connection.send(new ClientboundSetEquipmentPacket(entityId, equipment));
    }

    /**
     * Sends a scale attribute update addressed to an arbitrary entity ID (no real server entity needed).
     * Uses the private {@code (int, List<AttributeSnapshot>)} constructor via reflection so we can
     * specify any entity ID without needing an {@link net.minecraft.world.entity.ai.attributes.AttributeInstance}.
     */
    private static void sendScaleAttributeToId(ServerPlayer observer, int entityId, double scale) {
        try {
            var snapshot = new ClientboundUpdateAttributesPacket.AttributeSnapshot(
                    net.minecraft.world.entity.ai.attributes.Attributes.SCALE,
                    scale,
                    java.util.Collections.emptyList());
            java.lang.reflect.Constructor<ClientboundUpdateAttributesPacket> ctor =
                    ClientboundUpdateAttributesPacket.class.getDeclaredConstructor(int.class, List.class);
            ctor.setAccessible(true);
            observer.connection.send(ctor.newInstance(entityId, List.of(snapshot)));
        } catch (Exception e) {
            DisguisesMod.LOGGER.warn("[Disguises] Failed to send scale attribute (id={}): {}",
                    entityId, e.getMessage());
        }
    }

    /**
     * Builds a {@link ClientboundRotateHeadPacket} addressed to an arbitrary entity ID.
     *
     * The public constructor only accepts an {@link Entity} argument (from which it reads
     * the entity ID).  We decode the packet from a raw buffer instead, which lets us
     * specify any int ID without needing a real server-side entity.
     */
    public static ClientboundRotateHeadPacket buildRotateHeadPacket(int entityId, byte yHeadRot) {
        try {
            net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(5));
            buf.writeVarInt(entityId);
            buf.writeByte(yHeadRot & 0xFF);
            ClientboundRotateHeadPacket pkt = ClientboundRotateHeadPacket.STREAM_CODEC.decode(buf);
            buf.release();
            return pkt;
        } catch (Exception e) {
            DisguisesMod.LOGGER.debug("[Disguises] buildRotateHeadPacket({}) failed: {}",
                    entityId, e.getMessage());
            return null;
        }
    }

    /**
     * Builds a GameProfile with a custom UUID but the skin properties (textures) taken
     * from {@code skinSource}.
     *
     * GameProfile is a record in authlib 7.x with a public 3-arg canonical constructor.
     * PropertyMap has no no-arg constructor and PropertyMap.EMPTY is immutable, so we
     * must construct a fresh mutable PropertyMap and pass it to the 3-arg constructor.
     */
    private static GameProfile buildSkinProfile(UUID fakeUUID, GameProfile skinSource) {
        com.mojang.authlib.properties.PropertyMap props = new com.mojang.authlib.properties.PropertyMap(
                com.google.common.collect.LinkedHashMultimap.create(skinSource.properties()));
        return new GameProfile(fakeUUID, skinSource.name(), props);
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
        // Entry is a record in MC 1.21.11 with this exact public constructor:
        //   Entry(UUID profileId, GameProfile profile, boolean listed, int latency,
        //         GameType gameMode, Component displayName, boolean showHat,
        //         int listOrder, RemoteChatSession.Data chatSession)
        try {
            return new ClientboundPlayerInfoUpdatePacket.Entry(
                    fakeProfile.id(),           // profileId
                    fakeProfile,                // profile (carries textures property)
                    true,                       // listed
                    0,                          // latency
                    net.minecraft.world.level.GameType.SURVIVAL, // gameMode
                    null,                       // displayName
                    false,                      // showHat
                    0,                          // listOrder
                    null                        // chatSession
            );
        } catch (Exception e) {
            throw new RuntimeException("[Disguises] Could not construct ClientboundPlayerInfoUpdatePacket.Entry: "
                    + e.getMessage(), e);
        }
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

            SENDING_DISGUISE_REMOVE.set(true);
            try { player.connection.send(new ClientboundRemoveEntitiesPacket(entity.getId())); }
            finally { SENDING_DISGUISE_REMOVE.set(false); }
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

    // =========================================================================
    // Vanished + disguised position sync
    // =========================================================================

    /**
     * Called every server tick from DisguisesMod.
     *
     * When a player is vanished, their vanish mod removes unauthorized observers
     * from vanilla's ServerEntity.seenBy set. This stops ALL movement/rotation
     * packets to those observers, so the disguise entity freezes on their client.
     *
     * This method detects exactly which observers vanilla has stopped tracking
     * (by reading ChunkMap.TrackedEntity.seenBy via mixin accessor) and manually
     * sends compact delta-position + head-rotation packets to those observers
     * every tick.
     *
     * This approach works regardless of which vanish mod is used — it detects
     * the effect (observer removed from vanilla tracking) rather than relying on
     * a VanishProvider integration.
     */
    public static void syncVanishedDisguisedPositions(MinecraftServer server) {
        // Remove stale entries for entities that are no longer disguised.
        lastSyncPos.keySet().removeIf(
                uuid -> !com.coffee.disguises.core.DisguiseManager.INSTANCE.isDisguised(uuid));

        double rangeSq = 128.0 * 128.0;

        for (UUID uuid : com.coffee.disguises.core.DisguiseManager.INSTANCE.getAllDisguisedUUIDs()) {
            // Find the entity across all loaded levels.
            Entity entity = null;
            for (ServerLevel lvl : server.getAllLevels()) {
                entity = lvl.getEntity(uuid);
                if (entity != null) break;
            }
            if (entity == null || !entity.isAlive()) { lastSyncPos.remove(uuid); continue; }
            if (!(entity.level() instanceof ServerLevel level)) continue;

            Disguise disguise = com.coffee.disguises.core.DisguiseManager.INSTANCE.getDisguise(entity);
            if (disguise == null) continue;

            // Get vanilla's seenBy set for this entity via the ChunkMapAccessor.
            // seenBy contains every ServerPlayerConnection that vanilla ServerEntity
            // is currently broadcasting movement to.  Any observer NOT in this set
            // is being excluded by something (a vanish mod) and needs our manual sync.
            java.util.Set<net.minecraft.server.network.ServerPlayerConnection> seenBy =
                    getVanillaSeenBy(entity, level);

            // Collect observers in range who are NOT in vanilla's seenBy.
            List<ServerPlayer> targets = new ArrayList<>();
            for (ServerPlayer observer : server.getPlayerList().getPlayers()) {
                if (observer == entity) continue;
                if (!observer.level().dimension().equals(level.dimension())) continue;
                if (observer.distanceToSqr(entity) > rangeSq) continue;
                // If vanilla is already sending movement to this observer, skip.
                if (seenBy != null && seenBy.contains(observer.connection)) continue;
                targets.add(observer);
            }

            if (targets.isEmpty()) { lastSyncPos.remove(uuid); continue; }

            ConcurrentHashMap<UUID, long[]> perObserver =
                    lastSyncPos.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());

            long fx = (long) Math.floor(entity.getX() * 4096.0);
            long fy = (long) Math.floor(entity.getY() * 4096.0);
            long fz = (long) Math.floor(entity.getZ() * 4096.0);
            byte yRot     = (byte) ((int) (entity.getYRot()     * 256.0F / 360.0F));
            byte xRot     = (byte) ((int) (entity.getXRot()     * 256.0F / 360.0F));
            byte headYRot = (byte) ((int) (entity.getYHeadRot() * 256.0F / 360.0F));

            final Entity   fe = entity;
            final Disguise fd = disguise;

            for (ServerPlayer observer : targets) {
                long[] last = perObserver.get(observer.getUUID());
                long dx = last != null ? fx - last[0] : 0;
                long dy = last != null ? fy - last[1] : 0;
                long dz = last != null ? fz - last[2] : 0;

                boolean bigJump = last == null
                        || Math.abs(dx) > Short.MAX_VALUE
                        || Math.abs(dy) > Short.MAX_VALUE
                        || Math.abs(dz) > Short.MAX_VALUE;

                if (bigJump) {
                    // First appearance or teleport: do a clean respawn of the disguise.
                    SENDING_DISGUISE_REMOVE.set(true);
                    try { observer.connection.send(new ClientboundRemoveEntitiesPacket(fe.getId())); }
                    finally { SENDING_DISGUISE_REMOVE.set(false); }
                    sendDisguisedSpawn(observer, fe, fd);
                    observer.connection.send(new ClientboundRotateHeadPacket(fe, headYRot));
                } else if (dx != 0 || dy != 0 || dz != 0) {
                    observer.connection.send(new ClientboundMoveEntityPacket.PosRot(
                            fe.getId(), (short) dx, (short) dy, (short) dz,
                            yRot, xRot, fe.onGround()));
                    observer.connection.send(new ClientboundRotateHeadPacket(fe, headYRot));
                } else {
                    observer.connection.send(new ClientboundMoveEntityPacket.Rot(
                            fe.getId(), yRot, xRot, fe.onGround()));
                    observer.connection.send(new ClientboundRotateHeadPacket(fe, headYRot));
                }
                perObserver.put(observer.getUUID(), new long[]{fx, fy, fz});
            }
        }
    }

    /**
     * Returns the set of ServerPlayerConnections that vanilla's ServerEntity is
     * currently broadcasting movement to for the given entity.
     *
     * Returns null if the entity is not currently tracked (in which case the caller
     * should treat all in-range observers as needing manual sync).
     */
    private static java.util.Set<net.minecraft.server.network.ServerPlayerConnection>
    getVanillaSeenBy(Entity entity, ServerLevel level) {
        try {
            com.coffee.disguises.mixin.ChunkMapAccessor chunkMapAccessor =
                    (com.coffee.disguises.mixin.ChunkMapAccessor) level.getChunkSource().chunkMap;
            // Raw type: ChunkMap.TrackedEntity is private and cannot be named in source.
            @SuppressWarnings("rawtypes")
            it.unimi.dsi.fastutil.ints.Int2ObjectMap entityMap =
                    chunkMapAccessor.disguises$getEntityMap();
            Object tracked = entityMap.get(entity.getId());
            if (tracked == null) return null;
            com.coffee.disguises.mixin.TrackedEntityAccessor trackedAccessor =
                    (com.coffee.disguises.mixin.TrackedEntityAccessor) tracked;
            return trackedAccessor.disguises$getSeenBy();
        } catch (Exception e) {
            DisguisesMod.LOGGER.warn("[Disguises] getVanillaSeenBy failed: {}", e.getMessage());
            return null;
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