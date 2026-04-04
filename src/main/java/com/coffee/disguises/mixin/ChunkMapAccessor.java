package com.coffee.disguises.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes ChunkMap.entityMap so we can check which observers vanilla's
 * ServerEntity is currently broadcasting to for a given entity.
 *
 * Raw Int2ObjectMap (no generic) because ChunkMap.TrackedEntity is a
 * private inner class and cannot be referenced in Java source code.
 * Callers cast individual values to TrackedEntityAccessor at runtime.
 */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {

    @Accessor("entityMap")
    @SuppressWarnings("rawtypes")
    Int2ObjectMap disguises$getEntityMap();
}