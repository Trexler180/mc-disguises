package com.coffee.disguises.watcher;

/**
 * Horse-specific metadata: tame/flags, variant (color+markings).
 *
 * Vanilla Horse DataTracker (Mojang 1.21.x):
 *   (extends AbstractHorse extends Animal extends AgeableMob)
 *   index 15 - Mob flags
 *   index 16 - baby (AgeableMob)
 *   index 17 - AbstractHorse flags byte:
 *                bit 2 (0x02) = tame
 *                bit 3 (0x04) = eating
 *                bit 4 (0x08) = standing (rearing)
 *                bit 5 (0x10) = bred
 *                bit 6 (0x20) = mouth open / eating
 *   index 18 - Horse type variant (int, packed: low 8 bits = color 0-6, high 8 bits = markings 0-4)
 *
 * Horse colors: 0=white, 1=creamy, 2=chestnut, 3=brown, 4=black, 5=gray, 6=dark_brown
 * Horse markings: 0=none, 1=white, 2=white_field, 3=white_dots, 4=black_dots
 */
public class HorseWatcher extends AgeableWatcher {

    public static final int COLOR_WHITE      = 0;
    public static final int COLOR_CREAMY     = 1;
    public static final int COLOR_CHESTNUT   = 2;
    public static final int COLOR_BROWN      = 3;
    public static final int COLOR_BLACK      = 4;
    public static final int COLOR_GRAY       = 5;
    public static final int COLOR_DARK_BROWN = 6;

    public static final int MARKINGS_NONE        = 0;
    public static final int MARKINGS_WHITE        = 1;
    public static final int MARKINGS_WHITE_FIELD  = 2;
    public static final int MARKINGS_WHITE_DOTS   = 3;
    public static final int MARKINGS_BLACK_DOTS   = 4;

    private boolean tame    = false;
    private boolean eating  = false;
    private boolean rearing = false;
    private int color       = COLOR_BROWN;
    private int markings    = MARKINGS_NONE;

    public HorseWatcher setTame(boolean tame)       { this.tame    = tame;    return this; }
    public HorseWatcher setEating(boolean eating)   { this.eating  = eating;  return this; }
    public HorseWatcher setRearing(boolean rearing) { this.rearing = rearing; return this; }
    public HorseWatcher setColor(int color)         { this.color   = Math.max(0, Math.min(6, color));    return this; }
    public HorseWatcher setMarkings(int markings)   { this.markings = Math.max(0, Math.min(4, markings)); return this; }

    public boolean isTame()    { return tame; }
    public boolean isEating()  { return eating; }
    public boolean isRearing() { return rearing; }
    public int getColor()      { return color; }
    public int getMarkings()   { return markings; }

    /** Packs tame/eating/rearing into the vanilla AbstractHorse flags byte. */
    public byte getHorseFlags() {
        byte b = 0;
        if (tame)    b |= 0x02;
        if (eating)  b |= 0x04;
        if (rearing) b |= 0x08;
        return b;
    }

    /** Returns the packed variant int: color | (markings << 8). */
    public int getVariant() {
        return color | (markings << 8);
    }
}
