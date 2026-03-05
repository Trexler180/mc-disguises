package com.coffee.disguises.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Interface that vanish mods can implement to integrate with Disguises.
 *
 * Register via fabric.mod.json entrypoint "disguises:vanish", e.g.:
 *   "entrypoints": { "disguises:vanish": ["com.example.MyVanishProvider"] }
 *
 * Or call DisguisesAPI.registerVanishProvider() at runtime.
 */
public interface VanishProvider {

    /**
     * Return true if the given entity (usually a player) is vanished
     * and should be hidden from most observers.
     */
    boolean isVanished(Entity entity);

    /**
     * Return true if the given observer is allowed to see vanished entities.
     * (e.g. ops, other vanished players, etc.)
     */
    boolean canSeeVanished(ServerPlayer observer);
}
