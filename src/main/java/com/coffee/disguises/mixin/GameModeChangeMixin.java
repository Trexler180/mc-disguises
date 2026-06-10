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
 * Handles disguise visibility across game-mode changes.
 *
 * When a disguised player enters spectator mode:
 *   - The self-view puppet is removed.  Spectators are invisible to non-spectators,
 *     and a floating puppet at the player's position is wrong.
 *   - Other observers get a remove + vanilla player respawn.  Clients hide spectators
 *     by checking the tab-list game mode of PLAYER entities — a mob or fake-profile
 *     disguise entity is never matched against that entry, so without this the
 *     disguise would keep standing there while the real player flies around unseen.
 *
 * When a disguised player leaves spectator mode:
 *   - The disguise is re-spawned for nearby observers, and the self-view puppet is
 *     re-created if the player's preference is still on.
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
            // Entering spectator — keep the disguise stored, but show observers the
            // vanilla player entity instead (which their clients render invisible
            // based on the tab-list game mode).
            PacketInterceptor.refreshForNearbyPlayers(player, null);
            if (disguise.isSelfDisguise()) {
                PacketInterceptor.removeSelfView(player);
            }
        } else {
            // Leaving spectator (or switching between other modes) — re-spawn the
            // disguise for observers and re-apply self-view if still enabled.
            // refreshForNearbyPlayers also re-applies the self-view puppet; both
            // paths are idempotent, so duplicate calls are safe.
            PacketInterceptor.refreshForNearbyPlayers(player, disguise);
        }
    }
}
