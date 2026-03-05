package com.coffee.disguises.mixin;

import com.coffee.disguises.core.DisguiseManager;
import com.coffee.disguises.disguise.Disguise;
import com.coffee.disguises.packet.PacketInterceptor;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Intercepts ServerEntity.sendPairingData so every disguised entity is spawned
 * with the disguise type instead of the real type.
 *
 * ─── Self-view support ────────────────────────────────────────────────────────
 *
 * Previously the mixin returned early whenever player == entity so the
 * disguised player would never receive their own disguise packets.
 *
 * Now:
 *   • If the disguise has isSelfDisguise() == false  →  skip as before
 *     (player's own entity is rendered by the LocalPlayer object, not via
 *     the entity registry, so sending nothing is the safe default).
 *
 *   • If the disguise has isSelfDisguise() == true   →  fall through and
 *     call PacketInterceptor.sendDisguisedSpawn.  This updates the client-
 *     side entity registry entry for the player's ID to use the disguise
 *     EntityType, which affects name tags, particles, and any other system
 *     that looks up entities by ID on the client.  The LocalPlayer HUD/hands
 *     are unaffected (they don't go through the registry), so this is safe.
 *
 * This injection fires for the player's own entity during:
 *   • Initial world load
 *   • Chunk re-entry after unloading
 *   • Respawn (handled via a separate path in DisguiseManager)
 *
 * ─── Verify in IntelliJ ──────────────────────────────────────────────────────
 *
 * Ctrl+N → "ServerEntity" → confirm:
 *   1. Class: net.minecraft.server.level.ServerEntity
 *   2. Field: entity (type Entity)
 *   3. Method: sendPairingData(ServerPlayer, Consumer)
 */
@Mixin(net.minecraft.server.level.ServerEntity.class)
public abstract class ServerEntityMixin {

    @Shadow
    private Entity entity;

    @Inject(
            method = "sendPairingData(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void disguises$onSendPairingData(
            ServerPlayer player,
            Consumer<Packet<ClientGamePacketListener>> packetConsumer,
            CallbackInfo ci) {

        if (entity == null) return;

        Disguise disguise = DisguiseManager.INSTANCE.getDisguise(entity);
        if (disguise == null) return;

        if (player == entity) {
            // Self-view: only send disguise packets to the player for their own
            // entity if selfDisguise is explicitly enabled.  Sending without the
            // flag could break the client's own entity state on reload.
            if (!disguise.isSelfDisguise()) return;
            // Fall through — send disguised spawn to self
        }

        // Cancel vanilla's sendPairingData so the real entity type never reaches the client.
        ci.cancel();

        // Send disguised spawn + metadata + equipment.
        PacketInterceptor.sendDisguisedSpawn(player, entity, disguise);
    }
}