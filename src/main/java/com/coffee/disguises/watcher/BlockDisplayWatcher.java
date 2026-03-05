package com.coffee.disguises.watcher;

/**
 * Watcher for the block_display entity type (added in MC 1.19.4).
 *
 * Vanilla BlockDisplay DataTracker (Mojang 1.21.x):
 *   Display base fields (indices 8–22 approx):
 *     8   - interpolation start ticks  (int)
 *     9   - interpolation duration      (int)
 *     10  - teleport duration           (int)
 *     11  - translation                 (Vector3f)
 *     12  - scale                       (Vector3f)
 *     13  - left rotation               (Quaternionf)
 *     14  - right rotation              (Quaternionf)
 *     15  - billboard constraint        (byte, 0=FIXED 1=VERTICAL 2=HORIZONTAL 3=CENTER)
 *     16  - brightness override         (int, -1 = none)
 *     17  - view range                  (float)
 *     18  - shadow radius               (float)
 *     19  - shadow strength             (float)
 *     20  - width                       (float)
 *     21  - height                      (float)
 *     22  - glow color override         (int, 0 = none)
 *   BlockDisplay specific:
 *     23  - BLOCK_STATE_ID              (int) — raw block-state registry ID
 *
 * The index values above are approximate for 1.21.x; MetadataBuilder resolves
 * the actual accessor by name + index scan fallback.
 *
 * For most disguise use-cases only the block state matters.  The Display
 * transformation fields are left at their vanilla defaults (identity transform,
 * FIXED billboard, full brightness from surroundings).  Add extra fields here
 * if you need transformation control in the future.
 *
 * Usage:
 *   Disguise d = Disguise.builder(DisguiseType.BLOCK_DISPLAY)
 *       .watcher(new BlockDisplayWatcher().setBlock(Blocks.CRYING_OBSIDIAN))
 *       .build();
 */
public class BlockDisplayWatcher extends BlockStateWatcher {
    // The block is stored in the parent BlockStateWatcher.blockState field.
    // MetadataBuilder reads getBlockId() and emits the BLOCK_STATE_ID DataValue.
    //
    // No additional fields are exposed here; the Display transformation defaults
    // produce a 1×1×1 block at the entity's position with no billboard rotation,
    // which is the most useful default for a disguise.
}
