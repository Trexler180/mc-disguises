package com.coffee.disguises.watcher;

/**
 * Slime and Magma Cube size metadata.
 *
 * Vanilla Slime DataTracker (Mojang 1.21.x):
 *   index 16 - size (int, 1=tiny, 2=small, 4=large/boss-like)
 */
public class SlimeWatcher extends LivingEntityWatcher {

    /** Vanilla's own bound (Slime.MAX_SIZE). */
    public static final int MAX_SIZE = 127;

    private int size = 1;

    /**
     * Size: 1 = tiny, 2 = small, 4 = large. Values > 4 are valid but unusual.
     * Clamped to vanilla's 1–127: the client applies whatever it is sent, and an
     * enormous slime's bounding box can freeze every observer's client.
     */
    public SlimeWatcher setSize(int size) {
        this.size = Math.clamp(size, 1, MAX_SIZE);
        return this;
    }

    /** Clamped here too: persistence restores the field by reflection, bypassing the setter. */
    public int getSize() { return Math.clamp(size, 1, MAX_SIZE); }
}
