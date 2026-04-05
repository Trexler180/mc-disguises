package com.coffee.disguises.watcher;

/**
 * Mooshroom (MushroomCow)-specific metadata: cow variant (red/brown).
 *
 * Vanilla MushroomCow DataTracker (Mojang 1.21.x):
 *   (extends Cow extends Animal extends AgeableMob)
 *   index 15 - Mob flags
 *   index 16 - baby (AgeableMob)
 *   index 17 - cow variant (Holder<CowVariant>) — controls red vs brown mushroom appearance
 *
 * In 1.21.x the type is a registry-backed Holder<CowVariant>.
 * CowVariant registry keys: minecraft:temperate (brown), minecraft:warm (red), minecraft:cold (black)
 * For disguise purposes we store an int index:
 *   0 = temperate (brown mooshroom)
 *   1 = warm      (red mooshroom)
 *   2 = cold      (black mooshroom)
 */
public class MooshroomWatcher extends AgeableWatcher {

    public static final int BROWN = 0;  // temperate
    public static final int RED   = 1;  // warm

    private int variant = RED;  // default red like vanilla

    public MooshroomWatcher setVariant(int variant) { this.variant = Math.max(0, Math.min(1, variant)); return this; }
    public MooshroomWatcher setRed()                { this.variant = RED;   return this; }
    public MooshroomWatcher setBrown()              { this.variant = BROWN; return this; }

    public int getVariant()  { return variant; }
    public boolean isRed()   { return variant == RED; }
    public boolean isBrown() { return variant == BROWN; }
}
