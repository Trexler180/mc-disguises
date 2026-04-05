package com.coffee.disguises.watcher;

/**
 * Bee-specific metadata: anger, sting state, nectar.
 *
 * Vanilla Bee DataTracker (Mojang 1.21.x):
 *   (extends Animal extends AgeableMob)
 *   index 15 - Mob flags
 *   index 16 - baby (AgeableMob)
 *   index 17 - remaining anger time (int, 0 = not angry)
 *   index 18 - bee flags byte:
 *                bit 1 (0x02) = has stung
 *                bit 2 (0x04) = has nectar / is pollinating
 */
public class BeeWatcher extends AgeableWatcher {

    private int remainingAngerTime = 0;
    private boolean hasStung  = false;
    private boolean hasNectar = false;

    public BeeWatcher setRemainingAngerTime(int t) { this.remainingAngerTime = Math.max(0, t); return this; }
    public BeeWatcher setAngry(boolean angry)      { this.remainingAngerTime = angry ? 400 : 0; return this; }
    public BeeWatcher setHasStung(boolean v)       { this.hasStung  = v; return this; }
    public BeeWatcher setHasNectar(boolean v)      { this.hasNectar = v; return this; }

    public int getRemainingAngerTime() { return remainingAngerTime; }
    public boolean isHasStung()        { return hasStung; }
    public boolean isHasNectar()       { return hasNectar; }

    /** Packs hasStung + hasNectar into the vanilla flags byte. */
    public byte getBeeFlags() {
        byte b = 0;
        if (hasStung)  b |= 0x02;
        if (hasNectar) b |= 0x04;
        return b;
    }
}
