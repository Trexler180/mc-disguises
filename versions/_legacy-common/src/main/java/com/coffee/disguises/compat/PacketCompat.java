package com.coffee.disguises.compat;

import com.coffee.disguises.packet.PacketInterceptor;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;

/**
 * Pre-26.3 implementation of src/main/java/.../compat/PacketCompat.java, used by
 * every legacy target (26.2, 26.1.2, 1.21.11).  Keep the signatures identical.
 */
public final class PacketCompat {

    private PacketCompat() {}

    public static IntList removedEntityIds(ClientboundRemoveEntitiesPacket packet) {
        return packet.getEntityIds();
    }

    /** A relative move + rotation; deltas are in 1/4096 of a block. */
    public static ClientboundMoveEntityPacket.PosRot posRot(int entityId, short dx, short dy, short dz,
                                                            byte yRot, byte xRot, boolean onGround) {
        return new ClientboundMoveEntityPacket.PosRot(entityId, dx, dy, dz, yRot, xRot, onGround);
    }

    /** Copy of {@code original}'s movement with a different rotation. */
    public static ClientboundMoveEntityPacket.PosRot withRotation(ClientboundMoveEntityPacket.PosRot original,
                                                                  int entityId, byte yRot, byte xRot,
                                                                  boolean onGround) {
        return new ClientboundMoveEntityPacket.PosRot(
                entityId, original.getXa(), original.getYa(), original.getZa(), yRot, xRot, onGround);
    }

    /** Absolute position + rotation of {@code source}, sent for entity id {@code entityId}. */
    public static ClientboundEntityPositionSyncPacket positionSync(int entityId, Entity source) {
        return new ClientboundEntityPositionSyncPacket(
                entityId, PositionMoveRotation.of(source), source.onGround());
    }

    /** The entity id of an arm-swing packet, or -1 if {@code packet} is not one. */
    public static int swingEntityId(Packet<?> packet) {
        if (!(packet instanceof ClientboundAnimatePacket animate)) return -1;
        int action = PacketInterceptor.getAnimateAction(animate);
        return action == ClientboundAnimatePacket.SWING_MAIN_HAND
                || action == ClientboundAnimatePacket.SWING_OFF_HAND ? animate.getId() : -1;
    }
}
