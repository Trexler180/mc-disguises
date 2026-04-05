package com.coffee.disguises.watcher;

/**
 * ZombieVillager-specific metadata: converting, villager data.
 *
 * Vanilla ZombieVillager DataTracker (Mojang 1.21.x):
 *   (extends Zombie extends Monster extends Mob)
 *   index 15 - Mob flags
 *   index 18 - Zombie: baby (bool), hands-up (bool flags)
 *   index 19 - converting / shaking (bool)
 *   index 20 - VillagerData (type, profession, level)
 *
 * Note: ZombieVillager extends Zombie (not AgeableMob directly) so baby
 * is handled via Zombie's own flag, not AgeableMob. We extend LivingEntityWatcher.
 */
public class ZombieVillagerWatcher extends LivingEntityWatcher {

    private boolean converting = false;
    private int     type       = VillagerWatcher.TYPE_PLAINS;
    private int     profession = VillagerWatcher.PROF_NONE;
    private int     level      = 1;

    public ZombieVillagerWatcher setConverting(boolean converting) { this.converting = converting;                            return this; }
    public ZombieVillagerWatcher setType(int type)                 { this.type       = Math.max(0, Math.min(6, type));        return this; }
    public ZombieVillagerWatcher setProfession(int prof)           { this.profession = Math.max(0, Math.min(14, prof));       return this; }
    public ZombieVillagerWatcher setLevel(int level)               { this.level      = Math.max(1, Math.min(5, level));       return this; }

    public boolean isConverting() { return converting; }
    public int getType()          { return type; }
    public int getProfession()    { return profession; }
    public int getLevel()         { return level; }
}
