package com.coffee.disguises.watcher;

/**
 * Rabbit-specific metadata: fur type.
 *
 * Vanilla Rabbit DataTracker (Mojang 1.21.x):
 *   (extends Animal extends AgeableMob)
 *   index 15 - Mob flags
 *   index 16 - baby (AgeableMob)
 *   index 17 - type (int):
 *                0 = brown
 *                1 = white
 *                2 = black
 *                3 = black and white
 *                4 = gold
 *                5 = salt and pepper
 *               99 = killer rabbit (evil red eyes)
 */
public class RabbitWatcher extends AgeableWatcher {

    public static final int BROWN         = 0;
    public static final int WHITE         = 1;
    public static final int BLACK         = 2;
    public static final int BLACK_WHITE   = 3;
    public static final int GOLD          = 4;
    public static final int SALT          = 5;
    public static final int KILLER        = 99;

    private int type = BROWN;

    public RabbitWatcher setType(int type) { this.type = type; return this; }
    public int getType() { return type; }
}
