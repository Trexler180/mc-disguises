package com.coffee.disguises.api;

import com.coffee.disguises.compat.VanishCompat;
import com.coffee.disguises.compat.VanishProvider;
import com.coffee.disguises.core.DisguiseManager;
import com.coffee.disguises.disguise.Disguise;
import com.coffee.disguises.disguise.DisguiseType;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * Public API for interacting with the Disguises mod from other mods.
 *
 * All methods must be called on the server thread.
 */
public final class DisguisesAPI {

    private DisguisesAPI() {}

    // -----------------------------------------------------------------------
    // Disguise management
    // -----------------------------------------------------------------------

    /**
     * Apply a disguise to an entity.
     * Fires BEFORE_DISGUISE — may be cancelled.
     *
     * @return true if the disguise was applied, false if cancelled.
     */
    public static boolean disguise(Entity entity, Disguise disguise) {
        return DisguiseManager.INSTANCE.applyDisguise(entity, disguise);
    }

    /**
     * Remove the active disguise from an entity.
     * Fires BEFORE_UNDISGUISE — may be cancelled.
     *
     * @return true if a disguise was removed, false if none was active or removal was cancelled.
     */
    public static boolean undisguise(Entity entity) {
        return DisguiseManager.INSTANCE.removeDisguise(entity, true);
    }

    /**
     * Returns the active disguise for the entity, or empty if none.
     */
    public static Optional<Disguise> getDisguise(Entity entity) {
        return Optional.ofNullable(DisguiseManager.INSTANCE.getDisguise(entity));
    }

    /**
     * Returns true if the entity currently has an active disguise.
     */
    public static boolean isDisguised(Entity entity) {
        return DisguiseManager.INSTANCE.isDisguised(entity);
    }

    // -----------------------------------------------------------------------
    // Disguise builder
    // -----------------------------------------------------------------------

    /**
     * Create a new Disguise builder for the given type.
     * Example usage:
     *   Disguise d = DisguisesAPI.newDisguise(DisguiseType.SHEEP)
     *       .watcher(new SheepWatcher().setColor(DyeColor.RED).setBaby(true))
     *       .build();
     *   DisguisesAPI.disguise(player, d);
     */
    public static Disguise.Builder newDisguise(DisguiseType type) {
        return Disguise.builder(type);
    }

    // -----------------------------------------------------------------------
    // Vanish integration
    // -----------------------------------------------------------------------

    /**
     * Register a custom VanishProvider.
     * Can also be registered via the "disguises:vanish" fabric.mod.json entrypoint.
     */
    public static void registerVanishProvider(VanishProvider provider) {
        VanishCompat.register(provider);
    }
}
