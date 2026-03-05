package com.coffee.disguises.watcher;

/**
 * Enderman-specific metadata.
 *
 * Vanilla Enderman DataTracker (Mojang 1.21.x):
 *   index 16 - carriedBlockState (optional BlockState) — held block above head
 *   index 17 - isCreepy / screaming (boolean)
 *   index 18 - hasBeenStaredAt (boolean)
 */
public class EndermanWatcher extends LivingEntityWatcher {

    private boolean screaming = false;

    public EndermanWatcher setScreaming(boolean screaming) { this.screaming = screaming; return this; }
    public boolean isScreaming() { return screaming; }
}
