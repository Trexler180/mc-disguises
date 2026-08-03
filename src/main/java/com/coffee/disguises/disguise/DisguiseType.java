package com.coffee.disguises.disguise;

import com.coffee.disguises.watcher.*;
import com.coffee.disguises.watcher.FallingBlockWatcher;
import com.coffee.disguises.watcher.MinecartWatcher;
import com.coffee.disguises.watcher.BlockDisplayWatcher;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Every entity type the disguise system knows about for MC 1.21.11.
 *
 * ── Sounds ────────────────────────────────────────────────────────────────────
 * Each entry stores Supplier<SoundEvent> lambdas that reference SoundEvents.*
 * constants directly. This avoids ResourceLocation entirely — which is
 * inaccessible via Loom's remapping in this MC/mappings version.
 *
 * A null supplier means no substitution (the real entity keeps its own sound).
 * The sound mixins already handle null gracefully.
 *
 * ── Inanimate flag ────────────────────────────────────────────────────────────
 * isInanimate() returns true for non-LivingEntity types (projectiles, boats,
 * minecarts, falling blocks, etc.). These skip metadata and equipment packets.
 * ARMOR_STAND is the exception — it IS a LivingEntity so it's inanimate=false.
 *
 * ── New mobs (comment out if your target jar predates the version shown) ──────
 *   SNIFFER, CAMEL              1.20
 *   ARMADILLO, BOGGED, BREEZE,
 *   WIND_CHARGE, BREEZE_WIND_CHARGE  1.21
 *   CREAKING                    1.21.2
 *   CAMEL_HUSK, HAPPY_GHAST,
 *   NAUTILUS, ZOMBIE_NAUTILUS,
 *   PARCHED, COPPER_GOLEM       1.21.4 – 1.21.11
 */
public enum DisguiseType {

    // =========================================================================
    // LIVING — PASSIVE
    // =========================================================================

    ALLAY("allay", EntityTypes.ALLAY, LivingEntityWatcher.class, false,
            () -> SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM,
            () -> SoundEvents.ALLAY_HURT,
            () -> SoundEvents.ALLAY_DEATH),

    ARMADILLO("armadillo", EntityTypes.ARMADILLO, AgeableWatcher.class, false,
            null,
            () -> SoundEvents.ARMADILLO_HURT,
            () -> SoundEvents.ARMADILLO_DEATH),

    AXOLOTL("axolotl", EntityTypes.AXOLOTL, AxolotlWatcher.class, false,
            () -> SoundEvents.AXOLOTL_IDLE_WATER,
            () -> SoundEvents.AXOLOTL_HURT,
            () -> SoundEvents.AXOLOTL_DEATH),

    BAT("bat", EntityTypes.BAT, LivingEntityWatcher.class, false,
            () -> SoundEvents.BAT_AMBIENT,
            () -> SoundEvents.BAT_HURT,
            () -> SoundEvents.BAT_DEATH),

    BEE("bee", EntityTypes.BEE, BeeWatcher.class, false,
            () -> SoundEvents.BEE_LOOP,
            () -> SoundEvents.BEE_HURT,
            () -> SoundEvents.BEE_DEATH),

    CAMEL("camel", EntityTypes.CAMEL, AgeableWatcher.class, false,
            () -> SoundEvents.CAMEL_AMBIENT,
            () -> SoundEvents.CAMEL_HURT,
            () -> SoundEvents.CAMEL_DEATH),

    // 1.21.4+ — comment out if targeting older 1.21.x
    CAMEL_HUSK("camel_husk", EntityTypes.CAMEL_HUSK, AgeableWatcher.class, false,
            () -> SoundEvents.CAMEL_AMBIENT,
            () -> SoundEvents.CAMEL_HURT,
            () -> SoundEvents.CAMEL_DEATH),

    CAT("cat", EntityTypes.CAT, CatWatcher.class, false,
            DisguiseType::catAmbientSound,
            DisguiseType::catHurtSound,
            DisguiseType::catDeathSound),

    CHICKEN("chicken", EntityTypes.CHICKEN, AgeableWatcher.class, false,
            DisguiseType::chickenAmbientSound,
            DisguiseType::chickenHurtSound,
            DisguiseType::chickenDeathSound),

    COD("cod", EntityTypes.COD, LivingEntityWatcher.class, false,
            null,
            () -> SoundEvents.COD_HURT,
            () -> SoundEvents.COD_DEATH),

    // 1.21.5+ — comment out if targeting older releases
    COPPER_GOLEM("copper_golem", EntityTypes.COPPER_GOLEM, LivingEntityWatcher.class, false,
            null, null, null),

    COW("cow", EntityTypes.COW, AgeableWatcher.class, false,
            DisguiseType::cowAmbientSound,
            DisguiseType::cowHurtSound,
            DisguiseType::cowDeathSound),

    DOLPHIN("dolphin", EntityTypes.DOLPHIN, LivingEntityWatcher.class, false,
            () -> SoundEvents.DOLPHIN_AMBIENT,
            () -> SoundEvents.DOLPHIN_HURT,
            () -> SoundEvents.DOLPHIN_DEATH),

    DONKEY("donkey", EntityTypes.DONKEY, AgeableWatcher.class, false,
            () -> SoundEvents.DONKEY_AMBIENT,
            () -> SoundEvents.DONKEY_HURT,
            () -> SoundEvents.DONKEY_DEATH),

    ENDER_DRAGON("ender_dragon", EntityTypes.ENDER_DRAGON, LivingEntityWatcher.class, false,
            () -> SoundEvents.ENDER_DRAGON_GROWL,
            () -> SoundEvents.ENDER_DRAGON_HURT,
            () -> SoundEvents.ENDER_DRAGON_DEATH),

    FOX("fox", EntityTypes.FOX, FoxWatcher.class, false,
            () -> SoundEvents.FOX_AMBIENT,
            () -> SoundEvents.FOX_HURT,
            () -> SoundEvents.FOX_DEATH),

    FROG("frog", EntityTypes.FROG, FrogWatcher.class, false,
            () -> SoundEvents.FROG_AMBIENT,
            () -> SoundEvents.FROG_HURT,
            () -> SoundEvents.FROG_DEATH),

    GLOW_SQUID("glow_squid", EntityTypes.GLOW_SQUID, LivingEntityWatcher.class, false,
            () -> SoundEvents.GLOW_SQUID_AMBIENT,
            () -> SoundEvents.GLOW_SQUID_HURT,
            () -> SoundEvents.GLOW_SQUID_DEATH),

    GOAT("goat", EntityTypes.GOAT, GoatWatcher.class, false,
            () -> SoundEvents.GOAT_AMBIENT,
            () -> SoundEvents.GOAT_HURT,
            () -> SoundEvents.GOAT_DEATH),

    // 1.21.5+ — comment out if targeting older releases
    HAPPY_GHAST("happy_ghast", EntityTypes.HAPPY_GHAST, LivingEntityWatcher.class, false,
            null, null, null),

    HORSE("horse", EntityTypes.HORSE, HorseWatcher.class, false,
            () -> SoundEvents.HORSE_AMBIENT,
            () -> SoundEvents.HORSE_HURT,
            () -> SoundEvents.HORSE_DEATH),

    IRON_GOLEM("iron_golem", EntityTypes.IRON_GOLEM, LivingEntityWatcher.class, false,
            null,
            () -> SoundEvents.IRON_GOLEM_HURT,
            () -> SoundEvents.IRON_GOLEM_DEATH),

    LLAMA("llama", EntityTypes.LLAMA, LlamaWatcher.class, false,
            () -> SoundEvents.LLAMA_AMBIENT,
            () -> SoundEvents.LLAMA_HURT,
            () -> SoundEvents.LLAMA_DEATH),

    MOOSHROOM("mooshroom", EntityTypes.MOOSHROOM, MooshroomWatcher.class, false,
            DisguiseType::cowAmbientSound,
            DisguiseType::cowHurtSound,
            DisguiseType::cowDeathSound),

    MULE("mule", EntityTypes.MULE, AgeableWatcher.class, false,
            () -> SoundEvents.MULE_AMBIENT,
            () -> SoundEvents.MULE_HURT,
            () -> SoundEvents.MULE_DEATH),

    // 1.21.5+ — comment out if targeting older releases
    NAUTILUS("nautilus", EntityTypes.NAUTILUS, LivingEntityWatcher.class, false,
            () -> SoundEvents.SQUID_AMBIENT,
            () -> SoundEvents.SQUID_HURT,
            () -> SoundEvents.SQUID_DEATH),

    OCELOT("ocelot", EntityTypes.OCELOT, AgeableWatcher.class, false,
            () -> SoundEvents.OCELOT_AMBIENT,
            () -> SoundEvents.OCELOT_HURT,
            () -> SoundEvents.OCELOT_DEATH),

    PANDA("panda", EntityTypes.PANDA, PandaWatcher.class, false,
            () -> SoundEvents.PANDA_AMBIENT,
            () -> SoundEvents.PANDA_HURT,
            () -> SoundEvents.PANDA_DEATH),

    PARROT("parrot", EntityTypes.PARROT, ParrotWatcher.class, false,
            () -> SoundEvents.PARROT_AMBIENT,
            () -> SoundEvents.PARROT_HURT,
            () -> SoundEvents.PARROT_DEATH),

    PIG("pig", EntityTypes.PIG, AgeableWatcher.class, false,
            DisguiseType::pigAmbientSound,
            DisguiseType::pigHurtSound,
            DisguiseType::pigDeathSound),

    POLAR_BEAR("polar_bear", EntityTypes.POLAR_BEAR, AgeableWatcher.class, false,
            () -> SoundEvents.POLAR_BEAR_AMBIENT,
            () -> SoundEvents.POLAR_BEAR_HURT,
            () -> SoundEvents.POLAR_BEAR_DEATH),

    PUFFERFISH("pufferfish", EntityTypes.PUFFERFISH, LivingEntityWatcher.class, false,
            null,
            () -> SoundEvents.PUFFER_FISH_HURT,
            () -> SoundEvents.PUFFER_FISH_DEATH),

    RABBIT("rabbit", EntityTypes.RABBIT, RabbitWatcher.class, false,
            () -> SoundEvents.RABBIT_AMBIENT,
            () -> SoundEvents.RABBIT_HURT,
            () -> SoundEvents.RABBIT_DEATH),

    SALMON("salmon", EntityTypes.SALMON, LivingEntityWatcher.class, false,
            null,
            () -> SoundEvents.SALMON_HURT,
            () -> SoundEvents.SALMON_DEATH),

    SHEEP("sheep", EntityTypes.SHEEP, SheepWatcher.class, false,
            () -> SoundEvents.SHEEP_AMBIENT,
            () -> SoundEvents.SHEEP_HURT,
            () -> SoundEvents.SHEEP_DEATH),

    SKELETON_HORSE("skeleton_horse", EntityTypes.SKELETON_HORSE, AgeableWatcher.class, false,
            () -> SoundEvents.SKELETON_HORSE_AMBIENT,
            () -> SoundEvents.SKELETON_HORSE_HURT,
            () -> SoundEvents.SKELETON_HORSE_DEATH),

    SNIFFER("sniffer", EntityTypes.SNIFFER, AgeableWatcher.class, false,
            () -> SoundEvents.SNIFFER_IDLE,
            () -> SoundEvents.SNIFFER_HURT,
            () -> SoundEvents.SNIFFER_DEATH),

    SNOW_GOLEM("snow_golem", EntityTypes.SNOW_GOLEM, LivingEntityWatcher.class, false,
            null,
            () -> SoundEvents.SNOW_GOLEM_HURT,
            () -> SoundEvents.SNOW_GOLEM_DEATH),

    SQUID("squid", EntityTypes.SQUID, LivingEntityWatcher.class, false,
            () -> SoundEvents.SQUID_AMBIENT,
            () -> SoundEvents.SQUID_HURT,
            () -> SoundEvents.SQUID_DEATH),

    STRIDER("strider", EntityTypes.STRIDER, StriderWatcher.class, false,
            () -> SoundEvents.STRIDER_AMBIENT,
            () -> SoundEvents.STRIDER_HURT,
            () -> SoundEvents.STRIDER_DEATH),

    TADPOLE("tadpole", EntityTypes.TADPOLE, LivingEntityWatcher.class, false,
            () -> SoundEvents.TADPOLE_FLOP,
            () -> SoundEvents.TADPOLE_HURT,
            () -> SoundEvents.TADPOLE_DEATH),

    TRADER_LLAMA("trader_llama", EntityTypes.TRADER_LLAMA, LlamaWatcher.class, false,
            () -> SoundEvents.LLAMA_AMBIENT,
            () -> SoundEvents.LLAMA_HURT,
            () -> SoundEvents.LLAMA_DEATH),

    TROPICAL_FISH("tropical_fish", EntityTypes.TROPICAL_FISH, TropicalFishWatcher.class, false,
            null,
            () -> SoundEvents.TROPICAL_FISH_HURT,
            () -> SoundEvents.TROPICAL_FISH_DEATH),

    TURTLE("turtle", EntityTypes.TURTLE, AgeableWatcher.class, false,
            () -> SoundEvents.TURTLE_AMBIENT_LAND,
            () -> SoundEvents.TURTLE_HURT,
            () -> SoundEvents.TURTLE_DEATH),

    VILLAGER("villager", EntityTypes.VILLAGER, VillagerWatcher.class, false,
            () -> SoundEvents.VILLAGER_AMBIENT,
            () -> SoundEvents.VILLAGER_HURT,
            () -> SoundEvents.VILLAGER_DEATH),

    WANDERING_TRADER("wandering_trader", EntityTypes.WANDERING_TRADER, LivingEntityWatcher.class, false,
            () -> SoundEvents.WANDERING_TRADER_AMBIENT,
            () -> SoundEvents.WANDERING_TRADER_HURT,
            () -> SoundEvents.WANDERING_TRADER_DEATH),

    WOLF("wolf", EntityTypes.WOLF, WolfWatcher.class, false,
            null, null, null),

    ZOMBIE_HORSE("zombie_horse", EntityTypes.ZOMBIE_HORSE, AgeableWatcher.class, false,
            () -> SoundEvents.ZOMBIE_HORSE_AMBIENT,
            () -> SoundEvents.ZOMBIE_HORSE_HURT,
            () -> SoundEvents.ZOMBIE_HORSE_DEATH),

    // =========================================================================
    // LIVING — HOSTILE / NEUTRAL
    // =========================================================================

    BLAZE("blaze", EntityTypes.BLAZE, LivingEntityWatcher.class, false,
            () -> SoundEvents.BLAZE_AMBIENT,
            () -> SoundEvents.BLAZE_HURT,
            () -> SoundEvents.BLAZE_DEATH),

    BOGGED("bogged", EntityTypes.BOGGED, LivingEntityWatcher.class, false,
            null,
            () -> SoundEvents.BOGGED_HURT,
            () -> SoundEvents.BOGGED_DEATH),

    BREEZE("breeze", EntityTypes.BREEZE, LivingEntityWatcher.class, false,
            () -> SoundEvents.BREEZE_IDLE_GROUND,
            () -> SoundEvents.BREEZE_HURT,
            () -> SoundEvents.BREEZE_DEATH),

    CAVE_SPIDER("cave_spider", EntityTypes.CAVE_SPIDER, LivingEntityWatcher.class, false,
            () -> SoundEvents.SPIDER_AMBIENT,
            () -> SoundEvents.SPIDER_HURT,
            () -> SoundEvents.SPIDER_DEATH),

    CREAKING("creaking", EntityTypes.CREAKING, LivingEntityWatcher.class, false,
            () -> SoundEvents.CREAKING_AMBIENT,
            null,
            () -> SoundEvents.CREAKING_DEATH),

    CREEPER("creeper", EntityTypes.CREEPER, CreeperWatcher.class, false,
            null,
            () -> SoundEvents.CREEPER_HURT,
            () -> SoundEvents.CREEPER_DEATH),

    DROWNED("drowned", EntityTypes.DROWNED, LivingEntityWatcher.class, false,
            () -> SoundEvents.DROWNED_AMBIENT,
            () -> SoundEvents.DROWNED_HURT,
            () -> SoundEvents.DROWNED_DEATH),

    ELDER_GUARDIAN("elder_guardian", EntityTypes.ELDER_GUARDIAN, LivingEntityWatcher.class, false,
            () -> SoundEvents.ELDER_GUARDIAN_AMBIENT,
            () -> SoundEvents.ELDER_GUARDIAN_HURT,
            () -> SoundEvents.ELDER_GUARDIAN_DEATH),

    ENDERMAN("enderman", EntityTypes.ENDERMAN, EndermanWatcher.class, false,
            () -> SoundEvents.ENDERMAN_AMBIENT,
            () -> SoundEvents.ENDERMAN_HURT,
            () -> SoundEvents.ENDERMAN_DEATH),

    ENDERMITE("endermite", EntityTypes.ENDERMITE, LivingEntityWatcher.class, false,
            () -> SoundEvents.ENDERMITE_AMBIENT,
            () -> SoundEvents.ENDERMITE_HURT,
            () -> SoundEvents.ENDERMITE_DEATH),

    EVOKER("evoker", EntityTypes.EVOKER, LivingEntityWatcher.class, false,
            () -> SoundEvents.EVOKER_AMBIENT,
            () -> SoundEvents.EVOKER_HURT,
            () -> SoundEvents.EVOKER_DEATH),

    GHAST("ghast", EntityTypes.GHAST, LivingEntityWatcher.class, false,
            () -> SoundEvents.GHAST_AMBIENT,
            () -> SoundEvents.GHAST_HURT,
            () -> SoundEvents.GHAST_DEATH),

    GIANT("giant", EntityTypes.GIANT, LivingEntityWatcher.class, false,
            null,
            () -> SoundEvents.ZOMBIE_HURT,
            () -> SoundEvents.ZOMBIE_DEATH),

    GUARDIAN("guardian", EntityTypes.GUARDIAN, LivingEntityWatcher.class, false,
            () -> SoundEvents.GUARDIAN_AMBIENT,
            () -> SoundEvents.GUARDIAN_HURT,
            () -> SoundEvents.GUARDIAN_DEATH),

    HOGLIN("hoglin", EntityTypes.HOGLIN, AgeableWatcher.class, false,
            () -> SoundEvents.HOGLIN_AMBIENT,
            () -> SoundEvents.HOGLIN_HURT,
            () -> SoundEvents.HOGLIN_DEATH),

    HUSK("husk", EntityTypes.HUSK, LivingEntityWatcher.class, false,
            () -> SoundEvents.HUSK_AMBIENT,
            () -> SoundEvents.HUSK_HURT,
            () -> SoundEvents.HUSK_DEATH),

    MAGMA_CUBE("magma_cube", EntityTypes.MAGMA_CUBE, SlimeWatcher.class, false,
            () -> SoundEvents.MAGMA_CUBE_JUMP,
            () -> SoundEvents.MAGMA_CUBE_HURT_SMALL,
            () -> SoundEvents.MAGMA_CUBE_DEATH_SMALL),

    // 1.21.5+ dry Stray variant — comment out if targeting older releases
    PARCHED("parched", EntityTypes.PARCHED, LivingEntityWatcher.class, false,
            null,
            () -> SoundEvents.STRAY_HURT,
            () -> SoundEvents.STRAY_DEATH),

    PHANTOM("phantom", EntityTypes.PHANTOM, PhantomWatcher.class, false,
            () -> SoundEvents.PHANTOM_AMBIENT,
            () -> SoundEvents.PHANTOM_HURT,
            () -> SoundEvents.PHANTOM_DEATH),

    PIGLIN("piglin", EntityTypes.PIGLIN, PiglinWatcher.class, false,
            () -> SoundEvents.PIGLIN_AMBIENT,
            () -> SoundEvents.PIGLIN_HURT,
            () -> SoundEvents.PIGLIN_DEATH),

    PIGLIN_BRUTE("piglin_brute", EntityTypes.PIGLIN_BRUTE, LivingEntityWatcher.class, false,
            () -> SoundEvents.PIGLIN_BRUTE_AMBIENT,
            () -> SoundEvents.PIGLIN_BRUTE_HURT,
            () -> SoundEvents.PIGLIN_BRUTE_DEATH),

    PILLAGER("pillager", EntityTypes.PILLAGER, LivingEntityWatcher.class, false,
            () -> SoundEvents.PILLAGER_AMBIENT,
            () -> SoundEvents.PILLAGER_HURT,
            () -> SoundEvents.PILLAGER_DEATH),

    RAVAGER("ravager", EntityTypes.RAVAGER, LivingEntityWatcher.class, false,
            () -> SoundEvents.RAVAGER_AMBIENT,
            () -> SoundEvents.RAVAGER_HURT,
            () -> SoundEvents.RAVAGER_DEATH),

    SHULKER("shulker", EntityTypes.SHULKER, ShulkerWatcher.class, false,
            () -> SoundEvents.SHULKER_AMBIENT,
            () -> SoundEvents.SHULKER_HURT,
            () -> SoundEvents.SHULKER_DEATH),

    SILVERFISH("silverfish", EntityTypes.SILVERFISH, LivingEntityWatcher.class, false,
            () -> SoundEvents.SILVERFISH_AMBIENT,
            () -> SoundEvents.SILVERFISH_HURT,
            () -> SoundEvents.SILVERFISH_DEATH),

    SKELETON("skeleton", EntityTypes.SKELETON, LivingEntityWatcher.class, false,
            () -> SoundEvents.SKELETON_AMBIENT,
            () -> SoundEvents.SKELETON_HURT,
            () -> SoundEvents.SKELETON_DEATH),

    SLIME("slime", EntityTypes.SLIME, SlimeWatcher.class, false,
            () -> SoundEvents.SLIME_JUMP,
            () -> SoundEvents.SLIME_HURT_SMALL,
            () -> SoundEvents.SLIME_DEATH),

    //#if MC>=26.2
    // Cube mob added in 26.2 (Chaos Cubed). Hops like a slime and shares the
    // AbstractCubeMob size metadata (index 16), so SlimeWatcher applies directly.
    SULFUR_CUBE("sulfur_cube", EntityTypes.SULFUR_CUBE, SlimeWatcher.class, false,
            () -> SoundEvents.SLIME_JUMP,
            () -> SoundEvents.SLIME_HURT_SMALL,
            () -> SoundEvents.SLIME_DEATH),

    //#endif
    SPIDER("spider", EntityTypes.SPIDER, LivingEntityWatcher.class, false,
            () -> SoundEvents.SPIDER_AMBIENT,
            () -> SoundEvents.SPIDER_HURT,
            () -> SoundEvents.SPIDER_DEATH),

    STRAY("stray", EntityTypes.STRAY, LivingEntityWatcher.class, false,
            () -> SoundEvents.STRAY_AMBIENT,
            () -> SoundEvents.STRAY_HURT,
            () -> SoundEvents.STRAY_DEATH),

    VEX("vex", EntityTypes.VEX, LivingEntityWatcher.class, false,
            () -> SoundEvents.VEX_AMBIENT,
            () -> SoundEvents.VEX_HURT,
            () -> SoundEvents.VEX_DEATH),

    VINDICATOR("vindicator", EntityTypes.VINDICATOR, LivingEntityWatcher.class, false,
            () -> SoundEvents.VINDICATOR_AMBIENT,
            () -> SoundEvents.VINDICATOR_HURT,
            () -> SoundEvents.VINDICATOR_DEATH),

    WARDEN("warden", EntityTypes.WARDEN, LivingEntityWatcher.class, false,
            () -> SoundEvents.WARDEN_AMBIENT,
            () -> SoundEvents.WARDEN_HURT,
            () -> SoundEvents.WARDEN_DEATH),

    WITCH("witch", EntityTypes.WITCH, LivingEntityWatcher.class, false,
            () -> SoundEvents.WITCH_AMBIENT,
            () -> SoundEvents.WITCH_HURT,
            () -> SoundEvents.WITCH_DEATH),

    WITHER("wither", EntityTypes.WITHER, LivingEntityWatcher.class, false,
            () -> SoundEvents.WITHER_AMBIENT,
            () -> SoundEvents.WITHER_HURT,
            () -> SoundEvents.WITHER_DEATH),

    WITHER_SKELETON("wither_skeleton", EntityTypes.WITHER_SKELETON, LivingEntityWatcher.class, false,
            () -> SoundEvents.WITHER_SKELETON_AMBIENT,
            () -> SoundEvents.WITHER_SKELETON_HURT,
            () -> SoundEvents.WITHER_SKELETON_DEATH),

    ZOGLIN("zoglin", EntityTypes.ZOGLIN, AgeableWatcher.class, false,
            () -> SoundEvents.ZOGLIN_AMBIENT,
            () -> SoundEvents.ZOGLIN_HURT,
            () -> SoundEvents.ZOGLIN_DEATH),

    ZOMBIE("zombie", EntityTypes.ZOMBIE, LivingEntityWatcher.class, false,
            () -> SoundEvents.ZOMBIE_AMBIENT,
            () -> SoundEvents.ZOMBIE_HURT,
            () -> SoundEvents.ZOMBIE_DEATH),

    // 1.21.5+ — comment out if targeting older releases
    ZOMBIE_NAUTILUS("zombie_nautilus", EntityTypes.ZOMBIE_NAUTILUS, LivingEntityWatcher.class, false,
            null,
            () -> SoundEvents.DROWNED_HURT,
            () -> SoundEvents.DROWNED_DEATH),

    ZOMBIE_VILLAGER("zombie_villager", EntityTypes.ZOMBIE_VILLAGER, ZombieVillagerWatcher.class, false,
            () -> SoundEvents.ZOMBIE_VILLAGER_AMBIENT,
            () -> SoundEvents.ZOMBIE_VILLAGER_HURT,
            () -> SoundEvents.ZOMBIE_VILLAGER_DEATH),

    ZOMBIFIED_PIGLIN("zombified_piglin", EntityTypes.ZOMBIFIED_PIGLIN, LivingEntityWatcher.class, false,
            () -> SoundEvents.ZOMBIFIED_PIGLIN_AMBIENT,
            () -> SoundEvents.ZOMBIFIED_PIGLIN_HURT,
            () -> SoundEvents.ZOMBIFIED_PIGLIN_DEATH),

    // =========================================================================
    // PLAYER (special — uses PlayerDisguise)
    // =========================================================================

    PLAYER("player", EntityTypes.PLAYER, FlagWatcher.class, false,
            null, null, null),

    // =========================================================================
    // INANIMATE — Armor Stand (LivingEntity; can show equipment)
    // =========================================================================

    ARMOR_STAND("armor_stand", EntityTypes.ARMOR_STAND, ArmorStandWatcher.class, false,
            null, null, null),

    // =========================================================================
    // INANIMATE — Projectiles
    // =========================================================================

    ARROW("arrow", EntityTypes.ARROW, FlagWatcher.class, true, null, null, null),
    BREEZE_WIND_CHARGE("breeze_wind_charge", EntityTypes.BREEZE_WIND_CHARGE, FlagWatcher.class, true, null, null, null),
    EGG("egg", EntityTypes.EGG, FlagWatcher.class, true, null, null, null),
    ENDER_PEARL("ender_pearl", EntityTypes.ENDER_PEARL, FlagWatcher.class, true, null, null, null),
    EXPERIENCE_BOTTLE("experience_bottle", EntityTypes.EXPERIENCE_BOTTLE, FlagWatcher.class, true, null, null, null),
    FIREBALL("fireball", EntityTypes.FIREBALL, FlagWatcher.class, true, null, null, null),
    FIREWORK_ROCKET("firework_rocket", EntityTypes.FIREWORK_ROCKET, FlagWatcher.class, true, null, null, null),
    SMALL_FIREBALL("small_fireball", EntityTypes.SMALL_FIREBALL, FlagWatcher.class, true, null, null, null),
    SNOWBALL("snowball", EntityTypes.SNOWBALL, FlagWatcher.class, true, null, null, null),
    SPECTRAL_ARROW("spectral_arrow", EntityTypes.SPECTRAL_ARROW, FlagWatcher.class, true, null, null, null),
    TRIDENT("trident", EntityTypes.TRIDENT, FlagWatcher.class, true, null, null, null),
    WIND_CHARGE("wind_charge", EntityTypes.WIND_CHARGE, FlagWatcher.class, true, null, null, null),
    WITHER_SKULL("wither_skull", EntityTypes.WITHER_SKULL, FlagWatcher.class, true, null, null, null),

    // =========================================================================
    // INANIMATE — World entities
    // =========================================================================
    //
    // EXCLUDED — these types cannot be spoofed safely via ClientboundAddEntityPacket:
    //
    //   PAINTING       — Painting.recreateFromPacket calls HangingEntity.setDirection
    //                    which validates that a real block face exists at the entity's
    //                    position to hang on. A player in open air always fails this
    //                    check → Validate.isTrue crash on every observer. There is no
    //                    way to send a painting packet for an arbitrary world position.
    //                    Use ITEM_FRAME or GLOW_ITEM_FRAME for a similar flat look.
    //
    // EXPERIENCE_ORB was previously excluded because older MC versions sent it via a
    // separate ClientboundAddExperienceOrbPacket. As of 1.20.2, experience orbs use
    // the standard ClientboundAddEntityPacket like all other entity types.
    // PacketInterceptor.getAddEntityData() returns the exp value for the data field.

    END_CRYSTAL("end_crystal", EntityTypes.END_CRYSTAL, FlagWatcher.class, true, null, null, null),

    /**
     * Experience orb. The AddEntityPacket {@code data} field encodes the experience
     * amount shown visually. PacketInterceptor returns 1 by default (the smallest orb).
     */
    EXPERIENCE_ORB("experience_orb", EntityTypes.EXPERIENCE_ORB, FlagWatcher.class, true, null, null, null),

    /**
     * PacketInterceptor reads FallingBlockWatcher.getBlockId() for the AddEntityPacket data field.
     * Defaults to STONE.  Use setBlock() on the watcher to choose any block.
     */
    FALLING_BLOCK("falling_block", EntityTypes.FALLING_BLOCK, FallingBlockWatcher.class, true, null, null, null),

    /**
     * Block Display entity (MC 1.19.4+).
     * Renders a full-size block at the entity's position using the Display entity system.
     * Use setBlock() on the watcher to choose the displayed block (default: STONE).
     */
    BLOCK_DISPLAY("block_display", EntityTypes.BLOCK_DISPLAY, BlockDisplayWatcher.class, true, null, null, null),

    GLOW_ITEM_FRAME("glow_item_frame", EntityTypes.GLOW_ITEM_FRAME, FlagWatcher.class, true, null, null, null),
    ITEM("item", EntityTypes.ITEM, ItemWatcher.class, true, null, null, null),
    ITEM_FRAME("item_frame", EntityTypes.ITEM_FRAME, FlagWatcher.class, true, null, null, null),
    LEASH_KNOT("leash_knot", EntityTypes.LEASH_KNOT, FlagWatcher.class, true, null, null, null),
    TNT("tnt", EntityTypes.TNT, FlagWatcher.class, true, null, null, null),

    // =========================================================================
    // INANIMATE — Boats (each wood type is its own EntityType in 1.21)
    // =========================================================================

    ACACIA_BOAT("acacia_boat", EntityTypes.ACACIA_BOAT, FlagWatcher.class, true, null, null, null),
    ACACIA_CHEST_BOAT("acacia_chest_boat", EntityTypes.ACACIA_CHEST_BOAT, FlagWatcher.class, true, null, null, null),
    BAMBOO_RAFT("bamboo_raft", EntityTypes.BAMBOO_RAFT, FlagWatcher.class, true, null, null, null),
    BAMBOO_CHEST_RAFT("bamboo_chest_raft", EntityTypes.BAMBOO_CHEST_RAFT, FlagWatcher.class, true, null, null, null),
    BIRCH_BOAT("birch_boat", EntityTypes.BIRCH_BOAT, FlagWatcher.class, true, null, null, null),
    BIRCH_CHEST_BOAT("birch_chest_boat", EntityTypes.BIRCH_CHEST_BOAT, FlagWatcher.class, true, null, null, null),
    CHERRY_BOAT("cherry_boat", EntityTypes.CHERRY_BOAT, FlagWatcher.class, true, null, null, null),
    CHERRY_CHEST_BOAT("cherry_chest_boat", EntityTypes.CHERRY_CHEST_BOAT, FlagWatcher.class, true, null, null, null),
    DARK_OAK_BOAT("dark_oak_boat", EntityTypes.DARK_OAK_BOAT, FlagWatcher.class, true, null, null, null),
    DARK_OAK_CHEST_BOAT("dark_oak_chest_boat", EntityTypes.DARK_OAK_CHEST_BOAT, FlagWatcher.class, true, null, null, null),
    JUNGLE_BOAT("jungle_boat", EntityTypes.JUNGLE_BOAT, FlagWatcher.class, true, null, null, null),
    JUNGLE_CHEST_BOAT("jungle_chest_boat", EntityTypes.JUNGLE_CHEST_BOAT, FlagWatcher.class, true, null, null, null),
    MANGROVE_BOAT("mangrove_boat", EntityTypes.MANGROVE_BOAT, FlagWatcher.class, true, null, null, null),
    MANGROVE_CHEST_BOAT("mangrove_chest_boat", EntityTypes.MANGROVE_CHEST_BOAT, FlagWatcher.class, true, null, null, null),
    OAK_BOAT("oak_boat", EntityTypes.OAK_BOAT, FlagWatcher.class, true, null, null, null),
    OAK_CHEST_BOAT("oak_chest_boat", EntityTypes.OAK_CHEST_BOAT, FlagWatcher.class, true, null, null, null),
    PALE_OAK_BOAT("pale_oak_boat", EntityTypes.PALE_OAK_BOAT, FlagWatcher.class, true, null, null, null),
    PALE_OAK_CHEST_BOAT("pale_oak_chest_boat", EntityTypes.PALE_OAK_CHEST_BOAT, FlagWatcher.class, true, null, null, null),
    SPRUCE_BOAT("spruce_boat", EntityTypes.SPRUCE_BOAT, FlagWatcher.class, true, null, null, null),
    SPRUCE_CHEST_BOAT("spruce_chest_boat", EntityTypes.SPRUCE_CHEST_BOAT, FlagWatcher.class, true, null, null, null),

    // =========================================================================
    // INANIMATE — Minecarts
    // =========================================================================

    CHEST_MINECART("chest_minecart", EntityTypes.CHEST_MINECART, MinecartWatcher.class, true, null, null, null),
    COMMAND_BLOCK_MINECART("command_block_minecart", EntityTypes.COMMAND_BLOCK_MINECART, MinecartWatcher.class, true, null, null, null),
    FURNACE_MINECART("furnace_minecart", EntityTypes.FURNACE_MINECART, MinecartWatcher.class, true, null, null, null),
    HOPPER_MINECART("hopper_minecart", EntityTypes.HOPPER_MINECART, MinecartWatcher.class, true, null, null, null),
    MINECART("minecart", EntityTypes.MINECART, MinecartWatcher.class, true, null, null, null),
    SPAWNER_MINECART("spawner_minecart", EntityTypes.SPAWNER_MINECART, MinecartWatcher.class, true, null, null, null),
    TNT_MINECART("tnt_minecart", EntityTypes.TNT_MINECART, MinecartWatcher.class, true, null, null, null);

    // =========================================================================
    // Fields
    // =========================================================================

    private final String id;
    private final EntityType<?> entityType;
    private final Class<? extends FlagWatcher> watcherClass;
    private final boolean inanimate;

    private final Supplier<SoundEvent> ambientSupplier;
    private final Supplier<SoundEvent> hurtSupplier;
    private final Supplier<SoundEvent> deathSupplier;

    // =========================================================================
    // Constructor
    // =========================================================================

    DisguiseType(String id, EntityType<?> entityType,
                 Class<? extends FlagWatcher> watcherClass, boolean inanimate,
                 Supplier<SoundEvent> ambientSupplier,
                 Supplier<SoundEvent> hurtSupplier,
                 Supplier<SoundEvent> deathSupplier) {
        this.id              = id;
        this.entityType      = entityType;
        this.watcherClass    = watcherClass;
        this.inanimate       = inanimate;
        this.ambientSupplier = ambientSupplier;
        this.hurtSupplier    = hurtSupplier;
        this.deathSupplier   = deathSupplier;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    public String getId() { return id; }
    public EntityType<?> getEntityType() { return entityType; }
    public Class<? extends FlagWatcher> getWatcherClass() { return watcherClass; }
    public boolean isInanimate() { return inanimate; }

    public boolean isPlayer() {
        return this == PLAYER;
    }

    public FlagWatcher createDefaultWatcher() {
        try {
            return watcherClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return new FlagWatcher();
        }
    }

    public SoundEvent getAmbientSound() {
        return ambientSupplier != null ? ambientSupplier.get() : null;
    }

    public SoundEvent getHurtSound() {
        return hurtSupplier != null ? hurtSupplier.get() : null;
    }

    public SoundEvent getDeathSound() {
        return deathSupplier != null ? deathSupplier.get() : null;
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    public static Optional<DisguiseType> fromId(String id) {
        if (id == null) return Optional.empty();
        String lower = id.toLowerCase();
        return Arrays.stream(values()).filter(t -> t.id.equals(lower)).findFirst();
    }

    private static SoundEvent catAmbientSound() {
        return SoundCompat.catAmbientSound();
    }

    private static SoundEvent catHurtSound() {
        return SoundCompat.catHurtSound();
    }

    private static SoundEvent catDeathSound() {
        return SoundCompat.catDeathSound();
    }

    private static SoundEvent chickenAmbientSound() {
        return SoundCompat.chickenAmbientSound();
    }

    private static SoundEvent chickenHurtSound() {
        return SoundCompat.chickenHurtSound();
    }

    private static SoundEvent chickenDeathSound() {
        return SoundCompat.chickenDeathSound();
    }

    private static SoundEvent cowAmbientSound() {
        return SoundCompat.cowAmbientSound();
    }

    private static SoundEvent cowHurtSound() {
        return SoundCompat.cowHurtSound();
    }

    private static SoundEvent cowDeathSound() {
        return SoundCompat.cowDeathSound();
    }

    private static SoundEvent pigAmbientSound() {
        return SoundCompat.pigAmbientSound();
    }

    private static SoundEvent pigHurtSound() {
        return SoundCompat.pigHurtSound();
    }

    private static SoundEvent pigDeathSound() {
        return SoundCompat.pigDeathSound();
    }
}
