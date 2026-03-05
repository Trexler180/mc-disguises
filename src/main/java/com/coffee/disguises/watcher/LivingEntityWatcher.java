package com.coffee.disguises.watcher;

/**
 * Adds LivingEntity-level metadata on top of the base Entity flags.
 *
 * Vanilla LivingEntity DataTracker indices (Mojang 1.21.x):
 *   index 8  - living entity flags byte (hand active, active hand, riptide spin)
 *   index 9  - health (float)
 *   index 10 - potion effect color (int, ARGB)
 *   index 11 - potion effect ambient (boolean)
 *   index 12 - arrows stuck count (int)
 *   index 13 - bee stingers stuck count (int)
 *   index 14 - sleeping position (optional BlockPos)
 *
 * NOTE: Index values are based on 1.21.x vanilla source. Verify if upgrading.
 */
public class LivingEntityWatcher extends FlagWatcher {

    protected float health = 1.0f;
    protected int potionEffectColor = 0;      // 0 = no effect overlay
    protected boolean potionEffectAmbient = false;
    protected int arrowCount = 0;
    protected int stingerCount = 0;

    // ---- Setters ----

    public LivingEntityWatcher setHealth(float health)                     { this.health = health; return this; }
    public LivingEntityWatcher setPotionEffectColor(int color)             { this.potionEffectColor = color; return this; }
    public LivingEntityWatcher setPotionEffectAmbient(boolean v)           { this.potionEffectAmbient = v; return this; }
    public LivingEntityWatcher setArrowCount(int count)                    { this.arrowCount = count; return this; }
    public LivingEntityWatcher setStingerCount(int count)                  { this.stingerCount = count; return this; }

    // ---- Getters ----

    public float getHealth()                 { return health; }
    public int getPotionEffectColor()        { return potionEffectColor; }
    public boolean isPotionEffectAmbient()   { return potionEffectAmbient; }
    public int getArrowCount()               { return arrowCount; }
    public int getStingerCount()             { return stingerCount; }
}
