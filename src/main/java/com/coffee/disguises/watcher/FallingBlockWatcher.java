package com.coffee.disguises.watcher;

/**
 * Watcher for the falling_block entity type.
 *
 * The displayed block is encoded in the AddEntityPacket {@code data} field —
 * it is NOT a DataTracker value.  PacketInterceptor.getAddEntityData() reads
 * getBlockId() from this watcher when building the spawn packet.
 *
 * No additional DataTracker fields exist on FallingBlockEntity in 1.21.x beyond
 * the base Entity fields, so this watcher has no extra metadata to emit.
 *
 * Usage:
 *   Disguise d = Disguise.builder(DisguiseType.FALLING_BLOCK)
 *       .watcher(new FallingBlockWatcher().setBlock(Blocks.GRAVEL))
 *       .build();
 */
public class FallingBlockWatcher extends BlockStateWatcher {
    // No extra fields — the block is handled via the AddEntityPacket data field.
    // FlagWatcher base fields (invisible, noGravity, etc.) are still available.
}
