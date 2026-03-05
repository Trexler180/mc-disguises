package com.coffee.disguises.compat;

import com.coffee.disguises.DisguisesMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates all registered VanishProvider implementations.
 *
 * A disguised entity is considered "vanished from an observer" if ANY registered
 * provider reports that the entity is vanished AND the observer cannot see vanished entities.
 *
 * Providers are loaded from:
 *   1. The "disguises:vanish" fabric.mod.json entrypoint (other mods declare themselves)
 *   2. Runtime registration via register()
 *   3. Built-in fallbacks (scoreboard tag, invisibility effect) if no providers loaded
 */
public class VanishCompat {

    private static final List<VanishProvider> PROVIDERS = new ArrayList<>();

    /**
     * Called during mod init. Reads all mods that declared a "disguises:vanish" entrypoint.
     */
    public static void init() {
        // Load declared providers from other mods
        FabricLoader.getInstance()
                .getEntrypointContainers("disguises:vanish", VanishProvider.class)
                .forEach(container -> {
                    try {
                        PROVIDERS.add(container.getEntrypoint());
                        DisguisesMod.LOGGER.info("Registered vanish provider from mod: {}",
                                container.getProvider().getMetadata().getId());
                    } catch (Exception e) {
                        DisguisesMod.LOGGER.warn("Failed to load vanish provider from mod {}: {}",
                                container.getProvider().getMetadata().getId(), e.getMessage());
                    }
                });

        // Built-in fallback: scoreboard tag "vanished" (common convention)
        PROVIDERS.add(new ScoreboardTagVanishProvider());

        DisguisesMod.LOGGER.debug("VanishCompat initialized with {} provider(s).", PROVIDERS.size());
    }

    /**
     * Register a VanishProvider at runtime (e.g. from DisguisesAPI).
     */
    public static void register(VanishProvider provider) {
        PROVIDERS.add(provider);
    }

    /**
     * Returns true if the entity is vanished and should NOT be shown to the given observer.
     *
     * Logic: entity is vanished (any provider says yes) AND observer cannot see vanished entities.
     */
    public static boolean isVanishedFrom(Entity entity, ServerPlayer observer) {
        if (!DisguisesMod.CONFIG.vanishedEntitiesHidden) return false;

        boolean entityVanished = false;
        for (VanishProvider provider : PROVIDERS) {
            if (provider.isVanished(entity)) {
                entityVanished = true;
                break;
            }
        }
        if (!entityVanished) return false;

        // Check if observer can see vanished entities
        for (VanishProvider provider : PROVIDERS) {
            if (provider.canSeeVanished(observer)) return false;
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // Built-in fallback providers
    // -----------------------------------------------------------------------

    /**
     * Checks for the scoreboard tag "vanished" on the entity.
     * Many vanish plugins/mods use this as a convention.
     * canSeeVanished checks for the "vanish.see" scoreboard tag on the observer.
     */
    private static class ScoreboardTagVanishProvider implements VanishProvider {
        @Override
        public boolean isVanished(Entity entity) {
            return entity.getTags().contains("vanished");
        }

        @Override
        public boolean canSeeVanished(ServerPlayer observer) {
            // Ops can always see vanished entities
            return me.lucko.fabric.api.permissions.v0.Permissions.check(observer.createCommandSourceStack(), "disguises.vanish.see", 2) || observer.getTags().contains("vanish.see");
        }
    }
}
