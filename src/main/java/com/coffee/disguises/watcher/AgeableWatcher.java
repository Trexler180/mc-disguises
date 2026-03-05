package com.coffee.disguises.watcher;

/**
 * Adds the baby flag for all ageable mobs (Cow, Sheep, Pig, Horse, etc.).
 *
 * Vanilla AgeableMob DataTracker indices (Mojang 1.21.x):
 *   index 16 - isBaby (boolean)
 *   (indices 15 is Mob.DATA_MOB_FLAGS_ID - noAI flag)
 *
 * NOTE: Exact index depends on full hierarchy. MetadataBuilder resolves
 * the actual EntityDataAccessor for each concrete type.
 */
public class AgeableWatcher extends LivingEntityWatcher {

    protected boolean baby = false;

    public AgeableWatcher setBaby(boolean baby) { this.baby = baby; return this; }
    public boolean isBaby() { return baby; }
}
