package com.coffee.disguises.watcher;

/**
 * Frog-specific metadata: variant.
 *
 * Vanilla Frog DataTracker (Mojang 1.21.x):
 *   (extends Animal extends AgeableMob)
 *   index 15 - Mob flags
 *   index 16 - baby (AgeableMob)
 *   index 17 - variant (Holder<FrogVariant>)
 *   index 18 - tongue target (OptionalInt, entity ID)
 *
 * Frog variants: 0=temperate, 1=warm, 2=cold
 */
public class FrogWatcher extends AgeableWatcher {

    public static final int TEMPERATE = 0;
    public static final int WARM      = 1;
    public static final int COLD      = 2;

    private int variant = TEMPERATE;

    public FrogWatcher setVariant(int variant) { this.variant = Math.max(0, Math.min(2, variant)); return this; }
    public int getVariant()                    { return variant; }
}
