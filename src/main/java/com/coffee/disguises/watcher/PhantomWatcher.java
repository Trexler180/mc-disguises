package com.coffee.disguises.watcher;

/**
 * Phantom-specific metadata: size.
 *
 * Vanilla Phantom DataTracker (Mojang 1.21.x):
 *   (extends FlyingMob extends Mob — no AgeableMob layer)
 *   index 15 - Mob flags (noAI)
 *   index 16 - size (int, 0=small/default, larger = bigger phantom)
 */
public class PhantomWatcher extends LivingEntityWatcher {

    /** Vanilla's own bound (Phantom.MAX_SIZE). */
    public static final int MAX_SIZE = 64;

    /** 0 = default size; higher values scale the phantom up. */
    private int size = 0;

    /** Clamped to vanilla's 0–64, for the same client-freeze reason as SlimeWatcher. */
    public PhantomWatcher setSize(int size) { this.size = Math.clamp(size, 0, MAX_SIZE); return this; }
    /** Clamped here too: persistence restores the field by reflection, bypassing the setter. */
    public int getSize() { return Math.clamp(size, 0, MAX_SIZE); }
}
