package com.coffee.disguises.mixin;

import com.coffee.disguises.packet.PacketInterceptor;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
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
 * MC 1.21.11 version: attacks arrive as ServerboundInteractPacket with the
 * ATTACK action (detected via the dispatch Handler).  The 26.1.2 build uses the
 * shared-source variant of this class instead, which targets the dedicated
 * ServerboundAttackPacket handler introduced after 1.21.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class AttackKickMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void disguises$onHandleInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        // First pass runs on the netty thread; vanilla's ensureRunningOnSameThread
        // re-queues the packet onto the server thread, where we check for real.
        MinecraftServer server = player.level().getServer();
        if (server == null || !server.isSameThread()) return;

        // The action type has no accessor; dispatch to a recording handler to
        // find out whether this interact packet is an ATTACK.
        boolean[] attack = {false};
        packet.dispatch(new ServerboundInteractPacket.Handler() {
            @Override public void onInteraction(InteractionHand hand) {}
            @Override public void onInteraction(InteractionHand hand, Vec3 location) {}
            @Override public void onAttack() { attack[0] = true; }
        });
        if (!attack[0]) return;

        Entity target = packet.getTarget(player.level());
        if (PacketInterceptor.shouldSwallowAttack(target)) {
            ci.cancel();
        }
    }
}
