package com.coffee.disguises.watcher;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

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
 *   index 20 - variant (int: 0=creamy, 1=white, 2=brown, 3=gray)
 *
 * Since 1.21.5 the carpet is not metadata but an item in the BODY equipment slot,
 * so {@link #setCarpetColor} maintains that slot in the custom equipment.
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
    public LlamaWatcher clearCarpet()                      { return setCarpetColor(-1); }
    public LlamaWatcher setVariant(int variant)            { this.variant     = Math.max(0, Math.min(3, variant)); return this; }

    /** {@code dyeColorId} is a {@link DyeColor#getId()}; -1 (or any unknown id) removes the carpet. */
    public LlamaWatcher setCarpetColor(int dyeColorId) {
        ItemStack carpet = ItemStack.EMPTY;
        for (DyeColor color : DyeColor.values()) {
            if (color.getId() != dyeColorId) continue;
            Identifier id = Identifier.tryParse("minecraft:" + color.getName() + "_carpet");
            if (id != null) {
                carpet = BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
            }
        }
        this.carpetColor = carpet.isEmpty() ? -1 : dyeColorId;
        setCustomEquipment(EquipmentSlot.BODY, carpet);
        return this;
    }

    public boolean hasChest()    { return hasChest; }
    public int getStrength()     { return strength; }
    public int getCarpetColor()  { return carpetColor; }
    public int getVariant()      { return variant; }
}
