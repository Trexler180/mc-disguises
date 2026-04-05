package com.coffee.disguises.watcher;

/**
 * Axolotl-specific metadata: color variant.
 *
 * Vanilla Axolotl DataTracker (Mojang 1.21.x):
 *   (extends Animal extends AgeableMob)
 *   index 15 - Mob flags
 *   index 16 - baby (AgeableMob)
 *   index 17 - variant (int, 0-4)
 *   index 18 - playing dead (boolean)
 *
 * Variant values:
 *   0 = lucy  (pink / leucistic)
 *   1 = wild  (brown)
 *   2 = gold  (yellow)
 *   3 = cyan  (blue-green)
 *   4 = blue  (rare blue — glow-like)
 */
public class AxolotlWatcher extends AgeableWatcher {

    public static final int LUCY = 0;
    public static final int WILD = 1;
    public static final int GOLD = 2;
    public static final int CYAN = 3;
    public static final int BLUE = 4;

    private int variant     = LUCY;
    private boolean playingDead = false;

    public AxolotlWatcher setVariant(int variant) {
        this.variant = Math.max(0, Math.min(4, variant));
        return this;
    }
    public AxolotlWatcher setPlayingDead(boolean v) { this.playingDead = v; return this; }

    public int getVariant()       { return variant; }
    public boolean isPlayingDead(){ return playingDead; }
}
