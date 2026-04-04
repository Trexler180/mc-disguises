package com.coffee.disguises.mixin;

import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/**
 * Exposes ChunkMap.TrackedEntity.seenBy — the set of player connections that
 * vanilla ServerEntity is currently sending packets to for this entity.
 *
 * Uses targets string (not class literal) because TrackedEntity is a private
 * inner class of ChunkMap and cannot be referenced by name in Java source.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccessor {

    @Accessor("seenBy")
    Set<ServerPlayerConnection> disguises$getSeenBy();
}