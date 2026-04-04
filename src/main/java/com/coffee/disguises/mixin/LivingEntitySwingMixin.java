package com.coffee.disguises.mixin;

import com.coffee.disguises.core.DisguiseManager;
import com.coffee.disguises.packet.PacketInterceptor;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forwards arm-swing animation to the self-view puppet when a disguised player swings.
 *
 * LivingEntity.swing(InteractionHand, boolean) is the single entry-point for both
 * main-hand and off-hand swings.  It sets {@code swinging = true} and dispatches
 * a ClientboundAnimatePacket to NEARBY observers via {@code broadcast} (which excludes
 * the entity itself).  Because the player never receives their own swing packet, the
 * self-view puppet would otherwise never play the swing animation.
 *
 * We inject at HEAD and check {@code this.swinging} to guard against re-entry during
 * an in-progress swing cycle (vanilla resets the cycle when called mid-swing, but we
 * only need to forward the animation once per new swing).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {

    @Shadow
    protected boolean swinging;

    @Inject(
            method = "swing(Lnet/minecraft/world/InteractionHand;Z)V",
            at = @At("HEAD")
    )
    private void disguises$onSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        // Only forward for server-side player entities
        if (!((Object) this instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        // Only forward when the player has an active disguise with self-view
        var disguise = DisguiseManager.INSTANCE.getDisguise(player);
        if (disguise == null || !disguise.isSelfDisguise()) return;

        // Guard against mid-swing re-entry: swinging == false means a new swing is starting
        if (swinging) return;

        int action = hand == InteractionHand.MAIN_HAND
                ? ClientboundAnimatePacket.SWING_MAIN_HAND
                : ClientboundAnimatePacket.SWING_OFF_HAND;
        PacketInterceptor.forwardAnimationToPuppet(player, action);
    }
}
