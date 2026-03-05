package com.coffee.disguises.watcher;

/**
 * Creeper-specific metadata.
 *
 * Vanilla Creeper DataTracker (Mojang 1.21.x):
 *   index 16 - swell direction (int: -1=idle, 1=swelling)
 *   index 17 - isPowered (boolean)
 *   index 18 - isIgnited (boolean)
 */
public class CreeperWatcher extends LivingEntityWatcher {

    private boolean powered = false;
    private boolean ignited = false;

    public CreeperWatcher setPowered(boolean powered) { this.powered = powered; return this; }
    public CreeperWatcher setIgnited(boolean ignited) { this.ignited = ignited; return this; }

    public boolean isPowered() { return powered; }
    public boolean isIgnited() { return ignited; }
}
