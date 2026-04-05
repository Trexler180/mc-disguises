package com.coffee.disguises.packet;

import com.coffee.disguises.watcher.*;
import com.coffee.disguises.watcher.BlockDisplayWatcher;
import com.coffee.disguises.watcher.FallingBlockWatcher;
import com.coffee.disguises.watcher.MinecartWatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Rotations;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Builds SynchedEntityData.DataValue lists from a FlagWatcher for use in
 * ClientboundSetEntityDataPacket.
 *
 * ─── FIELD RESOLUTION STRATEGY ────────────────────────────────────────────────
 *
 * Mojang-mapped field names occasionally change between minor MC versions.
 * We use a two-pass approach to maximise resilience:
 *
 *   Pass 1 — Name lookup:
 *     Try a list of candidate names (known names across MC versions) via
 *     Class.getDeclaredField(). The first that resolves wins.
 *
 *   Pass 2 — Scan fallback:
 *     If ALL name candidates fail, scan every static field of the class that
 *     is an EntityDataAccessor and pick the one at the expected DataTracker
 *     index (the accessor's getId() value). Index numbers are stable even when
 *     field names change, because they are assigned sequentially by
 *     SynchedEntityData.defineId() in the order the fields are declared.
 *
 * ─── KNOWN INDICES (1.21.x Mojang) ───────────────────────────────────────────
 *
 * Entity base (0–7): flags, air, customName, customNameVisible, silent, noGravity, pose, frozenTicks
 * LivingEntity (8–14): living-flags, health, effect-color, effect-ambient, arrows, stingers, sleepPos
 * Mob (15): mob-flags (noAI)
 * AgeableMob (16): baby
 * Animal (no new DA fields)
 * Sheep (17): color+sheared packed byte
 * Creeper (15 from Mob, 16–18): swellDir, isPowered, isIgnited
 * Slime (15 from Mob, 16): size
 * TamableAnimal (15 from Mob, 16–17): flags, ownerUUID
 * Wolf (18+): collarColor, angerTime, ...
 * Enderman (15 from Mob, 16–18): carriedBlock, isCreepy, hasBeenStaredAt
 * Player (15–17 from... actually Player extends LivingEntity directly, skipping Mob,
 *         so indices continue at 15): main hand, absorption, score, skin-customisation,
 *         player-model-parts, etc. — exact layout varies
 *
 * If indices change in a future MC version, update INDEX_* constants below.
 */
public class MetadataBuilder {

    // ── Field slots ───────────────────────────────────────────────────────────

    // Entity base
    private static Field F_ENTITY_FLAGS;
    private static Field F_ENTITY_CUSTOM_NAME;
    private static Field F_ENTITY_CUSTOM_NAME_VISIBLE;
    private static Field F_ENTITY_SILENT;
    private static Field F_ENTITY_NO_GRAVITY;

    // LivingEntity
    private static Field F_LIVING_FLAGS;   // index 8: hand-active / active-hand / riptide
    private static Field F_LIVING_HEALTH;
    private static Field F_LIVING_ARROW_COUNT;
    private static Field F_LIVING_STINGER_COUNT;
    // NOTE: effect color/ambient were reworked in 1.21 to use a Set<MobEffectInstance>.
    // We skip them — the default (0/false) is fine.

    // AgeableMob
    private static Field F_AGEABLE_BABY;

    // Sheep
    private static Field F_SHEEP_COLOR;   // packed byte: bits 0–3 = DyeColor ordinal, bit 4 = sheared

    // Creeper
    private static Field F_CREEPER_SWELL_DIR;
    private static Field F_CREEPER_POWERED;
    private static Field F_CREEPER_IGNITED;

    // Slime
    private static Field F_SLIME_SIZE;

    // TamableAnimal + Wolf
    private static Field F_TAMEABLE_FLAGS;
    private static Field F_WOLF_COLLAR_COLOR;
    private static Field F_WOLF_ANGER_TIME;

    // Enderman
    private static Field F_ENDERMAN_CREEPY;

    // Player skin parts
    private static Field F_PLAYER_SKIN_PARTS;

    // AbstractMinecart custom display block
    // DATA_ID_DISPLAY_BLOCK  (int)  — raw block-state ID  ← INT, not BlockState
    // DATA_ID_DISPLAY_OFFSET (int)  — Y pixel offset (default 6)
    // DATA_ID_CUSTOM_DISPLAY (bool) — whether to use the custom tile
    private static Field F_MINECART_DISPLAY_BLOCK;
    private static Field F_MINECART_DISPLAY_OFFSET;
    private static Field F_MINECART_CUSTOM_DISPLAY;

    // BlockDisplay block state
    // Vanilla BlockDisplay.DATA_BLOCK_STATE_ID uses EntityDataSerializers.BLOCK_STATE —
    // the accessor type is EntityDataAccessor<BlockState>, NOT <Integer>.
    private static Field F_BLOCK_DISPLAY_BLOCK_STATE;

    // Entity pose (Pose enum, index 6)
    private static Field F_ENTITY_POSE;

    // Display base — scale field (Vector3f, index 12 in 1.21.x)
    // Sending (1,1,1) is required; the default is (0,0,0) which makes the entity invisible.
    private static Field F_DISPLAY_SCALE;

    // Phantom
    private static Field F_PHANTOM_SIZE;

    // Goat
    private static Field F_GOAT_SCREAMING;
    private static Field F_GOAT_LEFT_HORN;
    private static Field F_GOAT_RIGHT_HORN;

    // Bee
    private static Field F_BEE_ANGER_TIME;
    private static Field F_BEE_FLAGS;

    // Strider
    private static Field F_STRIDER_SHAKING;
    private static Field F_STRIDER_SADDLE;

    // Piglin
    private static Field F_PIGLIN_BABY;
    private static Field F_PIGLIN_CHARGING;
    private static Field F_PIGLIN_DANCING;
    private static Field F_PIGLIN_CELEBRATING;

    // ArmorStand
    private static Field F_ARMOR_STAND_FLAGS;

    // Parrot
    private static Field F_PARROT_VARIANT;

    // TropicalFish
    private static Field F_TROPICAL_FISH_VARIANT;

    // Rabbit
    private static Field F_RABBIT_TYPE;

    // Fox
    private static Field F_FOX_TYPE;
    private static Field F_FOX_FLAGS;

    // Panda
    private static Field F_PANDA_MAIN_GENE;
    private static Field F_PANDA_HIDDEN_GENE;

    // Axolotl
    private static Field F_AXOLOTL_VARIANT;
    private static Field F_AXOLOTL_PLAYING_DEAD;

    // Horse
    private static Field F_ABSTRACT_HORSE_FLAGS;
    private static Field F_HORSE_VARIANT;

    // Llama / AbstractChestedHorse
    private static Field F_ABSTRACT_CHESTED_HORSE_CHEST;
    private static Field F_LLAMA_STRENGTH;
    private static Field F_LLAMA_CARPET_COLOR;
    private static Field F_LLAMA_VARIANT;

    // Cat
    private static Field F_CAT_VARIANT;
    private static Field F_CAT_LYING;
    private static Field F_CAT_RELAXED;
    private static Field F_CAT_COLLAR_COLOR;

    // Frog
    private static Field F_FROG_VARIANT;

    // MushroomCow (Mooshroom)
    private static Field F_MOOSHROOM_VARIANT;

    // Villager
    private static Field F_VILLAGER_DATA;

    // ZombieVillager
    private static Field F_ZOMBIE_VILLAGER_CONVERTING;
    private static Field F_ZOMBIE_VILLAGER_DATA;

    // Shulker
    private static Field F_SHULKER_ATTACH_FACE;
    private static Field F_SHULKER_PEEK;
    private static Field F_SHULKER_COLOR;

    // ItemEntity
    private static Field F_ITEM_ENTITY_ITEM;

    // ArmorStand poses
    private static Field F_ARMOR_STAND_HEAD_POSE;
    private static Field F_ARMOR_STAND_BODY_POSE;
    private static Field F_ARMOR_STAND_LEFT_ARM_POSE;
    private static Field F_ARMOR_STAND_RIGHT_ARM_POSE;
    private static Field F_ARMOR_STAND_LEFT_LEG_POSE;
    private static Field F_ARMOR_STAND_RIGHT_LEG_POSE;

    // ── Expected DataTracker indices ──────────────────────────────────────────
    // Used as fallback when name-based lookup fails.

    private static final int IDX_ENTITY_FLAGS                = 0;
    private static final int IDX_ENTITY_CUSTOM_NAME          = 2;
    private static final int IDX_ENTITY_CUSTOM_NAME_VISIBLE  = 3;
    private static final int IDX_ENTITY_SILENT               = 4;
    private static final int IDX_ENTITY_NO_GRAVITY           = 5;
    private static final int IDX_ENTITY_POSE                 = 6;

    private static final int IDX_LIVING_FLAGS                = 8;
    private static final int IDX_LIVING_HEALTH               = 9;
    private static final int IDX_LIVING_ARROW_COUNT          = 12;
    private static final int IDX_LIVING_STINGER_COUNT        = 13;

    private static final int IDX_AGEABLE_BABY                = 16;

    private static final int IDX_SHEEP_COLOR                 = 17;

    private static final int IDX_CREEPER_SWELL_DIR           = 16;
    private static final int IDX_CREEPER_POWERED             = 17;
    private static final int IDX_CREEPER_IGNITED             = 18;

    private static final int IDX_SLIME_SIZE                  = 16;

    // TamableAnimal and Wolf: indices shift up because AgeableMob.DATA_BABY_ID occupies 16,
    // TamableAnimal adds DATA_FLAGS_ID(17) and DATA_OWNERUUID_ID(18), then Wolf adds its
    // own fields starting at 19.  Name-based lookup is preferred; IDX_* are fallback-only.
    private static final int IDX_TAMEABLE_FLAGS              = 17; // TamableAnimal.DATA_FLAGS_ID
    private static final int IDX_WOLF_COLLAR_COLOR           = 20; // Wolf.DATA_COLLAR_COLOR
    private static final int IDX_WOLF_ANGER_TIME             = 21; // Wolf.DATA_ANGER_END_TIME (Long in 1.21.11)

    private static final int IDX_ENDERMAN_CREEPY             = 17;

    private static final int IDX_PLAYER_SKIN_PARTS           = 16; // DATA_PLAYER_MODE_CUSTOMISATION (Avatar index in 1.21.11)

    // AbstractMinecart (indices relative to full entity hierarchy — approximately 11–13 in 1.21.x)
    private static final int IDX_MINECART_DISPLAY_BLOCK   = 11;
    private static final int IDX_MINECART_DISPLAY_OFFSET  = 12;
    private static final int IDX_MINECART_CUSTOM_DISPLAY  = 13;

    // BlockDisplay block state (first Display subtype-specific field, ~index 23 in 1.21.x)
    private static final int IDX_BLOCK_DISPLAY_BLOCK_STATE = 23;

    // Display.DATA_SCALE_ID — index 12 in 1.21.x Mojang mappings
    private static final int IDX_DISPLAY_SCALE = 12;

    private static final int IDX_PHANTOM_SIZE            = 16;

    private static final int IDX_GOAT_SCREAMING          = 17;
    private static final int IDX_GOAT_LEFT_HORN          = 18;
    private static final int IDX_GOAT_RIGHT_HORN         = 19;

    private static final int IDX_BEE_ANGER_TIME          = 17;
    private static final int IDX_BEE_FLAGS               = 18;

    private static final int IDX_STRIDER_SHAKING         = 18;
    private static final int IDX_STRIDER_SADDLE          = 19;

    private static final int IDX_PIGLIN_BABY             = 16;
    private static final int IDX_PIGLIN_CHARGING         = 17;
    private static final int IDX_PIGLIN_DANCING          = 18;
    private static final int IDX_PIGLIN_CELEBRATING      = 19;

    private static final int IDX_ARMOR_STAND_FLAGS       = 15;

    private static final int IDX_PARROT_VARIANT          = 19;

    private static final int IDX_TROPICAL_FISH_VARIANT   = 16;

    private static final int IDX_RABBIT_TYPE             = 17;

    private static final int IDX_FOX_TYPE                = 17;
    private static final int IDX_FOX_FLAGS               = 18;

    private static final int IDX_PANDA_MAIN_GENE         = 19;
    private static final int IDX_PANDA_HIDDEN_GENE       = 20;

    private static final int IDX_AXOLOTL_VARIANT         = 17;
    private static final int IDX_AXOLOTL_PLAYING_DEAD    = 18;

    private static final int IDX_ABSTRACT_HORSE_FLAGS    = 17;
    private static final int IDX_HORSE_VARIANT           = 18;

    private static final int IDX_ABSTRACT_CHESTED_CHEST  = 18;
    private static final int IDX_LLAMA_STRENGTH          = 19;
    private static final int IDX_LLAMA_CARPET_COLOR      = 20;
    private static final int IDX_LLAMA_VARIANT           = 21;

    private static final int IDX_CAT_VARIANT             = 19;
    private static final int IDX_CAT_LYING               = 20;
    private static final int IDX_CAT_RELAXED             = 21;
    private static final int IDX_CAT_COLLAR_COLOR        = 22;

    private static final int IDX_FROG_VARIANT            = 17;

    private static final int IDX_MOOSHROOM_VARIANT       = 17;

    private static final int IDX_VILLAGER_DATA           = 17;

    private static final int IDX_ZOMBIE_VILLAGER_CONVERTING = 19;
    private static final int IDX_ZOMBIE_VILLAGER_DATA    = 20;

    private static final int IDX_SHULKER_ATTACH_FACE     = 16;
    private static final int IDX_SHULKER_PEEK            = 17;
    private static final int IDX_SHULKER_COLOR           = 18;

    private static final int IDX_ITEM_ENTITY_ITEM        = 8;

    private static final int IDX_ARMOR_STAND_HEAD_POSE      = 16;
    private static final int IDX_ARMOR_STAND_BODY_POSE      = 17;
    private static final int IDX_ARMOR_STAND_LEFT_ARM_POSE  = 18;
    private static final int IDX_ARMOR_STAND_RIGHT_ARM_POSE = 19;
    private static final int IDX_ARMOR_STAND_LEFT_LEG_POSE  = 20;
    private static final int IDX_ARMOR_STAND_RIGHT_LEG_POSE = 21;

    // ─────────────────────────────────────────────────────────────────────────

    private static volatile boolean initialized = false;
    private static volatile boolean minecartInitialized = false;

    public static synchronized void init() {
        if (initialized) return;

        com.coffee.disguises.DisguisesMod.LOGGER.info("[MetadataBuilder] Resolving EntityDataAccessor fields…");

        F_ENTITY_FLAGS               = resolve(Entity.class,        IDX_ENTITY_FLAGS,               "DATA_SHARED_FLAGS_ID");
        F_ENTITY_CUSTOM_NAME         = resolve(Entity.class,        IDX_ENTITY_CUSTOM_NAME,         "DATA_CUSTOM_NAME");
        F_ENTITY_CUSTOM_NAME_VISIBLE = resolve(Entity.class,        IDX_ENTITY_CUSTOM_NAME_VISIBLE,  "DATA_CUSTOM_NAME_VISIBLE");
        F_ENTITY_SILENT              = resolve(Entity.class,        IDX_ENTITY_SILENT,              "DATA_SILENT");
        F_ENTITY_NO_GRAVITY          = resolve(Entity.class,        IDX_ENTITY_NO_GRAVITY,          "DATA_NO_GRAVITY");
        F_ENTITY_POSE                = resolve(Entity.class,        IDX_ENTITY_POSE,                "DATA_POSE");

        F_LIVING_FLAGS         = resolve(LivingEntity.class, IDX_LIVING_FLAGS,         "DATA_LIVING_ENTITY_FLAGS", "DATA_LIVING_FLAGS");
        F_LIVING_HEALTH        = resolve(LivingEntity.class, IDX_LIVING_HEALTH,        "DATA_HEALTH_ID",           "DATA_HEALTH");
        F_LIVING_ARROW_COUNT   = resolve(LivingEntity.class, IDX_LIVING_ARROW_COUNT,   "DATA_ARROW_COUNT_ID",      "DATA_ARROWS_ID");
        F_LIVING_STINGER_COUNT = resolve(LivingEntity.class, IDX_LIVING_STINGER_COUNT, "DATA_STINGER_COUNT_ID",    "DATA_STINGERS_ID");

        F_AGEABLE_BABY = resolve(AgeableMob.class, IDX_AGEABLE_BABY, "DATA_BABY_ID", "IS_BABY");

        // Sheep — use the class literal so Loom correctly remaps it in the production jar.
        // (String-based loadClass is NOT remapped and fails at runtime on a Fabric server.)
        F_SHEEP_COLOR = resolve(Sheep.class, IDX_SHEEP_COLOR,
                "DATA_WOOL_ID", "DATA_SHEARED_ID", "DATA_SHEEP_FLAGS", "DATA_COLOR_ID");

        F_CREEPER_SWELL_DIR = resolve(Creeper.class, IDX_CREEPER_SWELL_DIR, "DATA_SWELL_DIR",  "DATA_SWELL_DIRECTION");
        F_CREEPER_POWERED   = resolve(Creeper.class, IDX_CREEPER_POWERED,   "DATA_IS_POWERED", "DATA_POWERED");
        F_CREEPER_IGNITED   = resolve(Creeper.class, IDX_CREEPER_IGNITED,   "DATA_IS_IGNITED", "DATA_IGNITED");

        F_SLIME_SIZE = resolve(Slime.class, IDX_SLIME_SIZE, "DATA_SIZE", "ID_SIZE", "DATA_SLIME_SIZE");

        F_TAMEABLE_FLAGS    = resolve(TamableAnimal.class, IDX_TAMEABLE_FLAGS,    "DATA_FLAGS_ID",             "DATA_TAME_FLAGS");
        F_WOLF_COLLAR_COLOR = resolve(Wolf.class,          IDX_WOLF_COLLAR_COLOR, "DATA_COLLAR_COLOR",         "DATA_COLLAR_COLOR_ID");
        // DATA_ANGER_END_TIME is a Long (game-tick timestamp) in 1.21.11, renamed from the old
        // integer "ticks remaining" field.  List the 1.21.11 name first; older names follow.
        F_WOLF_ANGER_TIME   = resolve(Wolf.class,          IDX_WOLF_ANGER_TIME,   "DATA_ANGER_END_TIME", "DATA_REMAINING_ANGER_TIME", "DATA_ANGER_TIME", "DATA_ANGRY");

        F_ENDERMAN_CREEPY = resolve(EnderMan.class, IDX_ENDERMAN_CREEPY, "DATA_CREEPY", "DATA_CREEPY_STARE", "DATA_IS_SCREAMING");

        F_PHANTOM_SIZE = resolve(Phantom.class, IDX_PHANTOM_SIZE,
                "DATA_SIZE", "DATA_ID_SIZE", "DATA_PHANTOM_SIZE");

        F_GOAT_SCREAMING  = resolve(Goat.class, IDX_GOAT_SCREAMING,
                "DATA_IS_SCREAMING", "DATA_GOAT_SCREAMING");
        F_GOAT_LEFT_HORN  = resolve(Goat.class, IDX_GOAT_LEFT_HORN,
                "DATA_HAS_LEFT_HORN", "DATA_LEFT_HORN");
        F_GOAT_RIGHT_HORN = resolve(Goat.class, IDX_GOAT_RIGHT_HORN,
                "DATA_HAS_RIGHT_HORN", "DATA_RIGHT_HORN");

        F_BEE_ANGER_TIME = resolve(Bee.class, IDX_BEE_ANGER_TIME,
                "DATA_REMAINING_ANGER_TIME", "DATA_ANGER_TIME", "DATA_ANGRY");
        F_BEE_FLAGS      = resolve(Bee.class, IDX_BEE_FLAGS,
                "DATA_FLAGS_ID", "DATA_FLAGS", "DATA_BEE_FLAGS");

        F_STRIDER_SHAKING = resolve(Strider.class, IDX_STRIDER_SHAKING,
                "DATA_SHAKING", "DATA_SHAKING_ID", "DATA_SUFFOCATING");
        F_STRIDER_SADDLE  = resolve(Strider.class, IDX_STRIDER_SADDLE,
                "DATA_HAS_SADDLE", "DATA_SADDLED", "DATA_SADDLE");

        F_PIGLIN_BABY        = resolve(Piglin.class, IDX_PIGLIN_BABY,
                "DATA_BABY_PIGLIN", "DATA_BABY");
        F_PIGLIN_CHARGING    = resolve(Piglin.class, IDX_PIGLIN_CHARGING,
                "DATA_IS_CHARGING_CROSSBOW", "DATA_CHARGING_CROSSBOW");
        F_PIGLIN_DANCING     = resolve(Piglin.class, IDX_PIGLIN_DANCING,
                "DATA_IS_DANCING", "DATA_DANCING");
        F_PIGLIN_CELEBRATING = resolve(Piglin.class, IDX_PIGLIN_CELEBRATING,
                "DATA_IS_CELEBRATING", "DATA_CELEBRATING");

        F_ARMOR_STAND_FLAGS = resolve(ArmorStand.class, IDX_ARMOR_STAND_FLAGS,
                "DATA_CLIENT_FLAGS", "DATA_FLAGS", "DATA_ARMOR_STAND_FLAGS");

        F_PARROT_VARIANT = resolve(Parrot.class, IDX_PARROT_VARIANT,
                "DATA_VARIANT_ID", "DATA_VARIANT", "DATA_PARROT_VARIANT");

        F_TROPICAL_FISH_VARIANT = resolve(TropicalFish.class, IDX_TROPICAL_FISH_VARIANT,
                "DATA_ID_TYPE_VARIANT", "DATA_VARIANT", "DATA_TYPE_VARIANT");

        F_RABBIT_TYPE = resolve(Rabbit.class, IDX_RABBIT_TYPE,
                "DATA_TYPE_ID", "DATA_RABBIT_TYPE", "DATA_TYPE");

        F_FOX_TYPE  = resolve(Fox.class, IDX_FOX_TYPE,
                "DATA_TYPE_ID", "DATA_FOX_TYPE", "DATA_TYPE");
        F_FOX_FLAGS = resolve(Fox.class, IDX_FOX_FLAGS,
                "DATA_FLAGS_ID", "DATA_FOX_FLAGS", "DATA_FLAGS");

        F_PANDA_MAIN_GENE   = resolve(Panda.class, IDX_PANDA_MAIN_GENE,
                "DATA_MAIN_GENE_ID", "DATA_MAIN_GENE");
        F_PANDA_HIDDEN_GENE = resolve(Panda.class, IDX_PANDA_HIDDEN_GENE,
                "DATA_HIDDEN_GENE_ID", "DATA_HIDDEN_GENE");

        F_AXOLOTL_VARIANT      = resolve(Axolotl.class, IDX_AXOLOTL_VARIANT,
                "DATA_VARIANT", "DATA_VARIANT_ID", "DATA_AXOLOTL_VARIANT");
        F_AXOLOTL_PLAYING_DEAD = resolve(Axolotl.class, IDX_AXOLOTL_PLAYING_DEAD,
                "DATA_PLAYING_DEAD", "DATA_PLAY_DEAD", "DATA_PLAYING_DEAD_ID");

        // Horse
        F_ABSTRACT_HORSE_FLAGS = resolve(AbstractHorse.class, IDX_ABSTRACT_HORSE_FLAGS,
                "DATA_ID_FLAGS", "DATA_FLAGS_ID", "DATA_HORSE_FLAGS");
        F_HORSE_VARIANT = resolve(Horse.class, IDX_HORSE_VARIANT,
                "DATA_ID_TYPE_VARIANT", "DATA_VARIANT", "DATA_HORSE_VARIANT");

        // Llama
        F_ABSTRACT_CHESTED_HORSE_CHEST = resolve(AbstractChestedHorse.class, IDX_ABSTRACT_CHESTED_CHEST,
                "DATA_ID_CHEST", "DATA_CHEST_ID", "DATA_HAS_CHEST");
        F_LLAMA_STRENGTH    = resolve(Llama.class, IDX_LLAMA_STRENGTH,
                "DATA_STRENGTH_ID", "DATA_STRENGTH", "DATA_LLAMA_STRENGTH");
        F_LLAMA_CARPET_COLOR = resolve(Llama.class, IDX_LLAMA_CARPET_COLOR,
                "DATA_CARPET_COLOR", "DATA_CARPET_COLOR_ID", "DATA_DECOR_COLOR_ID");
        F_LLAMA_VARIANT     = resolve(Llama.class, IDX_LLAMA_VARIANT,
                "DATA_VARIANT_ID", "DATA_VARIANT", "DATA_LLAMA_VARIANT");

        // Cat
        F_CAT_VARIANT       = resolve(Cat.class, IDX_CAT_VARIANT,
                "DATA_VARIANT_ID", "DATA_VARIANT", "DATA_CAT_VARIANT");
        F_CAT_LYING         = resolve(Cat.class, IDX_CAT_LYING,
                "DATA_IS_LYING", "IS_LYING", "DATA_CAT_LYING");
        F_CAT_RELAXED       = resolve(Cat.class, IDX_CAT_RELAXED,
                "DATA_RELAX_STATE_ONE", "RELAX_STATE_ONE", "DATA_CAT_RELAXED");
        F_CAT_COLLAR_COLOR  = resolve(Cat.class, IDX_CAT_COLLAR_COLOR,
                "DATA_COLLAR_COLOR", "DATA_COLLAR_COLOR_ID");

        // Frog
        F_FROG_VARIANT = resolve(Frog.class, IDX_FROG_VARIANT,
                "DATA_VARIANT_ID", "DATA_VARIANT", "DATA_FROG_VARIANT");

        // Mooshroom
        F_MOOSHROOM_VARIANT = resolve(MushroomCow.class, IDX_MOOSHROOM_VARIANT,
                "DATA_TYPE", "DATA_VARIANT", "DATA_MOOSHROOM_TYPE");

        // Villager
        F_VILLAGER_DATA = resolve(Villager.class, IDX_VILLAGER_DATA,
                "DATA_VILLAGER_DATA", "DATA_VILLAGER_DATA_ID");

        // ZombieVillager
        F_ZOMBIE_VILLAGER_CONVERTING = resolve(ZombieVillager.class, IDX_ZOMBIE_VILLAGER_CONVERTING,
                "DATA_CONVERTING_ID", "DATA_CONVERTING", "DATA_IS_CONVERTING");
        F_ZOMBIE_VILLAGER_DATA       = resolve(ZombieVillager.class, IDX_ZOMBIE_VILLAGER_DATA,
                "DATA_VILLAGER_DATA", "DATA_VILLAGER_DATA_ID");

        // Shulker
        F_SHULKER_ATTACH_FACE = resolve(Shulker.class, IDX_SHULKER_ATTACH_FACE,
                "DATA_ATTACH_FACE_ID", "DATA_ATTACH_FACE", "DATA_DIRECTION");
        F_SHULKER_PEEK        = resolve(Shulker.class, IDX_SHULKER_PEEK,
                "DATA_PEEK_ID", "DATA_PEEK", "DATA_OPEN_AMOUNT");
        F_SHULKER_COLOR       = resolve(Shulker.class, IDX_SHULKER_COLOR,
                "DATA_COLOR_ID", "DATA_COLOR", "DATA_SHULKER_COLOR");

        // ItemEntity
        F_ITEM_ENTITY_ITEM = resolve(ItemEntity.class, IDX_ITEM_ENTITY_ITEM,
                "DATA_ITEM", "DATA_ITEM_ID", "DATA_ITEM_STACK");

        // ArmorStand poses
        F_ARMOR_STAND_HEAD_POSE      = resolve(ArmorStand.class, IDX_ARMOR_STAND_HEAD_POSE,
                "DATA_HEAD_POSE", "DATA_POSE_HEAD", "DATA_HEAD_ROTATION");
        F_ARMOR_STAND_BODY_POSE      = resolve(ArmorStand.class, IDX_ARMOR_STAND_BODY_POSE,
                "DATA_BODY_POSE", "DATA_POSE_BODY", "DATA_BODY_ROTATION");
        F_ARMOR_STAND_LEFT_ARM_POSE  = resolve(ArmorStand.class, IDX_ARMOR_STAND_LEFT_ARM_POSE,
                "DATA_LEFT_ARM_POSE", "DATA_POSE_LEFT_ARM", "DATA_LEFT_ARM_ROTATION");
        F_ARMOR_STAND_RIGHT_ARM_POSE = resolve(ArmorStand.class, IDX_ARMOR_STAND_RIGHT_ARM_POSE,
                "DATA_RIGHT_ARM_POSE", "DATA_POSE_RIGHT_ARM", "DATA_RIGHT_ARM_ROTATION");
        F_ARMOR_STAND_LEFT_LEG_POSE  = resolve(ArmorStand.class, IDX_ARMOR_STAND_LEFT_LEG_POSE,
                "DATA_LEFT_LEG_POSE", "DATA_POSE_LEFT_LEG", "DATA_LEFT_LEG_ROTATION");
        F_ARMOR_STAND_RIGHT_LEG_POSE = resolve(ArmorStand.class, IDX_ARMOR_STAND_RIGHT_LEG_POSE,
                "DATA_RIGHT_LEG_POSE", "DATA_POSE_RIGHT_LEG", "DATA_RIGHT_LEG_ROTATION");

        // Player skin parts — in MC 1.21.11 DATA_PLAYER_MODE_CUSTOMISATION (Byte) moved to the
        // new Avatar intermediate class (Avatar extends LivingEntity, Player extends Avatar).
        // Force-initialize Avatar so its static EntityDataAccessor fields are non-null before we
        // resolve; otherwise name lookup returns null and the index scan falls through to
        // Player.DATA_PLAYER_ABSORPTION_ID (Float) at index 17, causing a cast crash on encode.
        try {
            Class.forName("net.minecraft.world.entity.player.Avatar", true,
                    net.minecraft.world.entity.player.Player.class.getClassLoader());
        } catch (Exception ignored) {}
        F_PLAYER_SKIN_PARTS = resolve(net.minecraft.world.entity.player.Player.class,
                IDX_PLAYER_SKIN_PARTS,
                "DATA_PLAYER_MODE_CUSTOMISATION", "DATA_SKIN_PARTS", "DATA_PLAYER_SKIN_CUSTOMISATION");
        if (F_PLAYER_SKIN_PARTS == null) {
            F_PLAYER_SKIN_PARTS = resolve(net.minecraft.server.level.ServerPlayer.class,
                    IDX_PLAYER_SKIN_PARTS,
                    "DATA_PLAYER_MODE_CUSTOMISATION", "DATA_SKIN_PARTS", "DATA_PLAYER_SKIN_CUSTOMISATION");
        }

        // AbstractMinecart — custom display block fields.
        // In MC 1.21.2+ the minecart was reworked; the display-block DataTracker fields
        // moved and the class hierarchy changed.  Try all known names across versions.
        Class<?> minecartClass = loadClass(
                "net.minecraft.world.entity.vehicle.AbstractMinecart",       // 1.21.1 and below / Mojang
                "net.minecraft.world.entity.vehicle.AbstractMinecartLike",   // 1.21.2+ Mojang (post-rework base)
                "net.minecraft.world.entity.vehicle.MinecartLike",           // alternate name
                "net.minecraft.world.entity.vehicle.Minecart"                // plain Minecart (some mappings)
        );
        if (minecartClass != null) {
            F_MINECART_DISPLAY_BLOCK  = resolve(minecartClass, IDX_MINECART_DISPLAY_BLOCK,
                    "DATA_ID_DISPLAY_BLOCK", "DISPLAY_BLOCK_STATE_ID", "DATA_DISPLAY_BLOCK_STATE_ID");
            F_MINECART_DISPLAY_OFFSET = resolve(minecartClass, IDX_MINECART_DISPLAY_OFFSET,
                    "DATA_ID_DISPLAY_OFFSET", "DISPLAY_OFFSET_ID", "DATA_DISPLAY_OFFSET_ID");
            F_MINECART_CUSTOM_DISPLAY = resolve(minecartClass, IDX_MINECART_CUSTOM_DISPLAY,
                    "DATA_ID_CUSTOM_DISPLAY", "CUSTOM_DISPLAY_ID", "DATA_CUSTOM_DISPLAY_ID");
        } else {
            com.coffee.disguises.DisguisesMod.LOGGER.warn("[MetadataBuilder] AbstractMinecart class not found — minecart block display will not work.");
        }

        // BlockDisplay — use a class literal so Loom remaps it correctly in production.
        // (String-based loadClass is NOT remapped by Loom and fails on a Fabric server.)
        Class<?> blockDisplayClass = net.minecraft.world.entity.Display.BlockDisplay.class;
        if (blockDisplayClass != null) {
            // CRITICAL: Force the Display base class to fully initialize so its static
            // initializer runs and registers VECTOR3 / QUATERNION into the
            // EntityDataSerializers IdMapper.  Without this, sending a DataValue with a
            // Vector3f value crashes the encoder with:
            //   "Can't find id for '10' in map IdMapper"
            // because the serializer is never added to the map until Display.<clinit> runs.
            Class<?> displayBaseClass = blockDisplayClass.getSuperclass();
            if (displayBaseClass != null) {
                try {
                    Class.forName(displayBaseClass.getName(), true, displayBaseClass.getClassLoader());
                    com.coffee.disguises.DisguisesMod.LOGGER.debug(
                            "[MetadataBuilder] Force-initialized Display base '{}' to register VECTOR3/QUATERNION serializers.",
                            displayBaseClass.getName());
                } catch (Exception e) {
                    com.coffee.disguises.DisguisesMod.LOGGER.warn(
                            "[MetadataBuilder] Could not force-initialize Display base class: {}", e.getMessage());
                }
            }

            F_BLOCK_DISPLAY_BLOCK_STATE = resolve(blockDisplayClass, IDX_BLOCK_DISPLAY_BLOCK_STATE,
                    "DATA_BLOCK_STATE_ID", "BLOCK_STATE_ID", "DATA_BLOCK_STATE");
            // Resolve scale from Display base; fall back to scanning BlockDisplay itself.
            if (displayBaseClass != null) {
                F_DISPLAY_SCALE = resolve(displayBaseClass, IDX_DISPLAY_SCALE,
                        "DATA_SCALE_ID", "DATA_SCALE", "SCALE_ID");
            }
            if (F_DISPLAY_SCALE == null) {
                F_DISPLAY_SCALE = resolve(blockDisplayClass, IDX_DISPLAY_SCALE,
                        "DATA_SCALE_ID", "DATA_SCALE", "SCALE_ID");
            }
        }

        // Summary
        logResolution("Entity flags",          F_ENTITY_FLAGS);
        logResolution("Entity pose",           F_ENTITY_POSE);
        logResolution("Entity customName",     F_ENTITY_CUSTOM_NAME);
        logResolution("LivingEntity flags",    F_LIVING_FLAGS);
        logResolution("LivingEntity health",   F_LIVING_HEALTH);
        logResolution("AgeableMob baby",       F_AGEABLE_BABY);
        logResolution("Sheep color",           F_SHEEP_COLOR);
        logResolution("Creeper swellDir",      F_CREEPER_SWELL_DIR);
        logResolution("Creeper powered",       F_CREEPER_POWERED);
        logResolution("Slime size",            F_SLIME_SIZE);
        logResolution("Wolf collarColor",      F_WOLF_COLLAR_COLOR);
        logResolution("Wolf angerTime",        F_WOLF_ANGER_TIME);
        logResolution("Enderman creepy",       F_ENDERMAN_CREEPY);
        logResolution("Player skinParts",      F_PLAYER_SKIN_PARTS);
        logResolution("Minecart displayBlock",  F_MINECART_DISPLAY_BLOCK);
        logResolution("Minecart displayOffset", F_MINECART_DISPLAY_OFFSET);
        logResolution("Minecart customDisplay", F_MINECART_CUSTOM_DISPLAY);
        logResolution("BlockDisplay blockState",F_BLOCK_DISPLAY_BLOCK_STATE);
        logResolution("Display scale",          F_DISPLAY_SCALE);
        logResolution("Phantom size",           F_PHANTOM_SIZE);
        logResolution("Goat screaming",         F_GOAT_SCREAMING);
        logResolution("Goat leftHorn",          F_GOAT_LEFT_HORN);
        logResolution("Goat rightHorn",         F_GOAT_RIGHT_HORN);
        logResolution("Bee angerTime",          F_BEE_ANGER_TIME);
        logResolution("Bee flags",              F_BEE_FLAGS);
        logResolution("Strider shaking",        F_STRIDER_SHAKING);
        logResolution("Strider saddle",         F_STRIDER_SADDLE);
        logResolution("Piglin baby",            F_PIGLIN_BABY);
        logResolution("Piglin chargingCrossbow",F_PIGLIN_CHARGING);
        logResolution("Piglin dancing",         F_PIGLIN_DANCING);
        logResolution("Piglin celebrating",     F_PIGLIN_CELEBRATING);
        logResolution("ArmorStand flags",       F_ARMOR_STAND_FLAGS);
        logResolution("Parrot variant",         F_PARROT_VARIANT);
        logResolution("TropicalFish variant",   F_TROPICAL_FISH_VARIANT);
        logResolution("Rabbit type",            F_RABBIT_TYPE);
        logResolution("Fox type",               F_FOX_TYPE);
        logResolution("Fox flags",              F_FOX_FLAGS);
        logResolution("Panda mainGene",         F_PANDA_MAIN_GENE);
        logResolution("Panda hiddenGene",       F_PANDA_HIDDEN_GENE);
        logResolution("Axolotl variant",        F_AXOLOTL_VARIANT);
        logResolution("Axolotl playingDead",    F_AXOLOTL_PLAYING_DEAD);
        logResolution("AbstractHorse flags",    F_ABSTRACT_HORSE_FLAGS);
        logResolution("Horse variant",          F_HORSE_VARIANT);
        logResolution("Llama chest",            F_ABSTRACT_CHESTED_HORSE_CHEST);
        logResolution("Llama strength",         F_LLAMA_STRENGTH);
        logResolution("Llama carpetColor",      F_LLAMA_CARPET_COLOR);
        logResolution("Llama variant",          F_LLAMA_VARIANT);
        logResolution("Cat variant",            F_CAT_VARIANT);
        logResolution("Cat lying",              F_CAT_LYING);
        logResolution("Cat relaxed",            F_CAT_RELAXED);
        logResolution("Cat collarColor",        F_CAT_COLLAR_COLOR);
        logResolution("Frog variant",           F_FROG_VARIANT);
        logResolution("Mooshroom variant",      F_MOOSHROOM_VARIANT);
        logResolution("Villager data",          F_VILLAGER_DATA);
        logResolution("ZombieVillager converting", F_ZOMBIE_VILLAGER_CONVERTING);
        logResolution("ZombieVillager data",    F_ZOMBIE_VILLAGER_DATA);
        logResolution("Shulker attachFace",     F_SHULKER_ATTACH_FACE);
        logResolution("Shulker peek",           F_SHULKER_PEEK);
        logResolution("Shulker color",          F_SHULKER_COLOR);
        logResolution("ItemEntity item",        F_ITEM_ENTITY_ITEM);
        logResolution("ArmorStand headPose",    F_ARMOR_STAND_HEAD_POSE);
        logResolution("ArmorStand bodyPose",    F_ARMOR_STAND_BODY_POSE);
        logResolution("ArmorStand leftArmPose", F_ARMOR_STAND_LEFT_ARM_POSE);
        logResolution("ArmorStand rightArmPose",F_ARMOR_STAND_RIGHT_ARM_POSE);
        logResolution("ArmorStand leftLegPose", F_ARMOR_STAND_LEFT_LEG_POSE);
        logResolution("ArmorStand rightLegPose",F_ARMOR_STAND_RIGHT_LEG_POSE);

        // Resolve minecart fields now, using EntityType.MINECART to get the right class.
        initMinecartFields();

        initialized = true;
    }

    /**
     * Resolves minecart display-block DataTracker fields.
     * Gets the entity class directly from EntityType.MINECART (no live entity needed),
     * then uses NAME-ONLY resolution — never index scan.
     *
     * Index scan is intentionally disabled here because indices 11–13 exist on
     * LivingEntity (effect-ambience, arrow-count, stinger-count) and would be
     * matched on any entity class, producing completely wrong accessor types.
     */
    private static synchronized void initMinecartFields() {
        if (minecartInitialized) return;

        // Get the entity class from EntityType.MINECART via reflection.
        // EntityType stores its entity factory; we create a temporary instance
        // using a null level just to get the concrete class — we only need the class
        // object itself, not a functioning entity.
        Class<?> minecartEntityClass = null;
        try {
            // create(Level, EntitySpawnReason) — pass null level and COMMAND reason.
            // We only need the class object; the entity is discarded immediately.
            // Wrapped in try/catch in case null-level causes an NPE inside the factory.
            net.minecraft.world.entity.Entity tmp =
                    net.minecraft.world.entity.EntityType.MINECART.create(
                            (net.minecraft.world.level.Level) null,
                            net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (tmp != null) {
                minecartEntityClass = tmp.getClass();
                com.coffee.disguises.DisguisesMod.LOGGER.debug(
                        "[MetadataBuilder] Got minecart class '{}' from EntityType.MINECART.create()",
                        minecartEntityClass.getName());
            }
        } catch (Exception e) {
            com.coffee.disguises.DisguisesMod.LOGGER.debug(
                    "[MetadataBuilder] EntityType.MINECART.create(null, COMMAND) threw {}: {} — trying class name fallback",
                    e.getClass().getSimpleName(), e.getMessage());
        }

        // If create() failed, fall back to known Mojang class names.
        if (minecartEntityClass == null) {
            minecartEntityClass = loadClass(
                    "net.minecraft.world.entity.vehicle.Minecart",
                    "net.minecraft.world.entity.vehicle.AbstractMinecart",
                    "net.minecraft.world.entity.vehicle.AbstractMinecartLike"
            );
        }

        if (minecartEntityClass == null) {
            com.coffee.disguises.DisguisesMod.LOGGER.warn(
                    "[MetadataBuilder] Could not determine minecart entity class — " +
                            "custom display blocks in minecarts will not work.");
            minecartInitialized = true;
            return;
        }

        com.coffee.disguises.DisguisesMod.LOGGER.info(
                "[MetadataBuilder] Resolving minecart display fields from class '{}'",
                minecartEntityClass.getName());

        // Name-only resolution — never fall back to index scan.
        // The indices 11–13 collide with LivingEntity fields; scanning would return
        // the wrong accessor (e.g. DATA_EFFECT_AMBIENCE_ID, DATA_ARROW_COUNT_ID).
        Class<?> c = minecartEntityClass;
        while (c != null && c != Object.class) {
            if (F_MINECART_DISPLAY_BLOCK == null)
                F_MINECART_DISPLAY_BLOCK = resolveByNameOnly(c,
                        "DATA_ID_DISPLAY_BLOCK", "DISPLAY_BLOCK_STATE_ID", "DATA_DISPLAY_BLOCK_STATE_ID");
            if (F_MINECART_DISPLAY_OFFSET == null)
                F_MINECART_DISPLAY_OFFSET = resolveByNameOnly(c,
                        "DATA_ID_DISPLAY_OFFSET", "DISPLAY_OFFSET_ID", "DATA_DISPLAY_OFFSET_ID");
            if (F_MINECART_CUSTOM_DISPLAY == null)
                F_MINECART_CUSTOM_DISPLAY = resolveByNameOnly(c,
                        "DATA_ID_CUSTOM_DISPLAY", "CUSTOM_DISPLAY_ID", "DATA_CUSTOM_DISPLAY_ID");
            if (F_MINECART_DISPLAY_BLOCK != null
                    && F_MINECART_DISPLAY_OFFSET != null
                    && F_MINECART_CUSTOM_DISPLAY != null) break;
            c = c.getSuperclass();
        }

        logResolution("Minecart displayBlock",  F_MINECART_DISPLAY_BLOCK);
        logResolution("Minecart displayOffset", F_MINECART_DISPLAY_OFFSET);
        logResolution("Minecart customDisplay", F_MINECART_CUSTOM_DISPLAY);

        if (F_MINECART_DISPLAY_BLOCK == null) {
            com.coffee.disguises.DisguisesMod.LOGGER.warn(
                    "[MetadataBuilder] Minecart display block fields not found — " +
                            "the custom display tile feature may have been removed in this MC version.");
        }
        minecartInitialized = true;
    }

    // =========================================================================
    // Build
    // =========================================================================

    public static List<SynchedEntityData.DataValue<?>> build(FlagWatcher watcher) {
        return build(watcher, null);
    }

    public static List<SynchedEntityData.DataValue<?>> build(
            FlagWatcher watcher,
            com.coffee.disguises.disguise.DisguiseType type) {
        return build(watcher, type, null);
    }

    /**
     * Entity parameter used to obtain RegistryAccess for dynamic-registry lookups
     * (cat/frog/mooshroom variants). May be null; those entries are silently skipped.
     */
    public static List<SynchedEntityData.DataValue<?>> build(
            FlagWatcher watcher,
            com.coffee.disguises.disguise.DisguiseType type,
            net.minecraft.world.entity.Entity entity) {
        RegistryAccess registryAccess = (entity != null) ? entity.level().registryAccess() : null;

        if (!initialized) init();

        List<SynchedEntityData.DataValue<?>> list = new ArrayList<>();

        // ── Entity base ───────────────────────────────────────────────────────

        putByte(list, F_ENTITY_FLAGS, watcher.buildSharedFlags());

        if (watcher.getCustomName() != null) {
            putOptComponent(list, F_ENTITY_CUSTOM_NAME,
                    Optional.of(Component.literal(watcher.getCustomName())));
            putBool(list, F_ENTITY_CUSTOM_NAME_VISIBLE, watcher.isCustomNameVisible());
        }
        if (watcher.isSilent())    putBool(list, F_ENTITY_SILENT,     true);
        if (watcher.isNoGravity()) putBool(list, F_ENTITY_NO_GRAVITY, true);

        // ── LivingEntity ──────────────────────────────────────────────────────

        if (watcher instanceof LivingEntityWatcher lew) {
            // Living-entity flags byte: bit 0 = IS_HAND_ACTIVE, bit 1 = ACTIVE_HAND, bit 2 = SPIN_ATTACK
            // Default 0 = no active hand (idle pose). The client updates this via ongoing DA syncs.
            putByte(list, F_LIVING_FLAGS, (byte) 0);
            putFloat(list, F_LIVING_HEALTH, lew.getHealth());

            if (lew.getArrowCount()   > 0) putInt(list, F_LIVING_ARROW_COUNT,   lew.getArrowCount());
            if (lew.getStingerCount() > 0) putInt(list, F_LIVING_STINGER_COUNT, lew.getStingerCount());
        }

        // ── AgeableMob ────────────────────────────────────────────────────────

        if (watcher instanceof AgeableWatcher aw) {
            putBool(list, F_AGEABLE_BABY, aw.isBaby());
        }

        // ── Sheep ─────────────────────────────────────────────────────────────

        if (watcher instanceof SheepWatcher sw) {
            putByte(list, F_SHEEP_COLOR, sw.getSheepFlags());
        }

        // ── Creeper ───────────────────────────────────────────────────────────

        if (watcher instanceof CreeperWatcher cw) {
            putInt(list,  F_CREEPER_SWELL_DIR, -1);         // -1 = idle
            putBool(list, F_CREEPER_POWERED,   cw.isPowered());
            putBool(list, F_CREEPER_IGNITED,   cw.isIgnited());
        }

        // ── Slime / Magma Cube ────────────────────────────────────────────────

        if (watcher instanceof SlimeWatcher slw) {
            putInt(list, F_SLIME_SIZE, slw.getSize());
        }

        // ── Wolf ──────────────────────────────────────────────────────────────

        if (watcher instanceof WolfWatcher ww) {
            putByte(list, F_TAMEABLE_FLAGS,    ww.getTameableFlags());
            putInt(list,  F_WOLF_COLLAR_COLOR, ww.getCollarColor().getId());
            // DATA_ANGER_END_TIME changed from int (ticks-remaining) to long (game-tick timestamp)
            // in 1.21.11.  Use Long.MAX_VALUE as "angry forever", -1L as "not angry" (vanilla default).
            putLong(list, F_WOLF_ANGER_TIME,   ww.isAngry() ? Long.MAX_VALUE : -1L);
        }

        // ── Enderman ──────────────────────────────────────────────────────────

        if (watcher instanceof EndermanWatcher ew) {
            putBool(list, F_ENDERMAN_CREEPY, ew.isScreaming());
        }

        // ── Phantom ───────────────────────────────────────────────────────────

        if (watcher instanceof PhantomWatcher phw) {
            putInt(list, F_PHANTOM_SIZE, phw.getSize());
        }

        // ── Goat ──────────────────────────────────────────────────────────────

        if (watcher instanceof GoatWatcher gw) {
            putBool(list, F_GOAT_SCREAMING,  gw.isScreaming());
            putBool(list, F_GOAT_LEFT_HORN,  gw.hasLeftHorn());
            putBool(list, F_GOAT_RIGHT_HORN, gw.hasRightHorn());
        }

        // ── Bee ───────────────────────────────────────────────────────────────

        if (watcher instanceof BeeWatcher bw) {
            putInt(list,  F_BEE_ANGER_TIME, bw.getRemainingAngerTime());
            putByte(list, F_BEE_FLAGS,      bw.getBeeFlags());
        }

        // ── Strider ───────────────────────────────────────────────────────────

        if (watcher instanceof StriderWatcher strw) {
            putBool(list, F_STRIDER_SHAKING, strw.isShaking());
            putBool(list, F_STRIDER_SADDLE,  strw.isSaddled());
        }

        // ── Piglin ────────────────────────────────────────────────────────────

        if (watcher instanceof PiglinWatcher piw) {
            putBool(list, F_PIGLIN_BABY,        piw.isBabyPiglin());
            putBool(list, F_PIGLIN_CHARGING,    piw.isChargingCrossbow());
            putBool(list, F_PIGLIN_DANCING,     piw.isDancing());
            putBool(list, F_PIGLIN_CELEBRATING, piw.isCelebrating());
        }

        // ── ArmorStand ────────────────────────────────────────────────────────

        if (watcher instanceof ArmorStandWatcher asw) {
            putByte(list, F_ARMOR_STAND_FLAGS, asw.getArmorStandFlags());
            putRotations(list, F_ARMOR_STAND_HEAD_POSE,      asw.getHeadPose());
            putRotations(list, F_ARMOR_STAND_BODY_POSE,      asw.getBodyPose());
            putRotations(list, F_ARMOR_STAND_LEFT_ARM_POSE,  asw.getLeftArmPose());
            putRotations(list, F_ARMOR_STAND_RIGHT_ARM_POSE, asw.getRightArmPose());
            putRotations(list, F_ARMOR_STAND_LEFT_LEG_POSE,  asw.getLeftLegPose());
            putRotations(list, F_ARMOR_STAND_RIGHT_LEG_POSE, asw.getRightLegPose());
        }

        // ── Parrot ────────────────────────────────────────────────────────────

        if (watcher instanceof ParrotWatcher parw) {
            putInt(list, F_PARROT_VARIANT, parw.getVariant());
        }

        // ── TropicalFish ──────────────────────────────────────────────────────

        if (watcher instanceof TropicalFishWatcher tfw) {
            putInt(list, F_TROPICAL_FISH_VARIANT, tfw.getPackedData());
        }

        // ── Rabbit ────────────────────────────────────────────────────────────

        if (watcher instanceof RabbitWatcher rw) {
            putInt(list, F_RABBIT_TYPE, rw.getType());
        }

        // ── Fox ───────────────────────────────────────────────────────────────

        if (watcher instanceof FoxWatcher fw) {
            putInt(list,  F_FOX_TYPE,  fw.getType());
            putByte(list, F_FOX_FLAGS, fw.getFoxFlags());
        }

        // ── Panda ─────────────────────────────────────────────────────────────

        if (watcher instanceof PandaWatcher pdw) {
            // Gene serializer type changed across MC versions: BYTE in some builds, INT in others.
            // putIntOrByte() inspects the resolved accessor's serializer at runtime.
            putIntOrByte(list, F_PANDA_MAIN_GENE,   pdw.getMainGene());
            putIntOrByte(list, F_PANDA_HIDDEN_GENE, pdw.getHiddenGene());
        }

        // ── Axolotl ───────────────────────────────────────────────────────────

        if (watcher instanceof AxolotlWatcher axw) {
            putInt(list,  F_AXOLOTL_VARIANT,      axw.getVariant());
            putBool(list, F_AXOLOTL_PLAYING_DEAD, axw.isPlayingDead());
        }

        // ── Horse ─────────────────────────────────────────────────────────────

        if (watcher instanceof HorseWatcher horsew) {
            putByte(list, F_ABSTRACT_HORSE_FLAGS, horsew.getHorseFlags());
            putInt(list,  F_HORSE_VARIANT,        horsew.getVariant());
        }

        // ── Llama (and TraderLlama, which extends Llama) ──────────────────────

        if (watcher instanceof LlamaWatcher llamaw) {
            putBool(list, F_ABSTRACT_CHESTED_HORSE_CHEST, llamaw.hasChest());
            putInt(list,  F_LLAMA_STRENGTH,               llamaw.getStrength());
            putInt(list,  F_LLAMA_CARPET_COLOR,           llamaw.getCarpetColor());
            putInt(list,  F_LLAMA_VARIANT,                llamaw.getVariant());
        }

        // ── Cat ───────────────────────────────────────────────────────────────

        if (watcher instanceof CatWatcher catw) {
            putByte(list, F_TAMEABLE_FLAGS,      catw.getTameableFlags());
            putHolderFromRegistry(list, F_CAT_VARIANT, registryAccess, Registries.CAT_VARIANT, catw.getVariant());
            putBool(list, F_CAT_LYING,           catw.isLying());
            putBool(list, F_CAT_RELAXED,         catw.isRelaxed());
            putInt(list,  F_CAT_COLLAR_COLOR,    catw.getCollarColor().getId());
        }

        // ── Frog ──────────────────────────────────────────────────────────────

        if (watcher instanceof FrogWatcher frogw) {
            putHolderFromRegistry(list, F_FROG_VARIANT, registryAccess, Registries.FROG_VARIANT, frogw.getVariant());
        }

        // ── Mooshroom ─────────────────────────────────────────────────────────

        if (watcher instanceof MooshroomWatcher moow) {
            putHolderFromRegistry(list, F_MOOSHROOM_VARIANT, registryAccess, Registries.COW_VARIANT, moow.getVariant());
        }

        // ── Villager ──────────────────────────────────────────────────────────

        if (watcher instanceof VillagerWatcher vw) {
            putVillagerData(list, F_VILLAGER_DATA, vw.getType(), vw.getProfession(), vw.getLevel());
        }

        // ── ZombieVillager ────────────────────────────────────────────────────

        if (watcher instanceof ZombieVillagerWatcher zvw) {
            putBool(list, F_ZOMBIE_VILLAGER_CONVERTING, zvw.isConverting());
            putVillagerData(list, F_ZOMBIE_VILLAGER_DATA, zvw.getType(), zvw.getProfession(), zvw.getLevel());
        }

        // ── Shulker ───────────────────────────────────────────────────────────

        if (watcher instanceof ShulkerWatcher shuw) {
            putDirection(list, F_SHULKER_ATTACH_FACE, shuw.getAttachFace());
            putByte(list,      F_SHULKER_PEEK,        shuw.getPeek());
            putByte(list,      F_SHULKER_COLOR,       shuw.getColor());
        }

        // ── ItemEntity ────────────────────────────────────────────────────────

        if (watcher instanceof ItemWatcher itw) {
            putItemStack(list, F_ITEM_ENTITY_ITEM, itw.getItem());
        }

        // ── Player skin-layer customisation ───────────────────────────────────
        // 0x7F = all outer layers visible (cape, jacket, sleeves, pants, hat)

        if (type == com.coffee.disguises.disguise.DisguiseType.PLAYER && F_PLAYER_SKIN_PARTS != null) {
            // Guard: verify the resolved accessor actually uses the BYTE serializer before emitting.
            // In 1.21.11 the Avatar class introduced an index shift; if resolution picked the wrong
            // field (e.g. DATA_PLAYER_ABSORPTION_ID which is a Float at index 17), encoding a Byte
            // value through a Float serializer causes a ClassCastException in Netty.
            try {
                EntityDataAccessor<?> skinAcc = (EntityDataAccessor<?>) F_PLAYER_SKIN_PARTS.get(null);
                if (skinAcc != null && skinAcc.serializer() == EntityDataSerializers.BYTE) {
                    putByte(list, F_PLAYER_SKIN_PARTS, (byte) 0x7F);
                } else {
                    com.coffee.disguises.DisguisesMod.LOGGER.warn(
                            "[MetadataBuilder] Skipping skin-parts byte: resolved accessor at index {} has unexpected serializer {}",
                            skinAcc != null ? skinAcc.id() : -1,
                            skinAcc != null ? skinAcc.serializer() : "null");
                }
            } catch (Exception e) {
                com.coffee.disguises.DisguisesMod.LOGGER.warn("[MetadataBuilder] Skin-parts guard failed: {}", e.getMessage());
            }
        }

        // ── Minecart custom display block ─────────────────────────────────────
        // Only emitted when the watcher has explicitly requested a custom block.

        if (watcher instanceof MinecartWatcher mw && mw.isUseCustomDisplay()) {
            putInt(list,  F_MINECART_DISPLAY_BLOCK,   mw.getBlockId());
            putInt(list,  F_MINECART_DISPLAY_OFFSET,  mw.getDisplayOffset());
            putBool(list, F_MINECART_CUSTOM_DISPLAY,  true);
        }

        // ── BlockDisplay block state ───────────────────────────────────────────
        // DATA_BLOCK_STATE_ID uses EntityDataSerializers.BLOCK_STATE — the accessor
        // type is EntityDataAccessor<BlockState>, NOT <Integer>.  Passing getBlockId()
        // (an int) would cause a type-mismatch crash in the Netty encoder.

        if (watcher instanceof BlockDisplayWatcher bdw) {
            // Scale (1,1,1) is mandatory — the vanilla default is (0,0,0) which
            // makes the entity invisible.  Must be sent before or alongside the
            // block state so the client renders a full-size block.
            putVector3f(list, F_DISPLAY_SCALE, 1f, 1f, 1f);
            putBlockState(list, F_BLOCK_DISPLAY_BLOCK_STATE, bdw.getBlockState());
        }

        return list;
    }

    /**
     * Builds a minimal DataValue list containing just the shared entity-flags byte and pose.
     * Used by the self-view puppet sync to propagate crouching, sprinting, swimming, and
     * elytra state from the real player to the puppet every tick.
     */
    public static List<SynchedEntityData.DataValue<?>> buildEntityStateUpdate(
            byte entityFlags, net.minecraft.world.entity.Pose pose) {
        if (!initialized) init();
        List<SynchedEntityData.DataValue<?>> list = new ArrayList<>();
        putByte(list, F_ENTITY_FLAGS, entityFlags);
        putPose(list, F_ENTITY_POSE, pose);
        return list;
    }

    /**
     * Builds a DataValue list that assigns a hidden custom name to an entity.
     *
     * The name is written into DATA_CUSTOM_NAME (index 2) but DATA_CUSTOM_NAME_VISIBLE
     * (index 3) is set to {@code false}, so it is never rendered in-game. Its sole
     * purpose is to give the entity a scoreboard-visible name so that the client's
     * {@code Entity.getScoreboardName()} returns this string, enabling team membership
     * for the collision-suppression team sent alongside self-view puppets.
     *
     * Returns an empty list if the required accessor fields could not be resolved.
     */
    public static List<SynchedEntityData.DataValue<?>> buildHiddenName(String name) {
        if (!initialized) init();
        List<SynchedEntityData.DataValue<?>> list = new ArrayList<>();
        putOptComponent(list, F_ENTITY_CUSTOM_NAME, Optional.of(Component.literal(name)));
        putBool(list, F_ENTITY_CUSTOM_NAME_VISIBLE, false);
        return list;
    }

    // =========================================================================
    // Field resolution
    // =========================================================================

    /**
     * Resolves an EntityDataAccessor field by trying candidate names first,
     * then falling back to a scan by expected DataTracker index.
     *
     * @param clazz      the class that declares the accessor field
     * @param expectIdx  expected getId() value of the accessor (index fallback)
     * @param names      candidate field names to try (in preference order)
     */
    private static Field resolve(Class<?> clazz, int expectIdx, String... names) {
        if (clazz == null) return null;

        // Pass 1: try known names
        for (String name : names) {
            try {
                Field f = findFieldInHierarchy(clazz, name);
                if (f != null) {
                    f.setAccessible(true);
                    // Sanity check: verify it's an EntityDataAccessor
                    Object val = f.get(null);
                    if (val instanceof EntityDataAccessor<?> acc) {
                        com.coffee.disguises.DisguisesMod.LOGGER.debug(
                                "[MetadataBuilder] {} resolved by name '{}' → index {}",
                                clazz.getSimpleName(), name, acc.id());
                        return f;
                    }
                }
            } catch (Exception ignored) {}
        }

        // Pass 2: scan all static EntityDataAccessor fields on the class hierarchy
        // looking for one whose accessor.id() == expectIdx
        Field found = scanByIndex(clazz, expectIdx);
        if (found != null) {
            com.coffee.disguises.DisguisesMod.LOGGER.info(
                    "[MetadataBuilder] {} index={} resolved by scan (field '{}') — name candidates {} all failed",
                    clazz.getSimpleName(), expectIdx, found.getName(), Arrays.toString(names));
            return found;
        }

        com.coffee.disguises.DisguisesMod.LOGGER.warn(
                "[MetadataBuilder] FAILED to resolve {}.* at index {} (tried names: {}). "
                        + "Flags using this field will be silently ignored. "
                        + "Run with -Dcom.coffee.disguises.dumpDA=true to dump all accessible EntityDataAccessor fields.",
                clazz.getSimpleName(), expectIdx, Arrays.toString(names));

        // Diagnostic dump if requested
        if (Boolean.getBoolean("com.coffee.disguises.dumpDA")) {
            dumpAccessors(clazz);
        }

        return null;
    }

    /**
     * Name-only resolution — never falls back to index scan.
     * Used for fields where the index is shared with unrelated fields on parent classes
     * (e.g. minecart display-block indices 11–13 collide with LivingEntity fields), so
     * a scan hit on the wrong class would return a completely wrong accessor.
     */
    private static Field resolveByNameOnly(Class<?> clazz, String... names) {
        if (clazz == null) return null;
        for (String name : names) {
            try {
                Field f = findFieldInHierarchy(clazz, name);
                if (f != null) {
                    f.setAccessible(true);
                    Object val = f.get(null);
                    if (val instanceof EntityDataAccessor<?> acc) {
                        com.coffee.disguises.DisguisesMod.LOGGER.debug(
                                "[MetadataBuilder] {} resolved by name '{}' → index {}",
                                clazz.getSimpleName(), name, acc.id());
                        return f;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Walks the class hierarchy looking for a field with the given name. */
    private static Field findFieldInHierarchy(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {}
            c = c.getSuperclass();
        }
        return null;
    }

    /**
     * Scans all static EntityDataAccessor fields accessible from {@code clazz}
     * (declared fields only, not inherited — we walk the hierarchy explicitly)
     * and returns the first one whose getId() matches {@code expectIdx}.
     */
    @SuppressWarnings("unchecked")
    private static Field scanByIndex(Class<?> clazz, int expectIdx) {
        // Build a list of all classes in the hierarchy from clazz up to Entity
        List<Class<?>> hierarchy = new ArrayList<>();
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            hierarchy.add(c);
            c = c.getSuperclass();
        }

        for (Class<?> cls : hierarchy) {
            for (Field f : cls.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                if (!EntityDataAccessor.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object val = f.get(null);
                    if (val instanceof EntityDataAccessor<?> acc && acc.id() == expectIdx) {
                        return f;
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static Class<?> loadClass(String... candidates) {
        for (String name : candidates) {
            try { return Class.forName(name); }
            catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    private static void logResolution(String label, Field f) {
        if (f == null) {
            com.coffee.disguises.DisguisesMod.LOGGER.warn(
                    "[MetadataBuilder] ✗ {} — NOT resolved", label);
        } else {
            try {
                Object acc = f.get(null);
                int idx = acc instanceof EntityDataAccessor<?> a ? a.id() : -1;
                com.coffee.disguises.DisguisesMod.LOGGER.info(
                        "[MetadataBuilder] ✓ {} → {}.{} (index {})",
                        label, f.getDeclaringClass().getSimpleName(), f.getName(), idx);
            } catch (Exception e) {
                com.coffee.disguises.DisguisesMod.LOGGER.info(
                        "[MetadataBuilder] ✓ {} → {}.{}", label,
                        f.getDeclaringClass().getSimpleName(), f.getName());
            }
        }
    }

    private static void dumpAccessors(Class<?> clazz) {
        com.coffee.disguises.DisguisesMod.LOGGER.info(
                "[MetadataBuilder] ── Dumping all EntityDataAccessor fields for {} ──", clazz.getSimpleName());
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                if (!EntityDataAccessor.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object val = f.get(null);
                    int idx = val instanceof EntityDataAccessor<?> a ? a.id() : -1;
                    com.coffee.disguises.DisguisesMod.LOGGER.info(
                            "  {}.{} → index {}", c.getSimpleName(), f.getName(), idx);
                } catch (Exception ignored) {}
            }
            c = c.getSuperclass();
        }
    }

    // =========================================================================
    // DataValue helpers
    // =========================================================================

    @SuppressWarnings("unchecked")
    private static <T> void put(List<SynchedEntityData.DataValue<?>> list,
                                Field accessorField,
                                T value) {
        if (accessorField == null) return;
        try {
            EntityDataAccessor<T> acc = (EntityDataAccessor<T>) accessorField.get(null);
            list.add(SynchedEntityData.DataValue.create(acc, value));
        } catch (Exception e) {
            com.coffee.disguises.DisguisesMod.LOGGER.warn(
                    "[MetadataBuilder] Failed to create DataValue for field '{}': {}",
                    accessorField.getName(), e.getMessage());
        }
    }

    private static void putByte(List<SynchedEntityData.DataValue<?>> list, Field f, byte value) {
        put(list, f, value);
    }
    private static void putByte(List<SynchedEntityData.DataValue<?>> list, Field f, int value) {
        put(list, f, (byte) value);
    }

    /**
     * Writes an integer value that may be serialized as either BYTE or INT depending
     * on the MC version. Inspects the resolved accessor's serializer at runtime and
     * dispatches accordingly, avoiding ClassCastException in the Netty encoder.
     *
     * Used for fields like Panda gene IDs whose serializer type changed across versions:
     *   - some builds use EntityDataSerializers.BYTE  (values 0-6 fit in a byte)
     *   - some builds use EntityDataSerializers.INT   (generic int)
     */
    @SuppressWarnings("unchecked")
    private static void putIntOrByte(List<SynchedEntityData.DataValue<?>> list, Field f, int value) {
        if (f == null) return;
        try {
            EntityDataAccessor<?> acc = (EntityDataAccessor<?>) f.get(null);
            if (acc == null) return;
            if (acc.serializer() == EntityDataSerializers.BYTE) {
                EntityDataAccessor<Byte> byteAcc = (EntityDataAccessor<Byte>) acc;
                list.add(SynchedEntityData.DataValue.create(byteAcc, (byte) value));
            } else {
                EntityDataAccessor<Integer> intAcc = (EntityDataAccessor<Integer>) acc;
                list.add(SynchedEntityData.DataValue.create(intAcc, value));
            }
        } catch (Exception e) {
            com.coffee.disguises.DisguisesMod.LOGGER.warn(
                    "[MetadataBuilder] Failed to create IntOrByte DataValue for '{}': {}",
                    f.getName(), e.getMessage());
        }
    }
    private static void putBool(List<SynchedEntityData.DataValue<?>> list, Field f, boolean value) {
        put(list, f, value);
    }
    private static void putInt(List<SynchedEntityData.DataValue<?>> list, Field f, int value) {
        put(list, f, value);
    }
    private static void putFloat(List<SynchedEntityData.DataValue<?>> list, Field f, float value) {
        put(list, f, value);
    }
    private static void putLong(List<SynchedEntityData.DataValue<?>> list, Field f, long value) {
        put(list, f, value);
    }
    private static void putOptComponent(List<SynchedEntityData.DataValue<?>> list,
                                        Field f, Optional<net.minecraft.network.chat.Component> value) {
        put(list, f, value);
    }
    private static void putBlockState(List<SynchedEntityData.DataValue<?>> list, Field f,
                                      net.minecraft.world.level.block.state.BlockState value) {
        put(list, f, value);
    }
    private static void putPose(List<SynchedEntityData.DataValue<?>> list, Field f,
                                net.minecraft.world.entity.Pose value) {
        put(list, f, value);
    }
    private static void putVector3f(List<SynchedEntityData.DataValue<?>> list, Field f,
                                    float x, float y, float z) {
        if (f == null) return;
        try {
            @SuppressWarnings("unchecked")
            EntityDataAccessor<org.joml.Vector3f> acc =
                    (EntityDataAccessor<org.joml.Vector3f>) f.get(null);
            list.add(SynchedEntityData.DataValue.create(acc, new org.joml.Vector3f(x, y, z)));
        } catch (Exception e) {
            com.coffee.disguises.DisguisesMod.LOGGER.warn(
                    "[MetadataBuilder] Failed to create Vector3f DataValue for field '{}': {}",
                    f.getName(), e.getMessage());
        }
    }

    private static void putRotations(List<SynchedEntityData.DataValue<?>> list, Field f, Rotations rot) {
        put(list, f, rot);
    }

    private static void putDirection(List<SynchedEntityData.DataValue<?>> list, Field f, Direction dir) {
        put(list, f, dir);
    }

    private static void putItemStack(List<SynchedEntityData.DataValue<?>> list, Field f, ItemStack item) {
        put(list, f, item);
    }

    /**
     * Looks up entry {@code rawId} in the dynamic registry identified by {@code key},
     * wraps it as a Holder, and emits it.  If {@code registryAccess} is null (e.g. no
     * entity is available for the call site) the entry is silently skipped.
     */
    @SuppressWarnings("unchecked")
    private static <T> void putHolderFromRegistry(List<SynchedEntityData.DataValue<?>> list, Field f,
                                                   RegistryAccess registryAccess,
                                                   ResourceKey<net.minecraft.core.Registry<T>> key,
                                                   int rawId) {
        if (f == null || registryAccess == null) return;
        try {
            net.minecraft.core.Registry<T> registry = registryAccess.lookupOrThrow(key);
            T value = registry.byId(rawId);
            if (value == null) value = registry.byId(0);
            if (value == null) return;
            Holder<T> holder = registry.wrapAsHolder(value);
            EntityDataAccessor<Holder<T>> acc = (EntityDataAccessor<Holder<T>>) f.get(null);
            list.add(SynchedEntityData.DataValue.create(acc, holder));
        } catch (Exception e) {
            com.coffee.disguises.DisguisesMod.LOGGER.warn(
                    "[MetadataBuilder] Failed to create Holder DataValue for '{}': {}",
                    f != null ? f.getName() : "null", e.getMessage());
        }
    }

    /**
     * Constructs a {@link VillagerData} from registry IDs and emits it.
     */
    @SuppressWarnings("unchecked")
    private static void putVillagerData(List<SynchedEntityData.DataValue<?>> list, Field f,
                                        int typeId, int professionId, int level) {
        if (f == null) return;
        try {
            VillagerType   type = BuiltInRegistries.VILLAGER_TYPE.byId(typeId);
            if (type == null)   type   = BuiltInRegistries.VILLAGER_TYPE.byId(2);       // plains fallback
            VillagerProfession prof = BuiltInRegistries.VILLAGER_PROFESSION.byId(professionId);
            if (prof == null)   prof   = BuiltInRegistries.VILLAGER_PROFESSION.byId(0); // none fallback
            if (type == null || prof == null) return;
            Holder<VillagerType>       typeHolder = BuiltInRegistries.VILLAGER_TYPE.wrapAsHolder(type);
            Holder<VillagerProfession> profHolder = BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(prof);
            EntityDataAccessor<VillagerData> acc = (EntityDataAccessor<VillagerData>) f.get(null);
            list.add(SynchedEntityData.DataValue.create(acc, new VillagerData(typeHolder, profHolder, level)));
        } catch (Exception e) {
            com.coffee.disguises.DisguisesMod.LOGGER.warn(
                    "[MetadataBuilder] Failed to create VillagerData DataValue: {}", e.getMessage());
        }
    }

}