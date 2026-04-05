package com.coffee.disguises.watcher;

import net.minecraft.world.item.DyeColor;

/**
 * TropicalFish-specific metadata: body color, pattern color, pattern type.
 *
 * Vanilla TropicalFish DataTracker (Mojang 1.21.x):
 *   (extends AbstractSchoolingFish extends AbstractFish extends WaterAnimal extends PathfinderMob extends Mob)
 *   index 15 - Mob flags
 *   index 16 - type variant (int, packed):
 *                bits  0- 7 = base/pattern shape (0–5: flopper, stripey, glitter, blockfish, betty, clayfish)
 *                              values 6-11 give the "large" variants of each shape
 *                bits  8-15 = body base color  (DyeColor.getId(), 0-15)
 *                bits 16-23 = pattern color     (DyeColor.getId(), 0-15)
 *
 * Shape values 0-5 are the small variants; 6-11 add 6 to get the large variant
 * of the same pattern.  Most common tropical fish use value 0 (flopper/kob shape).
 */
public class TropicalFishWatcher extends LivingEntityWatcher {

    /** Pattern shape index (0-5 = small, 6-11 = large). */
    public static final int FLOPPER   = 0;
    public static final int STRIPEY   = 1;
    public static final int GLITTER   = 2;
    public static final int BLOCKFISH = 3;
    public static final int BETTY     = 4;
    public static final int CLAYFISH  = 5;

    private int patternShape = FLOPPER;
    private boolean largeVariant = false;
    private DyeColor bodyColor    = DyeColor.WHITE;
    private DyeColor patternColor = DyeColor.ORANGE;

    public TropicalFishWatcher setPatternShape(int shape) {
        this.patternShape = Math.max(0, Math.min(5, shape));
        return this;
    }
    public TropicalFishWatcher setLarge(boolean large)           { this.largeVariant = large;        return this; }
    public TropicalFishWatcher setBodyColor(DyeColor color)      { this.bodyColor    = color;        return this; }
    public TropicalFishWatcher setPatternColor(DyeColor color)   { this.patternColor = color;        return this; }

    public int getPatternShape()      { return patternShape; }
    public boolean isLargeVariant()   { return largeVariant; }
    public DyeColor getBodyColor()    { return bodyColor; }
    public DyeColor getPatternColor() { return patternColor; }

    /**
     * Returns the packed integer suitable for the DATA_ID_TYPE_VARIANT accessor.
     * Format: patternShape | (bodyColor.getId() << 8) | (patternColor.getId() << 16)
     * Large variants use patternShape + 6.
     */
    public int getPackedData() {
        int shape = largeVariant ? patternShape + 6 : patternShape;
        return shape
                | (bodyColor.getId()    << 8)
                | (patternColor.getId() << 16);
    }
}
