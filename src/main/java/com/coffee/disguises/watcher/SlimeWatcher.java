package com.coffee.disguises.watcher;

/**
 * Slime and Magma Cube size metadata.
 *
 * Vanilla Slime DataTracker (Mojang 1.21.x):
 *   index 16 - size (int, 1=tiny, 2=small, 4=large/boss-like)
 */
public class SlimeWatcher extends LivingEntityWatcher {

    private int size = 1;

    /** Size: 1 = tiny, 2 = small, 4 = large. Values > 4 are valid but unusual. */
    public SlimeWatcher setSize(int size) {
        this.size = Math.max(1, size);
        return this;
    }

    public int getSize() { return size; }
}
