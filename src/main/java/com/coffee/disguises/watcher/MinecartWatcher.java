package com.coffee.disguises.watcher;

/**
 * Watcher for all minecart entity types (minecart, chest_minecart, hopper_minecart, etc.).
 *
 * Vanilla AbstractMinecart DataTracker (Mojang 1.21.x):
 *   index 11 - DISPLAY_BLOCK_STATE_ID  (int) — raw block-state ID of the custom display tile;
 *                                               0 = use the default display for this minecart type
 *   index 12 - DISPLAY_OFFSET_ID       (int) — Y offset of the custom display tile in pixels
 *                                               default = 6
 *   index 13 - CUSTOM_DISPLAY_ID       (boolean) — whether to use the custom tile instead of
 *                                                   the type's default display
 *
 * Note: the exact indices above depend on the full class hierarchy.  Base Entity fields
 * (0–7) come first, so AbstractMinecart-specific accessors typically start at 8.
 * MetadataBuilder resolves the actual accessor by name + index fallback.
 *
 * Usage:
 *   Disguise d = Disguise.builder(DisguiseType.MINECART)
 *       .watcher(new MinecartWatcher().setBlock(Blocks.TNT))
 *       .build();
 *
 *   // Force no display block (show vanilla empty minecart):
 *   new MinecartWatcher()   // displayBlock defaults to false → uses type default
 *
 *   // Show a chest minecart with a gold block inside:
 *   new MinecartWatcher().setBlock(Blocks.GOLD_BLOCK)
 */
public class MinecartWatcher extends BlockStateWatcher {

    /**
     * Pixel Y-offset of the displayed block inside the minecart.
     * Vanilla default is 6. Increase to push the block higher.
     */
    private int displayOffset = 6;

    /**
     * Whether to actually use the custom block.
     * Automatically set to true when setBlock() is called.
     */
    private boolean useCustomDisplay = false;

    @Override
    public MinecartWatcher setBlock(net.minecraft.world.level.block.Block block) {
        super.setBlock(block);
        this.useCustomDisplay = true;
        return this;
    }

    @Override
    public MinecartWatcher setBlock(net.minecraft.world.level.block.state.BlockState state) {
        super.setBlock(state);
        this.useCustomDisplay = true;
        return this;
    }

    /** Set the Y pixel offset of the block displayed in the minecart (default 6). */
    public MinecartWatcher setDisplayOffset(int offset) {
        this.displayOffset = offset;
        return this;
    }

    /**
     * Explicitly control whether the custom block is active.
     * Calling setBlock() already enables this automatically.
     */
    public MinecartWatcher setUseCustomDisplay(boolean use) {
        this.useCustomDisplay = use;
        return this;
    }

    public int getDisplayOffset() { return displayOffset; }
    public boolean isUseCustomDisplay() { return useCustomDisplay; }
}
