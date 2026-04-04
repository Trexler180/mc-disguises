package com.coffee.disguises.mixin;

import com.coffee.disguises.core.DisguiseManager;
import com.coffee.disguises.disguise.Disguise;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Intercepts outgoing packets for disguised entities and filters/drops those
 * that would cause client crashes due to type-layout mismatches.
 *
 * ─── SAFE INDEX RANGES ───────────────────────────────────────────────────────
 *
 * Entity base fields (always safe, indices 0-7):
 *   0  DATA_SHARED_FLAGS_ID          byte
 *   1  DATA_AIR_SUPPLY_ID            int
 *   2  DATA_CUSTOM_NAME              Optional<Component>
 *   3  DATA_CUSTOM_NAME_VISIBLE      boolean
 *   4  DATA_SILENT                   boolean
 *   5  DATA_NO_GRAVITY               boolean
 *   6  DATA_POSE                     Pose
 *   7  DATA_TICKS_FROZEN             int
 *
 * LivingEntity additions (safe for any living-entity disguise, indices 8-14):
 *   8  DATA_LIVING_ENTITY_FLAGS      byte
 *   9  DATA_HEALTH_ID                float
 *   10 DATA_EFFECT_COLOR_ID          int
 *   11 DATA_EFFECT_AMBIENCE_ID       boolean
 *   12 DATA_ARROW_COUNT_ID           int
 *   13 DATA_STINGER_COUNT_ID         int
 *   14 DATA_SLEEPING_POS_ID          Optional<BlockPos>
 *
 * Player-specific fields (indices 15-21) are ONLY safe to forward from the real
 * entity's data tracker when the real entity is also a Player. For any mob
 * disguised as a player (mob-as-player), indices 15+ from the mob's own tracker
 * are mob type-specific fields (e.g. Zombie/Husk index 15 = HumanoidArm enum,
 * Creeper index 15 = swell direction int, etc.) and will CRASH the client because
 * the client's RemotePlayer entity expects different types at those indices.
 *
 * ─── MOB-AS-PLAYER SPECIAL CASE ─────────────────────────────────────────────
 *
 * When a mob is disguised as a player we:
 *   • Cap vanilla mob data at MAX_LIVING_ENTITY_ID (14) — same as any other
 *     living disguise. This prevents the mob's type-specific fields from
 *     reaching the client's RemotePlayer entity.
 *   • Our own synthesized player-specific packets (skin parts byte, sent by
 *     PacketInterceptor.pendingDataResend) contain only indices > 14. They pass
 *     through via the allSafe early-return because every value in them is already
 *     within our chosen ceiling when we re-send them (we control their content).
 *     Setting maxSafeId = MAX_LIVING_ENTITY_ID is correct: if the packet is ALL
 *     above 14 it means it came from our own pendingDataResend and is safe.
 *     If it is a mix (some ≤ 14, some > 14) it is a vanilla mob update and
 *     the > 14 values are correctly stripped.
 *
 * ─── REAL-PLAYER-AS-PLAYER ───────────────────────────────────────────────────
 *
 * When a real ServerPlayer is disguised as another player, their own tracker
 * sends genuine Player-layout values at 15-21 (main hand, absorption, etc.)
 * which are valid for the client's RemotePlayer. We allow up to MAX_PLAYER_ENTITY_ID
 * in that case only.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class EntityDataUpdateMixin {

    /** Inclusive upper bound for base Entity accessor IDs. */
    private static final int MAX_BASE_ENTITY_ID   = 7;

    /** Inclusive upper bound for LivingEntity accessor IDs (shared by all living subtypes). */
    private static final int MAX_LIVING_ENTITY_ID = 14;

    /**
     * Inclusive upper bound for Player accessor IDs.
     * Only used when the REAL entity is also a ServerPlayer.
     */
    private static final int MAX_PLAYER_ENTITY_ID = 21;

    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void disguises$filterEntityDataUpdates(Packet<?> packet, CallbackInfo ci) {

        // If PacketInterceptor.sendMetadataPacket is currently executing, this packet
        // came from MetadataBuilder and carries correct types for the disguise entity.
        // Do NOT filter it — type-specific indices (≥15) such as sheep color (17),
        // baby (16), creeper powered (17), etc. must reach the client intact.
        if (Boolean.TRUE.equals(com.coffee.disguises.packet.PacketInterceptor.SENDING_DISGUISE_METADATA.get())) return;

        if (!((Object) this instanceof ServerGamePacketListenerImpl gameListener)) return;
        ServerPlayer observer = gameListener.player;
        if (!(observer.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

        // ── ClientboundRemoveEntitiesPacket ──────────────────────────────────
        // Suppress any external remove packet (e.g. from a vanish mod) that would
        // destroy a disguised entity on the observer's client.
        //
        // SENDING_DISGUISE_REMOVE: if our own code sent this packet (controlled
        // respawn, undisguise, self-view), it must pass through untouched.
        // Otherwise, strip any entity IDs that belong to disguised+alive entities.
        // Non-disguised IDs in the same packet are re-sent in a trimmed packet.
        if (packet instanceof ClientboundRemoveEntitiesPacket removePacket) {
            if (Boolean.TRUE.equals(com.coffee.disguises.packet.PacketInterceptor.SENDING_DISGUISE_REMOVE.get())) {
                return; // Our own intentional remove — allow it through.
            }
            IntList toKeep = new IntArrayList();
            boolean anyFiltered = false;
            for (int entityId : removePacket.getEntityIds()) {
                Entity removed = serverLevel.getEntity(entityId);
                // Suppress if alive + disguised regardless of vanish state.
                // We do NOT check isVanishedFrom here: vanish mods often send
                // the remove packet BEFORE setting the vanished flag, causing a
                // timing race where the check would incorrectly pass the packet.
                if (removed != null && removed.isAlive()
                        && DisguiseManager.INSTANCE.isDisguised(removed)) {
                    anyFiltered = true;
                } else {
                    toKeep.add(entityId);
                }
            }
            if (anyFiltered) {
                ci.cancel();
                if (!toKeep.isEmpty()) {
                    observer.connection.send(new ClientboundRemoveEntitiesPacket(toKeep));
                }
            }
            return;
        }

        // ── ClientboundTakeItemEntityPacket ──────────────────────────────────
        // Client unconditionally casts the collector to LivingEntity.
        // Drop for inanimate disguises to prevent ClassCastException.
        if (packet instanceof net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket takePacket) {
            Entity collector = serverLevel.getEntity(takePacket.getPlayerId());
            if (collector != null) {
                Disguise d = DisguiseManager.INSTANCE.getDisguise(collector);
                if (d != null && d.getType().isInanimate()) ci.cancel();
            }
            return;
        }

        // ── ClientboundAnimatePacket ──────────────────────────────────────────
        // Client unconditionally casts to LivingEntity; drop for inanimate disguises.
        // For certain disguise types whose animation is NOT driven by LivingEntity.swingTime
        // but by a type-specific DataTracker field, we also emit that field here.
        if (packet instanceof ClientboundAnimatePacket animPacket) {
            Entity entity = serverLevel.getEntity(animPacket.getId());
            if (entity == null) return;
            Disguise disguise = DisguiseManager.INSTANCE.getDisguise(entity);
            if (disguise == null || observer == entity) return;
            if (disguise.getType().isInanimate()) {
                ci.cancel();
                return;
            }

            // ── Iron Golem: attack animation driven by AnimationState via entity event ──
            // In MC 1.21.x IronGolem has no DATA_ATTACK_ANIMATION_TICK_ID DataTracker field.
            // IronGolemModel uses an AnimationState (client-side) started by
            // IronGolem.handleEntityEvent((byte) 4). The real entity sends this event
            // via broadcastEntityEvent, but a disguised player only sends a swing packet.
            // We translate the swing into the entity event that the golem model expects.
            if (disguise.getType() == com.coffee.disguises.disguise.DisguiseType.IRON_GOLEM
                    || disguise.getType() == com.coffee.disguises.disguise.DisguiseType.WARDEN) {
                observer.connection.send(
                        new net.minecraft.network.protocol.game.ClientboundEntityEventPacket(
                                entity, (byte) 4));
            }

            return;
        }

        // ── ClientboundUpdateAttributesPacket ─────────────────────────────────
        // "Server tried to update attributes of a non-living entity" crash for
        // inanimate disguises. Safe to forward for living disguises.
        if (packet instanceof ClientboundUpdateAttributesPacket attrPacket) {
            int attrEntityId = attrPacket.getEntityId();
            Entity attrEntity = serverLevel.getEntity(attrEntityId);
            if (attrEntity == null) {
                // Entity not found on server — could be a self-view puppet.
                // Puppet IDs are 1_000_000_000 + real player entity ID.
                // If the corresponding player has an inanimate disguise, cancel:
                // non-LivingEntity clients crash when they receive attribute packets.
                if (attrEntityId >= 1_000_000_000) {
                    Entity real = serverLevel.getEntity(attrEntityId - 1_000_000_000);
                    if (real instanceof ServerPlayer sp) {
                        Disguise d = DisguiseManager.INSTANCE.getDisguise(sp);
                        if (d != null && d.getType().isInanimate()) {
                            ci.cancel();
                        }
                    }
                }
                return;
            }
            Disguise disguise = DisguiseManager.INSTANCE.getDisguise(attrEntity);
            if (disguise == null || observer == attrEntity) return;
            if (disguise.getType().isInanimate()) ci.cancel();
            return;
        }

        // ── ClientboundMoveEntityPacket / ClientboundRotateHeadPacket (Ender Dragon) ──
        // The EnderDragon model's "forward" direction is 180° off from standard entity
        // convention. The renderer reads yRot from DragonFlightHistory which accumulates
        // values from movement packets. Add 128 (= +180°) to the transmitted yRot byte
        // so the client's dragon faces the same direction as the disguised entity.
        //
        // Guard: the re-sent corrected packet goes through this same mixin injection point.
        // SENDING_DRAGON_ROTATION prevents infinite recursion (→ StackOverflowError).
        if (packet instanceof ClientboundMoveEntityPacket movePacket) {
            if (!movePacket.hasRotation()) return;
            if (Boolean.TRUE.equals(com.coffee.disguises.packet.PacketInterceptor.SENDING_DRAGON_ROTATION.get())) return;
            Entity moveEntity = movePacket.getEntity(serverLevel);
            if (moveEntity == null) return;
            Disguise moveDis = DisguiseManager.INSTANCE.getDisguise(moveEntity);
            if (moveDis == null || observer == moveEntity
                    || moveDis.getType() != com.coffee.disguises.disguise.DisguiseType.ENDER_DRAGON) return;
            ci.cancel();
            byte offsetYRot = (byte) Math.round((movePacket.getYRot() + 180.0f) * 256.0F / 360.0F);
            byte xRot = (byte) Math.round(movePacket.getXRot() * 256.0F / 360.0F);
            com.coffee.disguises.packet.PacketInterceptor.SENDING_DRAGON_ROTATION.set(true);
            try {
                if (movePacket instanceof ClientboundMoveEntityPacket.PosRot posRot) {
                    observer.connection.send(new ClientboundMoveEntityPacket.PosRot(
                            moveEntity.getId(), posRot.getXa(), posRot.getYa(), posRot.getZa(),
                            offsetYRot, xRot, movePacket.isOnGround()));
                } else {
                    observer.connection.send(new ClientboundMoveEntityPacket.Rot(
                            moveEntity.getId(), offsetYRot, xRot, movePacket.isOnGround()));
                }
            } finally {
                com.coffee.disguises.packet.PacketInterceptor.SENDING_DRAGON_ROTATION.set(false);
            }
            return;
        }

        if (packet instanceof ClientboundRotateHeadPacket headPacket) {
            if (Boolean.TRUE.equals(com.coffee.disguises.packet.PacketInterceptor.SENDING_DRAGON_ROTATION.get())) return;
            Entity headEntity = headPacket.getEntity(serverLevel);
            if (headEntity == null) return;
            Disguise headDis = DisguiseManager.INSTANCE.getDisguise(headEntity);
            if (headDis == null || observer == headEntity
                    || headDis.getType() != com.coffee.disguises.disguise.DisguiseType.ENDER_DRAGON) return;
            ci.cancel();
            byte offsetHeadYRot = (byte) Math.round((headPacket.getYHeadRot() + 180.0f) * 256.0F / 360.0F);
            com.coffee.disguises.packet.PacketInterceptor.SENDING_DRAGON_ROTATION.set(true);
            try {
                ClientboundRotateHeadPacket newPkt =
                        com.coffee.disguises.packet.PacketInterceptor.buildRotateHeadPacket(
                                headEntity.getId(), offsetHeadYRot);
                if (newPkt != null) observer.connection.send(newPkt);
            } finally {
                com.coffee.disguises.packet.PacketInterceptor.SENDING_DRAGON_ROTATION.set(false);
            }
            return;
        }

        // ── ClientboundSetEntityDataPacket ────────────────────────────────────
        if (!(packet instanceof ClientboundSetEntityDataPacket dataPacket)) return;

        Entity entity = serverLevel.getEntity(dataPacket.id());
        if (entity == null) return;

        Disguise disguise = DisguiseManager.INSTANCE.getDisguise(entity);
        if (disguise == null) return;

        // Self-send: the observer IS the disguised entity.
        //
        // With the puppet self-view approach, the player's own entity ID is never
        // replaced on the client — the LocalPlayer object always remains at its
        // original ID with full Player-layout fields.  All self-send data is safe
        // to pass through unfiltered regardless of whether self-view is enabled.
        //
        // Additionally, forward Entity base fields (indices 0–7) to the self-view
        // puppet so it mirrors the player's pose, crouch, sprint, and swim state
        // in real-time.  This is the primary crouch-sync mechanism: vanilla sends
        // DATA_POSE and DATA_SHARED_FLAGS_ID to the player via broadcastAndSend
        // whenever they change, and we relay those same values to the puppet.
        if (observer == entity) {
            if (disguise.isSelfDisguise()) {
                com.coffee.disguises.packet.PacketInterceptor.forwardEntityDataToPuppet(
                        observer, dataPacket);
            }
            return;
        }

        // Determine the safe ceiling for this disguise + real-entity combination.
        //
        //   inanimate disguise                       → 0–7   (Entity base only)
        //   PLAYER disguise, real=ServerPlayer       → 0–21  (genuine Player layout on both sides)
        //   any living disguise, real=non-LivingEntity → 0–7
        //       Non-LivingEntity types (boats, item frames, firework rockets, tridents, TNT, …)
        //       store entity-specific data starting at index 8 with completely different serializer
        //       types than what LivingEntity uses there.  Even though those indices are within the
        //       "living" ceiling of 14 they would still cause an IllegalStateException on the client
        //       (e.g. AbstractBoat.DATA_ID_HURT is an int at index 8; the client's living disguise
        //       entity expects DATA_LIVING_ENTITY_FLAGS which is a byte — type mismatch → crash).
        //       Cap at 7 so only the shared Entity base fields reach the client.
        //   any other living disguise                → 0–14
        final int maxSafeId;
        if (disguise.getType().isInanimate()) {
            maxSafeId = MAX_BASE_ENTITY_ID;
        } else if (disguise.getType() == com.coffee.disguises.disguise.DisguiseType.PLAYER
                && entity instanceof ServerPlayer) {
            // Real player disguised as another player — their own tracker sends
            // genuine Player-layout values, safe up to 21.
            maxSafeId = MAX_PLAYER_ENTITY_ID;
        } else if (!(entity instanceof LivingEntity)) {
            // Non-LivingEntity real entity disguised as a living type (mob or player).
            // Its fields at indices 8+ use entity-specific serializer types that are
            // incompatible with whatever the client's living-entity object expects there.
            // Only the base Entity indices (0–7) are shared and safe to forward.
            maxSafeId = MAX_BASE_ENTITY_ID;
        } else {
            // Living real entity disguised as any non-inanimate disguise.
            // Cap at 14: type-specific fields (15+) from the real mob have wrong types
            // for the client's disguise entity (e.g. Pillager's crossbow-charged boolean
            // at index 17 clashes with RemotePlayer's skin-customisation byte at 17).
            maxSafeId = MAX_LIVING_ENTITY_ID;
        }

        List<SynchedEntityData.DataValue<?>> items = dataPacket.packedItems();

        // If every value is already within the safe range this packet either
        // came from our own re-send path (pendingDataResend) or is a routine
        // base/living-entity update — pass straight through.
        boolean allSafe = items.stream().allMatch(v -> v.id() <= maxSafeId);
        if (allSafe) return;

        // Strip anything above the safe ceiling and re-send the clean subset.
        List<SynchedEntityData.DataValue<?>> filtered = items.stream()
                .filter(v -> v.id() <= maxSafeId)
                .toList();

        ci.cancel();

        if (!filtered.isEmpty()) {
            observer.connection.send(new ClientboundSetEntityDataPacket(dataPacket.id(), filtered));
        }
    }
}