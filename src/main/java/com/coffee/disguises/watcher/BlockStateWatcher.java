package com.coffee.disguises.watcher;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base watcher for entities that display a block state.
 *
 * Used by:
 *   • FallingBlockWatcher  — block is encoded in the AddEntityPacket data field
 *   • MinecartWatcher      — block is sent via DataTracker (custom display tile)
 *   • BlockDisplayWatcher  — block is sent via DataTracker
 *
 * Stores the BlockState to display.  Defaults to STONE so the entity is always
 * visible even if the caller forgets to set it.
 *
 * getBlockId() returns Block.getId(state), the raw block-state registry integer
 * used by both AddEntityPacket.data and the DataTracker INT serializer.
 */
public class BlockStateWatcher extends FlagWatcher {

    protected BlockState blockState = Blocks.STONE.defaultBlockState();

    /**
     * Set the displayed block by BlockState.
     * Example: setBlock(Blocks.DIAMOND_BLOCK.defaultBlockState())
     */
    public BlockStateWatcher setBlock(BlockState state) {
        this.blockState = (state != null) ? state : Blocks.STONE.defaultBlockState();
        return this;
    }

    /**
     * Set the displayed block by Block (uses its default BlockState).
     * Example: setBlock(Blocks.OAK_LOG)
     */
    public BlockStateWatcher setBlock(Block block) {
        return setBlock(block != null ? block.defaultBlockState() : null);
    }

    /** Returns the chosen BlockState. */
    public BlockState getBlockState() { return blockState; }

    /**
     * Returns the raw block-state registry integer (Block.getId(blockState)).
     * This is the value used in AddEntityPacket.data for FallingBlock, and in
     * the DataTracker INT field for minecarts and block_display.
     */
    public int getBlockId() {
        return Block.getId(blockState);
    }
}
