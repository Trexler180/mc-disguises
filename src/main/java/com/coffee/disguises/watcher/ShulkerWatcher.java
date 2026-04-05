package com.coffee.disguises.watcher;

import net.minecraft.core.Direction;

/**
 * Shulker-specific metadata: attach face, peek, color.
 *
 * Vanilla Shulker DataTracker (Mojang 1.21.x):
 *   (extends AbstractGolem extends Mob)
 *   index 15 - Mob flags
 *   index 16 - DATA_ATTACH_FACE_ID (Direction)
 *   index 17 - DATA_PEEK_ID (byte, 0-100, how open the shell is)
 *   index 18 - DATA_COLOR_ID (byte, DyeColor ID, 16=default/purple)
 *
 * Direction values: 0=DOWN, 1=UP, 2=NORTH, 3=SOUTH, 4=WEST, 5=EAST
 * Color: 0-15 = DyeColor IDs, 16 = default purple (no tint)
 */
public class ShulkerWatcher extends LivingEntityWatcher {

    public static final int COLOR_DEFAULT = 16;  // default purple

    private Direction attachFace = Direction.DOWN;
    private byte      peek       = 0;
    private byte      color      = (byte) COLOR_DEFAULT;

    public ShulkerWatcher setAttachFace(Direction face)  { this.attachFace = face;                                         return this; }
    public ShulkerWatcher setAttachFace(int faceId)      { this.attachFace = Direction.from3DDataValue(Math.max(0, Math.min(5, faceId))); return this; }
    public ShulkerWatcher setPeek(int peek)              { this.peek  = (byte) Math.max(0, Math.min(100, peek));           return this; }
    public ShulkerWatcher setColor(int color)            { this.color = (byte) Math.max(0, Math.min(16, color));           return this; }

    public Direction getAttachFace() { return attachFace; }
    public byte getPeek()            { return peek; }
    public byte getColor()           { return color; }
}
