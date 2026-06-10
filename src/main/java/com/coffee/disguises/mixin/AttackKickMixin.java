package com.coffee.disguises.mixin;

import com.coffee.disguises.packet.PacketInterceptor;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents the "Attempting to attack an invalid entity" kick when the target is
 * a disguised entity whose real type is unattackable (item, experience orb,
 * non-attackable arrow).  The observer's client sees the disguise — e.g. a
 * zombie — and legitimately swings at it; vanilla treats that swing as a cheat
 * attempt and disconnects the player.  Swallowing the packet is correct: even
 * without the kick, Player.attack() would no-op on an unattackable target.
 *
 * MC 26.1.2 version: attacks arrive on a dedicated ServerboundAttackPacket.
 * The 1.21.11 build excludes this file and compiles the override under
 * versions/1.21.11/src/main/java instead, where attacks still arrive through
 * ServerboundInteractPacket's ATTACK action.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class AttackKickMixin {

    @Shadow
    public ServerPlayer player;

    // getEntityOrPart is deprecated but is the exact lookup vanilla's handleAttack
    // performs — using it keeps our target resolution identical to the code we guard.
    @SuppressWarnings("deprecation")
    @Inject(method = "handleAttack", at = @At("HEAD"), cancellable = true)
    private void disguises$onHandleAttack(ServerboundAttackPacket packet, CallbackInfo ci) {
        // First pass runs on the netty thread; vanilla's ensureRunningOnSameThread
        // re-queues the packet onto the server thread, where we check for real.
        MinecraftServer server = player.level().getServer();
        if (server == null || !server.isSameThread()) return;

        Entity target = player.level().getEntityOrPart(packet.entityId());
        if (PacketInterceptor.shouldSwallowAttack(target)) {
            ci.cancel();
        }
    }
}
