package com.coffee.disguises.watcher;

/**
 * Panda-specific metadata: genes.
 *
 * Vanilla Panda DataTracker (Mojang 1.21.x):
 *   (extends Animal extends AgeableMob)
 *   index 15 - Mob flags
 *   index 16 - baby (AgeableMob)
 *   index 17 - unhappy counter (int)
 *   index 18 - sneeze progress (int)
 *   index 19 - main gene  (int, 0-6; determines face/behavior — serializer is INT in 1.21.x)
 *   index 20 - hidden gene (int, 0-6; recessive trait — serializer is INT in 1.21.x)
 *   index 21 - panda flags (byte: bit1=sneezing, bit2=eating, bit3=sitting, bit4=onBack)
 *
 * Gene values:
 *   0 = normal, 1 = lazy, 2 = worried, 3 = playful, 4 = brown (recessive), 5 = weak (recessive), 6 = aggressive
 */
public class PandaWatcher extends AgeableWatcher {

    public static final int GENE_NORMAL     = 0;
    public static final int GENE_LAZY       = 1;
    public static final int GENE_WORRIED    = 2;
    public static final int GENE_PLAYFUL    = 3;
    public static final int GENE_BROWN      = 4;
    public static final int GENE_WEAK       = 5;
    public static final int GENE_AGGRESSIVE = 6;

    private int mainGene   = GENE_NORMAL;
    private int hiddenGene = GENE_NORMAL;

    public PandaWatcher setMainGene(int gene)   { this.mainGene   = clamp(gene); return this; }
    public PandaWatcher setHiddenGene(int gene) { this.hiddenGene = clamp(gene); return this; }

    public int getMainGene()   { return mainGene; }
    public int getHiddenGene() { return hiddenGene; }

    private static int clamp(int g) { return Math.max(0, Math.min(GENE_AGGRESSIVE, g)); }
}
