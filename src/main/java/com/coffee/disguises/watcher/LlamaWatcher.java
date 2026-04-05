package com.coffee.disguises.watcher;

/**
 * Llama/TraderLlama-specific metadata: strength, carpet color, variant.
 *
 * Vanilla Llama DataTracker (Mojang 1.21.x):
 *   (extends AbstractChestedHorse extends AbstractHorse extends Animal extends AgeableMob)
 *   index 15 - Mob flags
 *   index 16 - baby (AgeableMob)
 *   index 17 - AbstractHorse flags (tame/eating/rearing byte)
 *   index 18 - AbstractChestedHorse: has chest (bool)
 *   index 19 - strength (int, 1-5)
 *   index 20 - carpet color (int, DyeColor ordinal; -1 = no carpet)
 *   index 21 - variant (int: 0=creamy, 1=white, 2=brown, 3=gray)
 */
public class LlamaWatcher extends AgeableWatcher {

    public static final int VARIANT_CREAMY = 0;
    public static final int VARIANT_WHITE  = 1;
    public static final int VARIANT_BROWN  = 2;
    public static final int VARIANT_GRAY   = 3;

    private boolean hasChest   = false;
    private int     strength   = 3;
    private int     carpetColor = -1;  // -1 = no carpet
    private int     variant    = VARIANT_CREAMY;

    public LlamaWatcher setHasChest(boolean hasChest)      { this.hasChest    = hasChest;                        return this; }
    public LlamaWatcher setStrength(int strength)          { this.strength    = Math.max(1, Math.min(5, strength)); return this; }
    public LlamaWatcher setCarpetColor(int dyeColorId)     { this.carpetColor = dyeColorId;                      return this; }
    public LlamaWatcher clearCarpet()                      { this.carpetColor = -1;                              return this; }
    public LlamaWatcher setVariant(int variant)            { this.variant     = Math.max(0, Math.min(3, variant)); return this; }

    public boolean hasChest()    { return hasChest; }
    public int getStrength()     { return strength; }
    public int getCarpetColor()  { return carpetColor; }
    public int getVariant()      { return variant; }
}
