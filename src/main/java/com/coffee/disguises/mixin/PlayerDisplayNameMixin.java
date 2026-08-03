package com.coffee.disguises.mixin;

import com.coffee.disguises.core.DisguiseNameResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Substitutes the presentation-only player name used by vanilla system messages.
 * Identity-bearing APIs such as GameProfile, getName, getPlainTextName and
 * getScoreboardName deliberately remain untouched.
 */
@Mixin(Player.class)
public abstract class PlayerDisplayNameMixin {

    @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
    private void disguises$substituteSystemMessageName(CallbackInfoReturnable<Component> cir) {
        if (!((Object) this instanceof ServerPlayer player)) return;

        Component disguiseName = DisguiseNameResolver.resolveSystemMessageName(player);
        if (disguiseName != null) {
            cir.setReturnValue(disguiseName);
        }
    }
}
