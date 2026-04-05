package com.coffee.disguises.watcher;

import net.minecraft.world.item.DyeColor;

/**
 * Cat-specific metadata: variant, lying, relaxed, collar color, tamed.
 *
 * Vanilla Cat DataTracker (Mojang 1.21.x):
 *   (extends TamableAnimal extends Animal extends AgeableMob)
 *   index 15 - Mob flags
 *   index 16 - baby (AgeableMob)
 *   index 17 - TamableAnimal flags (0x04=tame, 0x01=sitting)
 *   index 18 - TamableAnimal ownerUUID (Optional<UUID>)
 *   index 19 - Cat variant (Holder<CatVariant>)
 *   index 20 - isLying (bool)
 *   index 21 - relaxStateOne / head tilt (bool)
 *   index 22 - collar color (int, DyeColor ID)
 *
 * Cat variants: 0=tabby, 1=black, 2=red, 3=siamese, 4=british_shorthair,
 *               5=calico, 6=persian, 7=ragdoll, 8=white, 9=jellie, 10=all_black
 */
public class CatWatcher extends AgeableWatcher {

    public static final int TABBY             = 0;
    public static final int BLACK             = 1;
    public static final int RED               = 2;
    public static final int SIAMESE           = 3;
    public static final int BRITISH_SHORTHAIR = 4;
    public static final int CALICO            = 5;
    public static final int PERSIAN           = 6;
    public static final int RAGDOLL           = 7;
    public static final int WHITE             = 8;
    public static final int JELLIE            = 9;
    public static final int ALL_BLACK         = 10;

    private int      variant     = TABBY;
    private boolean  tame        = false;
    private boolean  sitting     = false;
    private boolean  lying       = false;
    private boolean  relaxed     = false;
    private DyeColor collarColor = DyeColor.RED;

    public CatWatcher setVariant(int variant)           { this.variant     = Math.max(0, Math.min(10, variant)); return this; }
    public CatWatcher setTame(boolean tame)             { this.tame        = tame;        return this; }
    public CatWatcher setSitting(boolean sitting)       { this.sitting     = sitting;     return this; }
    public CatWatcher setLying(boolean lying)           { this.lying       = lying;       return this; }
    public CatWatcher setRelaxed(boolean relaxed)       { this.relaxed     = relaxed;     return this; }
    public CatWatcher setCollarColor(DyeColor color)    { this.collarColor = color;       return this; }

    public int getVariant()          { return variant; }
    public boolean isTame()          { return tame; }
    public boolean isSitting()       { return sitting; }
    public boolean isLying()         { return lying; }
    public boolean isRelaxed()       { return relaxed; }
    public DyeColor getCollarColor() { return collarColor; }

    /** Packs tame+sitting into the TamableAnimal flags byte. */
    public byte getTameableFlags() {
        byte b = 0;
        if (sitting) b |= 0x01;
        if (tame)    b |= 0x04;
        return b;
    }
}
