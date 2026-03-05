package com.coffee.disguises.mixin;

import com.coffee.disguises.core.DisguiseManager;
import com.coffee.disguises.disguise.Disguise;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
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

        if (!((Object) this instanceof ServerGamePacketListenerImpl gameListener)) return;
        ServerPlayer observer = gameListener.player;
        if (!(observer.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

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
        if (packet instanceof ClientboundAnimatePacket animPacket) {
            Entity entity = serverLevel.getEntity(animPacket.getId());
            if (entity == null) return;
            Disguise disguise = DisguiseManager.INSTANCE.getDisguise(entity);
            if (disguise == null || observer == entity) return;
            if (disguise.getType().isInanimate()) ci.cancel();
            return;
        }

        // ── ClientboundUpdateAttributesPacket ─────────────────────────────────
        // "Server tried to update attributes of a non-living entity" crash for
        // inanimate disguises. Safe to forward for living disguises.
        if (packet instanceof ClientboundUpdateAttributesPacket attrPacket) {
            Entity entity = serverLevel.getEntity(attrPacket.getEntityId());
            if (entity == null) return;
            Disguise disguise = DisguiseManager.INSTANCE.getDisguise(entity);
            if (disguise == null || observer == entity) return;
            if (disguise.getType().isInanimate()) ci.cancel();
            return;
        }

        // ── ClientboundSetEntityDataPacket ────────────────────────────────────
        if (!(packet instanceof ClientboundSetEntityDataPacket dataPacket)) return;

        Entity entity = serverLevel.getEntity(dataPacket.id());
        if (entity == null) return;

        Disguise disguise = DisguiseManager.INSTANCE.getDisguise(entity);
        if (disguise == null) return;
        if (observer == entity) return;

        // Determine the safe ceiling for this disguise + real-entity combination.
        //
        //   inanimate disguise            → 0–7   (Entity base only)
        //   PLAYER disguise, real=Player  → 0–21  (full Player layout is valid on both sides)
        //   PLAYER disguise, real=mob     → 0–14  (mob fields at 15+ are wrong types for
        //                                           RemotePlayer; our own synthesized player
        //                                           packets pass via allSafe early-return)
        //   any other living disguise     → 0–14
        final int maxSafeId;
        if (disguise.getType().isInanimate()) {
            maxSafeId = MAX_BASE_ENTITY_ID;
        } else if (disguise.getType() == com.coffee.disguises.disguise.DisguiseType.PLAYER
                && entity instanceof ServerPlayer) {
            // Real player disguised as another player — their own tracker sends
            // genuine Player-layout values, safe up to 21.
            maxSafeId = MAX_PLAYER_ENTITY_ID;
        } else {
            // Mob/inanimate-as-player, or any other living disguise.
            // Cap at 14: mob type-specific fields (15+) have wrong types for the
            // client's RemotePlayer (or whatever living disguise type was chosen).
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