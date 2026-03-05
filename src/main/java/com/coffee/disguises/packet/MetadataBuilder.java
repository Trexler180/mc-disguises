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
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Slime;

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

    // Display base — scale field (Vector3f, index 12 in 1.21.x)
    // Sending (1,1,1) is required; the default is (0,0,0) which makes the entity invisible.
    private static Field F_DISPLAY_SCALE;

    // ── Expected DataTracker indices ──────────────────────────────────────────
    // Used as fallback when name-based lookup fails.

    private static final int IDX_ENTITY_FLAGS                = 0;
    private static final int IDX_ENTITY_CUSTOM_NAME          = 2;
    private static final int IDX_ENTITY_CUSTOM_NAME_VISIBLE  = 3;
    private static final int IDX_ENTITY_SILENT               = 4;
    private static final int IDX_ENTITY_NO_GRAVITY           = 5;

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

    private static final int IDX_TAMEABLE_FLAGS              = 16;
    private static final int IDX_WOLF_COLLAR_COLOR           = 18;
    private static final int IDX_WOLF_ANGER_TIME             = 19;

    private static final int IDX_ENDERMAN_CREEPY             = 17;

    private static final int IDX_PLAYER_SKIN_PARTS           = 17; // DATA_PLAYER_MODE_CUSTOMISATION

    // AbstractMinecart (indices relative to full entity hierarchy — approximately 11–13 in 1.21.x)
    private static final int IDX_MINECART_DISPLAY_BLOCK   = 11;
    private static final int IDX_MINECART_DISPLAY_OFFSET  = 12;
    private static final int IDX_MINECART_CUSTOM_DISPLAY  = 13;

    // BlockDisplay block state (first Display subtype-specific field, ~index 23 in 1.21.x)
    private static final int IDX_BLOCK_DISPLAY_BLOCK_STATE = 23;

    // Display.DATA_SCALE_ID — index 12 in 1.21.x Mojang mappings
    private static final int IDX_DISPLAY_SCALE = 12;

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

        F_LIVING_FLAGS         = resolve(LivingEntity.class, IDX_LIVING_FLAGS,         "DATA_LIVING_ENTITY_FLAGS", "DATA_LIVING_FLAGS");
        F_LIVING_HEALTH        = resolve(LivingEntity.class, IDX_LIVING_HEALTH,        "DATA_HEALTH_ID",           "DATA_HEALTH");
        F_LIVING_ARROW_COUNT   = resolve(LivingEntity.class, IDX_LIVING_ARROW_COUNT,   "DATA_ARROW_COUNT_ID",      "DATA_ARROWS_ID");
        F_LIVING_STINGER_COUNT = resolve(LivingEntity.class, IDX_LIVING_STINGER_COUNT, "DATA_STINGER_COUNT_ID",    "DATA_STINGERS_ID");

        F_AGEABLE_BABY = resolve(AgeableMob.class, IDX_AGEABLE_BABY, "DATA_BABY_ID", "IS_BABY");

        // Sheep — may have moved to sub-package in 1.21
        Class<?> sheepClass = loadClass(
                "net.minecraft.world.entity.animal.sheep.Sheep",   // 1.21+
                "net.minecraft.world.entity.animal.Sheep"          // <1.21
        );
        if (sheepClass != null) {
            F_SHEEP_COLOR = resolve(sheepClass, IDX_SHEEP_COLOR,
                    "DATA_WOOL_ID", "DATA_SHEARED_ID", "DATA_SHEEP_FLAGS", "DATA_COLOR_ID");
        } else {
            com.coffee.disguises.DisguisesMod.LOGGER.warn("[MetadataBuilder] Sheep class not found — color/sheared flags will not work.");
        }

        F_CREEPER_SWELL_DIR = resolve(Creeper.class, IDX_CREEPER_SWELL_DIR, "DATA_SWELL_DIR",  "DATA_SWELL_DIRECTION");
        F_CREEPER_POWERED   = resolve(Creeper.class, IDX_CREEPER_POWERED,   "DATA_IS_POWERED", "DATA_POWERED");
        F_CREEPER_IGNITED   = resolve(Creeper.class, IDX_CREEPER_IGNITED,   "DATA_IS_IGNITED", "DATA_IGNITED");

        F_SLIME_SIZE = resolve(Slime.class, IDX_SLIME_SIZE, "DATA_SIZE", "ID_SIZE", "DATA_SLIME_SIZE");

        F_TAMEABLE_FLAGS    = resolve(TamableAnimal.class, IDX_TAMEABLE_FLAGS,    "DATA_FLAGS_ID",             "DATA_TAME_FLAGS");
        F_WOLF_COLLAR_COLOR = resolve(Wolf.class,          IDX_WOLF_COLLAR_COLOR, "DATA_COLLAR_COLOR",         "DATA_COLLAR_COLOR_ID");
        F_WOLF_ANGER_TIME   = resolve(Wolf.class,          IDX_WOLF_ANGER_TIME,   "DATA_REMAINING_ANGER_TIME", "DATA_ANGER_TIME", "DATA_ANGRY");

        F_ENDERMAN_CREEPY = resolve(EnderMan.class, IDX_ENDERMAN_CREEPY, "DATA_CREEPY", "DATA_CREEPY_STARE", "DATA_IS_SCREAMING");

        // Player skin parts — check Player then ServerPlayer
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

        // BlockDisplay — block state field
        Class<?> blockDisplayClass = loadClass(
                "net.minecraft.world.entity.Display$BlockDisplay",
                "net.minecraft.world.entity.display.BlockDisplay"
        );
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
        } else {
            com.coffee.disguises.DisguisesMod.LOGGER.warn("[MetadataBuilder] BlockDisplay class not found — block_display disguise will not show block.");
        }

        // Summary
        logResolution("Entity flags",          F_ENTITY_FLAGS);
        logResolution("Entity customName",     F_ENTITY_CUSTOM_NAME);
        logResolution("LivingEntity flags",    F_LIVING_FLAGS);
        logResolution("LivingEntity health",   F_LIVING_HEALTH);
        logResolution("AgeableMob baby",       F_AGEABLE_BABY);
        logResolution("Sheep color",           F_SHEEP_COLOR);
        logResolution("Creeper swellDir",      F_CREEPER_SWELL_DIR);
        logResolution("Creeper powered",       F_CREEPER_POWERED);
        logResolution("Slime size",            F_SLIME_SIZE);
        logResolution("Wolf collarColor",      F_WOLF_COLLAR_COLOR);
        logResolution("Enderman creepy",       F_ENDERMAN_CREEPY);
        logResolution("Player skinParts",      F_PLAYER_SKIN_PARTS);
        logResolution("Minecart displayBlock",  F_MINECART_DISPLAY_BLOCK);
        logResolution("Minecart displayOffset", F_MINECART_DISPLAY_OFFSET);
        logResolution("Minecart customDisplay", F_MINECART_CUSTOM_DISPLAY);
        logResolution("BlockDisplay blockState",F_BLOCK_DISPLAY_BLOCK_STATE);
        logResolution("Display scale",          F_DISPLAY_SCALE);

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
     * Entity parameter retained for API compatibility; the entity is no longer
     * used internally (minecart fields are resolved in init() via EntityType).
     */
    public static List<SynchedEntityData.DataValue<?>> build(
            FlagWatcher watcher,
            com.coffee.disguises.disguise.DisguiseType type,
            net.minecraft.world.entity.Entity entity) {

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
            putInt(list,  F_WOLF_ANGER_TIME,   ww.isAngry() ? 400 : 0);
        }

        // ── Enderman ──────────────────────────────────────────────────────────

        if (watcher instanceof EndermanWatcher ew) {
            putBool(list, F_ENDERMAN_CREEPY, ew.isScreaming());
        }

        // ── Player skin-layer customisation ───────────────────────────────────
        // 0x7F = all outer layers visible (cape, jacket, sleeves, pants, hat)

        if (type == com.coffee.disguises.disguise.DisguiseType.PLAYER) {
            putByte(list, F_PLAYER_SKIN_PARTS, (byte) 0x7F);
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
    private static void putBool(List<SynchedEntityData.DataValue<?>> list, Field f, boolean value) {
        put(list, f, value);
    }
    private static void putInt(List<SynchedEntityData.DataValue<?>> list, Field f, int value) {
        put(list, f, value);
    }
    private static void putFloat(List<SynchedEntityData.DataValue<?>> list, Field f, float value) {
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
}