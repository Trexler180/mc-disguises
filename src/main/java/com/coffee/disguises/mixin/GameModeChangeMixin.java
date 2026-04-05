package com.coffee.disguises.mixin;

import com.coffee.disguises.core.DisguiseManager;
import com.coffee.disguises.disguise.Disguise;
import com.coffee.disguises.packet.PacketInterceptor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Handles disguise self-view puppet lifecycle across game-mode changes.
 *
 * When a disguised player enters spectator mode:
 *   - The self-view puppet is removed.  Spectators are invisible to non-spectators,
 *     and a floating puppet at the player's position is wrong.
 *
 * When a disguised player leaves spectator mode:
 *   - The self-view puppet is re-created if the player's preference is still on.
 *     (The puppet was cleaned up on spectator entry, so applySelfView starts fresh.)
 */
@Mixin(ServerPlayer.class)
public abstract class GameModeChangeMixin {

    @Inject(method = "setGameMode", at = @At("TAIL"))
    private void disguises$onGameModeChange(GameType gameMode, CallbackInfoReturnable<Boolean> cir) {
        // Only act if the game mode actually changed (return value = true means it changed)
        if (!Boolean.TRUE.equals(cir.getReturnValue())) return;

        ServerPlayer player = (ServerPlayer) (Object) this;
        Disguise disguise = DisguiseManager.INSTANCE.getDisguise(player);
        if (disguise == null) return;

        if (gameMode == GameType.SPECTATOR) {
            // Entering spectator — remove the self-view puppet without touching the disguise.
            // The disguise stays active so others still see it until it times out naturally.
            if (disguise.isSelfDisguise()) {
                PacketInterceptor.removeSelfView(player);
            }
        } else {
            // Leaving spectator (or switching between other modes) — re-apply self-view
            // if the disguise still has it enabled.  applySelfView is idempotent:
            // it calls removeSelfViewPuppetIfPresent first, so duplicate calls are safe.
            if (disguise.isSelfDisguise()) {
                PacketInterceptor.applySelfView(player, disguise);
            }
        }
    }
}
