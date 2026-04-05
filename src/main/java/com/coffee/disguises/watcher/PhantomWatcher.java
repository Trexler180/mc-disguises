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

    /** 0 = default size; higher values scale the phantom up. */
    private int size = 0;

    public PhantomWatcher setSize(int size) { this.size = Math.max(0, size); return this; }
    public int getSize() { return size; }
}
