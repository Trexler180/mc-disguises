package com.coffee.disguises.watcher;

/**
 * Goat-specific metadata: screaming variant, horns.
 *
 * Vanilla Goat DataTracker (Mojang 1.21.x):
 *   (extends Animal extends AgeableMob)
 *   index 15 - Mob flags
 *   index 16 - baby (AgeableMob)
 *   index 17 - isScreaming (boolean) — screaming goat variant
 *   index 18 - hasLeftHorn  (boolean)
 *   index 19 - hasRightHorn (boolean)
 */
public class GoatWatcher extends AgeableWatcher {

    private boolean screaming    = false;
    private boolean hasLeftHorn  = true;
    private boolean hasRightHorn = true;

    public GoatWatcher setScreaming(boolean screaming)        { this.screaming    = screaming;    return this; }
    public GoatWatcher setHasLeftHorn(boolean hasLeftHorn)    { this.hasLeftHorn  = hasLeftHorn;  return this; }
    public GoatWatcher setHasRightHorn(boolean hasRightHorn)  { this.hasRightHorn = hasRightHorn; return this; }

    public boolean isScreaming()    { return screaming; }
    public boolean hasLeftHorn()    { return hasLeftHorn; }
    public boolean hasRightHorn()   { return hasRightHorn; }
}
