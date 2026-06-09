package com.coffee.disguises.compat;

import net.minecraft.world.entity.Entity;

import java.util.Set;

final class EntityTagCompat {

    private EntityTagCompat() {
    }

    static Set<String> tags(Entity entity) {
        return entity.entityTags();
    }
}
