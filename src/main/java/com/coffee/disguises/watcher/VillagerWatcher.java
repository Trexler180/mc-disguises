package com.coffee.disguises.watcher;

/**
 * Villager-specific metadata: villager data (type, profession, level).
 *
 * Vanilla Villager DataTracker (Mojang 1.21.x):
 *   (extends AbstractVillager extends AgeableMob)
 *   index 15 - Mob flags
 *   index 16 - baby (AgeableMob)
 *   index 17 - VillagerData (type, profession, level)
 *
 * Villager types (biomes): 0=desert, 1=jungle, 2=plains, 3=savanna, 4=snow, 5=swamp, 6=taiga
 * Villager professions: 0=none, 1=armorer, 2=butcher, 3=cartographer, 4=cleric,
 *                       5=farmer, 6=fisherman, 7=fletcher, 8=leatherworker, 9=librarian,
 *                       10=mason, 11=nitwit, 12=shepherd, 13=toolsmith, 14=weaponsmith
 * Villager levels: 1=novice, 2=apprentice, 3=journeyman, 4=expert, 5=master
 */
public class VillagerWatcher extends AgeableWatcher {

    // Villager type constants
    public static final int TYPE_DESERT  = 0;
    public static final int TYPE_JUNGLE  = 1;
    public static final int TYPE_PLAINS  = 2;
    public static final int TYPE_SAVANNA = 3;
    public static final int TYPE_SNOW    = 4;
    public static final int TYPE_SWAMP   = 5;
    public static final int TYPE_TAIGA   = 6;

    // Villager profession constants
    public static final int PROF_NONE           = 0;
    public static final int PROF_ARMORER        = 1;
    public static final int PROF_BUTCHER        = 2;
    public static final int PROF_CARTOGRAPHER   = 3;
    public static final int PROF_CLERIC         = 4;
    public static final int PROF_FARMER         = 5;
    public static final int PROF_FISHERMAN      = 6;
    public static final int PROF_FLETCHER       = 7;
    public static final int PROF_LEATHERWORKER  = 8;
    public static final int PROF_LIBRARIAN      = 9;
    public static final int PROF_MASON          = 10;
    public static final int PROF_NITWIT         = 11;
    public static final int PROF_SHEPHERD       = 12;
    public static final int PROF_TOOLSMITH      = 13;
    public static final int PROF_WEAPONSMITH    = 14;

    private int type       = TYPE_PLAINS;
    private int profession = PROF_NONE;
    private int level      = 1;

    public VillagerWatcher setType(int type)           { this.type       = Math.max(0, Math.min(6, type));       return this; }
    public VillagerWatcher setProfession(int prof)     { this.profession = Math.max(0, Math.min(14, prof));      return this; }
    public VillagerWatcher setLevel(int level)         { this.level      = Math.max(1, Math.min(5, level));      return this; }

    public int getType()       { return type; }
    public int getProfession() { return profession; }
    public int getLevel()      { return level; }
}
