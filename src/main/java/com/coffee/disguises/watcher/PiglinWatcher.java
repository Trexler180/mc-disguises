package com.coffee.disguises.watcher;

/**
 * Piglin-specific metadata: baby state, crossbow charging, dancing, celebrating.
 *
 * Vanilla Piglin DataTracker (Mojang 1.21.x):
 *   (extends AbstractPiglin extends Monster extends Mob — Piglin adds its own baby field)
 *   index 15 - Mob flags
 *   index 16 - baby piglin (boolean, Piglin's own field)
 *   index 17 - is charging crossbow (boolean)
 *   index 18 - is dancing (boolean)
 *   index 19 - is celebrating (boolean)
 *
 * Note: Piglin does not extend AgeableMob; the baby flag here is Piglin's own
 * DATA_BABY_PIGLIN field. The DisguiseType entry uses AgeableWatcher which sends
 * AgeableMob.DATA_BABY_ID — that field won't exist on Piglin's tracker, so the
 * baby display from AgeableWatcher will have no effect. This class sends the
 * correct Piglin-specific baby field instead.
 */
public class PiglinWatcher extends LivingEntityWatcher {

    private boolean babyPiglin        = false;
    private boolean chargingCrossbow  = false;
    private boolean dancing           = false;
    private boolean celebrating       = false;

    public PiglinWatcher setBaby(boolean baby)                     { this.babyPiglin       = baby;       return this; }
    public PiglinWatcher setChargingCrossbow(boolean v)            { this.chargingCrossbow = v;          return this; }
    public PiglinWatcher setDancing(boolean dancing)               { this.dancing          = dancing;    return this; }
    public PiglinWatcher setCelebrating(boolean celebrating)       { this.celebrating      = celebrating; return this; }

    public boolean isBabyPiglin()       { return babyPiglin; }
    public boolean isChargingCrossbow() { return chargingCrossbow; }
    public boolean isDancing()          { return dancing; }
    public boolean isCelebrating()      { return celebrating; }
}
