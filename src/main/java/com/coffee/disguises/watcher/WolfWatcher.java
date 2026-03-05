package com.coffee.disguises.watcher;

import net.minecraft.world.item.DyeColor;

/**
 * Wolf-specific metadata.
 *
 * Vanilla Wolf DataTracker (Mojang 1.21.x):
 *   (extends TamableAnimal which extends Animal which extends AgeableMob)
 *   index 17 - tameableFlags byte (bit 0 = sitting, bit 2 = tamed)
 *   index 18 - ownerUUID (optional UUID)
 *   index 19 - collarColor (int = DyeColor ordinal) — only visible when tamed
 *   index 20 - remainingAngerTime (int) — non-zero = angry
 *   index 21 - variant (wolf variant ResourceLocation)
 */
public class WolfWatcher extends AgeableWatcher {

    private boolean tamed = false;
    private boolean sitting = false;
    private boolean angry = false;
    private DyeColor collarColor = DyeColor.RED;

    public WolfWatcher setTamed(boolean tamed) { this.tamed = tamed; return this; }
    public WolfWatcher setSitting(boolean sitting) { this.sitting = sitting; return this; }
    public WolfWatcher setAngry(boolean angry) { this.angry = angry; return this; }
    public WolfWatcher setCollarColor(DyeColor color) { this.collarColor = color; return this; }

    public boolean isTamed() { return tamed; }
    public boolean isSitting() { return sitting; }
    public boolean isAngry() { return angry; }
    public DyeColor getCollarColor() { return collarColor; }

    /** Build the tameable-flags byte for index 17. */
    public byte getTameableFlags() {
        byte b = 0;
        if (sitting) b |= 0x01;
        if (tamed)   b |= 0x04;
        return b;
    }
}
