package com.coffee.disguises.watcher;

import net.minecraft.world.item.DyeColor;

/**
 * Sheep-specific metadata: wool color and sheared state.
 *
 * Vanilla Sheep DataTracker (Mojang 1.21.x):
 *   index 17 - color + sheared packed byte
 *              bits 0-3 = DyeColor ordinal
 *              bit 4    = isSheared
 */
public class SheepWatcher extends AgeableWatcher {

    private DyeColor color = DyeColor.WHITE;
    private boolean sheared = false;

    public SheepWatcher setColor(DyeColor color) { this.color = color; return this; }
    public SheepWatcher setSheared(boolean sheared) { this.sheared = sheared; return this; }

    public DyeColor getColor() { return color; }
    public boolean isSheared() { return sheared; }

    /** Pack color + sheared into the vanilla byte format. */
    public byte getSheepFlags() {
        byte b = (byte) (color.getId() & 0x0F);
        if (sheared) b |= 0x10;
        return b;
    }
}
