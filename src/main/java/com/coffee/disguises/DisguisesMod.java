package com.coffee.disguises;

import com.coffee.disguises.command.DisguiseCommand;
import com.coffee.disguises.command.DisguiseHelpCommand;
import com.coffee.disguises.command.DisguisesAdminCommand;
import com.coffee.disguises.command.SavedDisguiseCommand;
import com.coffee.disguises.command.UndisguiseCommand;
import com.coffee.disguises.compat.VanishCompat;
import com.coffee.disguises.core.DisguiseManager;
import com.coffee.disguises.packet.PacketInterceptor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class DisguisesMod implements ModInitializer {

    public static final String MOD_ID = "disguises";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static DisguisesConfig CONFIG;

    @Override
    public void onInitialize() {
        CONFIG = DisguisesConfig.load();

        VanishCompat.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            DisguiseCommand.register(dispatcher);
            UndisguiseCommand.register(dispatcher);
            DisguisesAdminCommand.register(dispatcher);
            DisguiseHelpCommand.register(dispatcher);
            SavedDisguiseCommand.register(dispatcher);
        });

        // ─── Per-tick work ────────────────────────────────────────────────────
        //
        // Disguise interception is now handled entirely by ServerEntityMixin which
        // targets ServerEntity.sendPairingData — the exact method called once when
        // a player first starts tracking an entity.
        //
        // This tick handler has two jobs:
        //   1. Flush the 1-tick-delayed equipment resend queue so that held items
        //      and armor appear correctly on disguised entities. The client can
        //      sometimes process AddEntity after SetEquipment due to packet batching;
        //      the deferred resend guarantees the equipment arrives when the entity
        //      is already registered on the client side.
        //
        //   2. Refresh the action bar for disguised players once per second so the
        //      disguise indicator stays visible above the hotbar.
        //
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            PacketInterceptor.flushPendingEquipment(server);
            PacketInterceptor.syncVanishedDisguisedPositions(server);
            PacketInterceptor.syncSelfViewPuppets(server);

            if (!CONFIG.showDisguiseActionBar) return;
            if (CONFIG.actionBarIntervalTicks <= 0) return;
            if (server.getTickCount() % CONFIG.actionBarIntervalTicks != 0) return;
            for (UUID uuid : DisguiseManager.INSTANCE.getAllDisguisedUUIDs()) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player != null) {
                    DisguiseManager.INSTANCE.sendDisguiseActionBar(player);
                }
            }
        });

        // ─── Auto-undisguise: death / world change ────────────────────────────
        // AFTER_RESPAWN fires for both death respawns and dimension transfers.
        //   alive=false → the player died (death respawn)
        //   alive=true  → the player changed dimension (teleport/portal)
        //
        // When the disguise is KEPT, the old ServerPlayer object is gone and the
        // self-view puppet (if any) was bound to its now-dead connection.  We must
        // transfer the puppet state to the new player object so the self-view works
        // again after the respawn / dimension change.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive && CONFIG.undisguiseOnDeath) {
                DisguiseManager.INSTANCE.removeDisguise(oldPlayer, true);
            } else if (alive && CONFIG.undisguiseOnWorldChange) {
                DisguiseManager.INSTANCE.removeDisguise(oldPlayer, true);
            } else {
                // Disguise is kept — re-anchor the self-view puppet to the new player object.
                com.coffee.disguises.disguise.Disguise kept =
                        DisguiseManager.INSTANCE.getDisguise(newPlayer);
                if (kept != null && kept.isSelfDisguise()) {
                    PacketInterceptor.transferSelfView(oldPlayer, newPlayer, kept);
                }
            }
        });

        // ─── Lifecycle ────────────────────────────────────────────────────────

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (CONFIG.persistDisguises) {
                DisguiseManager.INSTANCE.saveDisguise(handler.player);
            }
            DisguiseManager.INSTANCE.removeDisguise(handler.player, false);
            // Drop any per-observer state tied to the disconnecting player so it
            // doesn't leak (small map entries, queued packet sends targeted at
            // their now-dead connection, etc.).
            PacketInterceptor.cleanupForRemovedObserver(handler.player.getUUID());
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (CONFIG.persistDisguises) {
                DisguiseManager.INSTANCE.loadPersistedDisguises(server);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (CONFIG.persistDisguises) {
                DisguiseManager.INSTANCE.persistAll(server);
            }
        });

        LOGGER.info("Disguises mod initialized.");
    }
}
