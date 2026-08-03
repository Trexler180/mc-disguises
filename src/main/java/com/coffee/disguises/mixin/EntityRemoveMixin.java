package com.coffee.disguises.mixin;

import com.coffee.disguises.core.DisguiseManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops a disguise when its backing entity is actually destroyed.
 *
 * ─── Why this exists ──────────────────────────────────────────────────────────
 *
 * Disguises are stored in {@link DisguiseManager} keyed by entity UUID, but the
 * only lifecycle hooks that removed them were player-centric (death/world-change
 * respawn, disconnect).  A disguised NON-player entity — e.g. an armor stand the
 * player placed and then ran {@code /disguise entity ... player <skin>} on — had
 * no cleanup path when it was broken/killed.
 *
 * The map entry survived, and because a pending async skin fetch
 * (see SkinFetcher / DisguiseCommand) guards only on
 * {@code getDisguise(entity) == disguise}, a skin that finished loading AFTER the
 * entity was destroyed would pass the guard and re-spawn a fake-player packet at
 * the dead entity's last position — a disguise with no entity behind it.
 *
 * ─── What we hook ─────────────────────────────────────────────────────────────
 *
 * {@code Entity.setRemoved(RemovalReason)} fires for every removal, but we only
 * want to forget the disguise when the entity is genuinely gone, not when it is
 * merely unloaded with its chunk (it will re-track and re-apply the disguise from
 * the in-memory map on reload).  {@code RemovalReason.shouldDestroy()} is true
 * only for KILLED and DISCARDED, which is exactly the distinction we need.
 *
 * Players are intentionally excluded — their disguise lifecycle is handled by the
 * respawn/disconnect events in DisguisesMod, which respect the undisguise-on-death
 * / world-change config options.
 */
@Mixin(Entity.class)
public abstract class EntityRemoveMixin {

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void disguises$onSetRemoved(Entity.RemovalReason reason, CallbackInfo ci) {
        // Only act when the entity is truly being destroyed (KILLED / DISCARDED),
        // not on chunk/player unload or dimension change.
        if (reason == null || !reason.shouldDestroy()) return;

        Entity self = (Entity) (Object) this;

        // Server-side only; the manager and packet sends assume the server thread.
        if (!(self.level() instanceof ServerLevel)) return;

        // Players are handled by the respawn/disconnect lifecycle events.
        if (self instanceof ServerPlayer) return;

        // sendVanillaRespawn = false: the entity is going away, so there is nothing
        // to revert to.  Vanilla's own tracker removal packet clears the fake entity
        // (it reuses the real entity id), and lifecycle cleanup still clears injected
        // tab profiles, observer overrides, and nametag-hide teams across all observers.
        DisguiseManager.INSTANCE.removeDisguiseForLifecycle(self);
    }
}
