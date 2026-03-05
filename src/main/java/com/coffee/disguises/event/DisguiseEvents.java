package com.coffee.disguises.event;

import com.coffee.disguises.disguise.Disguise;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;

/**
 * Public Fabric events fired by the disguises mod.
 * Other mods can subscribe to these to react to or modify disguise behavior.
 */
public final class DisguiseEvents {

    /**
     * Fired before a disguise is applied to an entity.
     * Return false from any listener to cancel the disguise.
     */
    public static final Event<BeforeDisguise> BEFORE_DISGUISE =
            EventFactory.createArrayBacked(BeforeDisguise.class, listeners -> (entity, disguise) -> {
                for (BeforeDisguise listener : listeners) {
                    if (!listener.onBeforeDisguise(entity, disguise)) return false;
                }
                return true;
            });

    /**
     * Fired after a disguise has been successfully applied.
     */
    public static final Event<AfterDisguise> AFTER_DISGUISE =
            EventFactory.createArrayBacked(AfterDisguise.class, listeners -> (entity, disguise) -> {
                for (AfterDisguise listener : listeners) {
                    listener.onAfterDisguise(entity, disguise);
                }
            });

    /**
     * Fired before a disguise is removed from an entity.
     * Return false from any listener to cancel the removal.
     */
    public static final Event<BeforeUndisguise> BEFORE_UNDISGUISE =
            EventFactory.createArrayBacked(BeforeUndisguise.class, listeners -> (entity, disguise) -> {
                for (BeforeUndisguise listener : listeners) {
                    if (!listener.onBeforeUndisguise(entity, disguise)) return false;
                }
                return true;
            });

    /**
     * Fired after a disguise has been removed.
     */
    public static final Event<AfterUndisguise> AFTER_UNDISGUISE =
            EventFactory.createArrayBacked(AfterUndisguise.class, listeners -> entity -> {
                for (AfterUndisguise listener : listeners) {
                    listener.onAfterUndisguise(entity);
                }
            });

    /**
     * Fired before a disguised spawn packet sequence is sent to a specific observer.
     * Return false to suppress the send for this specific observer (e.g. vanish integration).
     */
    public static final Event<BeforeSendSpawn> BEFORE_SEND_SPAWN =
            EventFactory.createArrayBacked(BeforeSendSpawn.class, listeners -> (observer, entity, disguise) -> {
                for (BeforeSendSpawn listener : listeners) {
                    if (!listener.onBeforeSendSpawn(observer, entity, disguise)) return false;
                }
                return true;
            });

    // -----------------------------------------------------------------------
    // Functional interfaces
    // -----------------------------------------------------------------------

    @FunctionalInterface
    public interface BeforeDisguise {
        /** @return true to allow, false to cancel. */
        boolean onBeforeDisguise(Entity entity, Disguise disguise);
    }

    @FunctionalInterface
    public interface AfterDisguise {
        void onAfterDisguise(Entity entity, Disguise disguise);
    }

    @FunctionalInterface
    public interface BeforeUndisguise {
        /** @return true to allow, false to cancel. */
        boolean onBeforeUndisguise(Entity entity, Disguise currentDisguise);
    }

    @FunctionalInterface
    public interface AfterUndisguise {
        void onAfterUndisguise(Entity entity);
    }

    @FunctionalInterface
    public interface BeforeSendSpawn {
        /**
         * Called just before sending disguised spawn packets to an observer.
         * @return true to allow the send, false to suppress it for this observer.
         */
        boolean onBeforeSendSpawn(ServerPlayer observer, Entity entity, Disguise disguise);
    }

    private DisguiseEvents() {}
}
