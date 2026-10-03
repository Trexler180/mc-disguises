package com.coffee.disguises.compat;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSwingAnimationPacket;
import net.minecraft.network.protocol.game.VecDelta;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionPath;

/**
 * Movement, removal and swing packets whose shape changed in Minecraft 26.3.
 *
 * This is the 26.3 implementation.  Targets older than 26.3 compile
 * versions/_legacy-common/.../PacketCompat.java instead (see
 * gradle/legacy-sources.gradle); both must keep the same signatures.
 */
public final class PacketCompat {

    private PacketCompat() {}

    public static IntList removedEntityIds(ClientboundRemoveEntitiesPacket packet) {
        return packet.entityIds();
    }

    /** A relative move + rotation; deltas are in 1/4096 of a block. */
    public static ClientboundMoveEntityPacket.PosRot posRot(int entityId, short dx, short dy, short dz,
                                                            byte yRot, byte xRot, boolean onGround) {
        return new ClientboundMoveEntityPacket.PosRot(
                entityId, new VecDelta.Linear(dx, dy, dz), yRot, xRot, onGround);
    }

    /** Copy of {@code original}'s movement with a different rotation. */
    public static ClientboundMoveEntityPacket.PosRot withRotation(ClientboundMoveEntityPacket.PosRot original,
                                                                  int entityId, byte yRot, byte xRot,
                                                                  boolean onGround) {
        return new ClientboundMoveEntityPacket.PosRot(
                entityId, original.getPositionDelta(), yRot, xRot, onGround);
    }

    /** Absolute position + rotation of {@code source}, sent for entity id {@code entityId}. */
    public static ClientboundEntityPositionSyncPacket positionSync(int entityId, Entity source) {
        return new ClientboundEntityPositionSyncPacket(entityId, PositionPath.of(source.position()),
                source.getYRot(), source.getXRot(), source.onGround());
    }

    /**
     * The entity id of an arm-swing packet, or -1 if {@code packet} is not one.
     * 26.3 moved swings out of ClientboundAnimatePacket into their own packet.
     */
    public static int swingEntityId(Packet<?> packet) {
        return packet instanceof ClientboundSwingAnimationPacket swing ? swing.entityId() : -1;
    }
}
