package com.coffee.disguises.watcher;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Strider-specific metadata: shaking (wet), saddled.
 *
 * Vanilla Strider DataTracker (Mojang 1.21.x):
 *   (extends Animal extends AgeableMob)
 *   index 15 - Mob flags
 *   index 16 - baby (AgeableMob)
 *   index 17 - boost time remaining (int, used for boost animation)
 *   index 18 - shaking (boolean, true when cold/wet — shivering animation)
 *
 * Since 1.21.5 the saddle is not metadata but an item in the SADDLE equipment
 * slot, so {@link #setSaddled} maintains that slot in the custom equipment.
 */
public class StriderWatcher extends AgeableWatcher {

    private boolean shaking = false;
    private boolean saddled = false;

    public StriderWatcher setShaking(boolean shaking) { this.shaking = shaking; return this; }

    public StriderWatcher setSaddled(boolean saddled) {
        this.saddled = saddled;
        setCustomEquipment(EquipmentSlot.SADDLE, saddled ? new ItemStack(Items.SADDLE) : ItemStack.EMPTY);
        return this;
    }

    public boolean isShaking() { return shaking; }
    public boolean isSaddled() { return saddled; }
}
