package com.coffee.disguises.watcher;

/**
 * Fox-specific metadata: type (red/snow) and behavioral states.
 *
 * Vanilla Fox DataTracker (Mojang 1.21.x):
 *   (extends Animal extends AgeableMob)
 *   index 15 - Mob flags
 *   index 16 - baby (AgeableMob)
 *   index 17 - type (int, 0=red, 1=snow)
 *   index 18 - fox flags byte:
 *                bit 0 (0x01) = sitting
 *                bit 2 (0x04) = crouching
 *                bit 3 (0x08) = interested (head tilt)
 *                bit 4 (0x10) = pouncing
 *                bit 5 (0x20) = sleeping
 *                bit 6 (0x40) = faceplanted (after failed pounce)
 *                bit 7 (0x80) = defending
 */
public class FoxWatcher extends AgeableWatcher {

    public static final int RED  = 0;
    public static final int SNOW = 1;

    private int     type       = RED;
    private boolean sitting    = false;
    private boolean crouching  = false;
    private boolean interested = false;  // head tilt / curious look
    private boolean sleeping   = false;

    public FoxWatcher setType(int type)            { this.type       = (type == SNOW) ? SNOW : RED; return this; }
    public FoxWatcher setSitting(boolean sitting)  { this.sitting    = sitting;    return this; }
    public FoxWatcher setCrouching(boolean v)      { this.crouching  = v;          return this; }
    public FoxWatcher setInterested(boolean v)     { this.interested = v;          return this; }
    public FoxWatcher setSleeping(boolean sleeping){ this.sleeping   = sleeping;   return this; }

    public int     getType()        { return type; }
    public boolean isSitting()      { return sitting; }
    public boolean isCrouching()    { return crouching; }
    public boolean isInterested()   { return interested; }
    public boolean isSleeping()     { return sleeping; }

    /** Packs behavioral booleans into the vanilla fox flags byte. */
    public byte getFoxFlags() {
        byte b = 0;
        if (sitting)    b |= 0x01;
        if (crouching)  b |= 0x04;
        if (interested) b |= 0x08;
        if (sleeping)   b |= 0x20;
        return b;
    }
}
