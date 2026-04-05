package com.coffee.disguises.watcher;

/**
 * Parrot-specific metadata: color variant.
 *
 * Vanilla Parrot DataTracker (Mojang 1.21.x):
 *   (extends TamableAnimal extends Animal extends AgeableMob)
 *   index 15 - Mob flags
 *   index 16 - baby (AgeableMob)
 *   index 17 - tame flags byte (TamableAnimal: bit 0=sitting, bit 2=tamed)
 *   index 18 - owner UUID (TamableAnimal, Optional<UUID>)
 *   index 19 - variant (int, 0=red, 1=blue, 2=green, 3=cyan, 4=gray)
 */
public class ParrotWatcher extends AgeableWatcher {

    public static final int RED  = 0;
    public static final int BLUE = 1;
    public static final int GREEN = 2;
    public static final int CYAN = 3;
    public static final int GRAY = 4;

    private int variant = RED;

    public ParrotWatcher setVariant(int variant) {
        this.variant = Math.max(0, Math.min(4, variant));
        return this;
    }

    public int getVariant() { return variant; }
}
