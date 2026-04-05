package com.coffee.disguises.watcher;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Item entity (dropped item) metadata: the displayed item stack.
 *
 * Vanilla ItemEntity DataTracker (Mojang 1.21.x):
 *   (extends Entity)
 *   index 8 - DATA_ITEM (ItemStack)
 *
 * Note: ItemEntity extends Entity directly, not LivingEntity,
 * so it has NO living-entity metadata indices. We extend FlagWatcher.
 */
public class ItemWatcher extends FlagWatcher {

    private ItemStack item = new ItemStack(Items.STONE);

    public ItemWatcher setItem(ItemStack item) { this.item = item == null ? new ItemStack(Items.STONE) : item; return this; }

    public ItemStack getItem() { return item; }
}
