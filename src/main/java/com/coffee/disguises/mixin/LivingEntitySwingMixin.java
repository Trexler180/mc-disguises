package com.coffee.disguises.mixin;

import com.coffee.disguises.core.DisguiseManager;
import com.coffee.disguises.packet.PacketInterceptor;
import net.minecraft.network.protocol.game.ClientboundSwingAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forwards arm-swing animation to the self-view puppet when a disguised player swings.
 *
 * LivingEntity.swing(InteractionHand, SwingAnimation, boolean) is the single
 * entry-point for both main-hand and off-hand swings.  It sends a
 * ClientboundSwingAnimationPacket to tracking observers, which never includes the
 * player's own client unless {@code updateSelf} is set — and even then it targets
 * the player, not the puppet — so the self-view puppet would otherwise never swing.
 *
 * swing returns whether a new swing actually started (false while one is still in
 * progress), so forwarding on a true return sends exactly one packet per swing.
 *
 * This is the 26.3 version; older targets compile
 * versions/_legacy-common/.../LivingEntitySwingMixin.java instead.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {

    @Inject(
            method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z",
            at = @At("RETURN")
    )
    private void disguises$onSwing(InteractionHand hand, SwingAnimation animation, boolean updateSelf,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        if (!((Object) this instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        // Only forward when the player has an active disguise with self-view
        var disguise = DisguiseManager.INSTANCE.getDisguise(player);
        if (disguise == null || !disguise.isSelfDisguise()) return;

        PacketInterceptor.forwardToPuppet(player,
                puppetId -> new ClientboundSwingAnimationPacket(puppetId, hand, animation));
    }
}
