package com.coffee.disguises.watcher;

/**
 * Strider-specific metadata: shaking (wet), saddled.
 *
 * Vanilla Strider DataTracker (Mojang 1.21.x):
 *   (extends Animal extends AgeableMob)
 *   index 15 - Mob flags
 *   index 16 - baby (AgeableMob)
 *   index 17 - boost time remaining (int, used for boost animation)
 *   index 18 - shaking (boolean, true when cold/wet — shivering animation)
 *   index 19 - has saddle (boolean)
 */
public class StriderWatcher extends AgeableWatcher {

    private boolean shaking = false;
    private boolean saddled = false;

    public StriderWatcher setShaking(boolean shaking) { this.shaking = shaking; return this; }
    public StriderWatcher setSaddled(boolean saddled) { this.saddled = saddled; return this; }

    public boolean isShaking() { return shaking; }
    public boolean isSaddled() { return saddled; }
}
