package com.coffee.disguises.packet;

import com.coffee.disguises.watcher.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.wolf.Wolf;   // Wolf moved to animal.wolf sub-package in 1.21
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Slime;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Constructs SynchedEntityData.DataValue lists from a FlagWatcher.
 *
 * Uses reflection to access the static EntityDataAccessor fields because many
 * are private. An Access Widener is the cleaner long-term solution, but
 * reflection works fine for development.
 *
 * NOTE: Sheep is loaded via Class.forName() rather than a direct import because
 * the class path can vary between minor MC versions / Loom remapping environments.
 * If MetadataBuilder.init() throws a RuntimeException naming "Sheep", open the
 * decompiled source in IntelliJ (Ctrl+N → search "Sheep") to confirm its package.
 */
public class MetadataBuilder {

    // Entity base
    private static Field F_ENTITY_FLAGS;
    private static Field F_ENTITY_CUSTOM_NAME;
    private static Field F_ENTITY_CUSTOM_NAME_VISIBLE;
    private static Field F_ENTITY_SILENT;
    private static Field F_ENTITY_NO_GRAVITY;

    // LivingEntity
    private static Field F_LIVING_HEALTH;
    private static Field F_LIVING_EFFECT_COLOR;
    private static Field F_LIVING_EFFECT_AMBIENT;
    private static Field F_LIVING_ARROW_COUNT;
    private static Field F_LIVING_STINGER_COUNT;

    // AgeableMob
    private static Field F_AGEABLE_BABY;

    // Sheep — loaded via Class.forName to avoid import path issues across MC minor versions
    private static Field F_SHEEP_FLAGS;

    // Creeper
    private static Field F_CREEPER_SWELL_DIR;
    private static Field F_CREEPER_POWERED;
    private static Field F_CREEPER_IGNITED;

    // Slime
    private static Field F_SLIME_SIZE;

    // Wolf (TamableAnimal + Wolf)
    private static Field F_TAMEABLE_FLAGS;
    private static Field F_WOLF_COLLAR_COLOR;
    private static Field F_WOLF_ANGER_TIME;

    // Enderman
    private static Field F_ENDERMAN_CREEPY;

    // Player — skin layer visibility (hat, jacket, sleeves, pants legs)
    // DATA_PLAYER_MODE_CUSTOMISATION byte: 0x7f = all outer layers visible
    private static Field F_PLAYER_SKIN_PARTS;

    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;

        // Each field is resolved independently. If a field was renamed or removed
        // in this MC version it is left null and skipped in build() rather than
        // crashing the whole mod at startup.
        F_ENTITY_FLAGS               = tryField(Entity.class,        "DATA_SHARED_FLAGS_ID");
        F_ENTITY_CUSTOM_NAME         = tryField(Entity.class,        "DATA_CUSTOM_NAME");
        F_ENTITY_CUSTOM_NAME_VISIBLE = tryField(Entity.class,        "DATA_CUSTOM_NAME_VISIBLE");
        F_ENTITY_SILENT              = tryField(Entity.class,        "DATA_SILENT");
        F_ENTITY_NO_GRAVITY          = tryField(Entity.class,        "DATA_NO_GRAVITY");

        F_LIVING_HEALTH              = tryField(LivingEntity.class,  "DATA_HEALTH_ID");
        F_LIVING_ARROW_COUNT         = tryField(LivingEntity.class,  "DATA_ARROW_COUNT_ID");
        F_LIVING_STINGER_COUNT       = tryField(LivingEntity.class,  "DATA_STINGER_COUNT_ID");

        // Potion effect fields were renamed/restructured in 1.21.x.
        // Try both old and new names; whichever resolves is used.
        F_LIVING_EFFECT_COLOR   = firstField(LivingEntity.class,
                "DATA_EFFECT_COLOR_ID", "DATA_EFFECT_PARTICLES");
        F_LIVING_EFFECT_AMBIENT = firstField(LivingEntity.class,
                "DATA_EFFECT_AMBIENCE_ID", "DATA_EFFECT_AMBIENCE");

        F_AGEABLE_BABY               = tryField(AgeableMob.class,    "DATA_BABY_ID");

        // Sheep — try multiple paths across MC versions
        Class<?> sheepClass = null;
        for (String path : new String[]{
                "net.minecraft.world.entity.animal.Sheep",
                "net.minecraft.world.entity.animal.sheep.Sheep"}) {
            try { sheepClass = Class.forName(path); break; }
            catch (ClassNotFoundException ignored) {}
        }
        if (sheepClass != null) {
            F_SHEEP_FLAGS = firstField(sheepClass, "DATA_WOOL_ID", "DATA_SHEARED", "DATA_SHEEP_FLAGS");
        } else {
            com.coffee.disguises.DisguisesMod.LOGGER.warn("[Disguises] Could not find Sheep class — sheep disguise metadata will be skipped.");
        }

        F_CREEPER_SWELL_DIR = tryField(Creeper.class, "DATA_SWELL_DIR");
        F_CREEPER_POWERED   = tryField(Creeper.class, "DATA_IS_POWERED");
        F_CREEPER_IGNITED   = tryField(Creeper.class, "DATA_IS_IGNITED");

        F_SLIME_SIZE        = firstField(Slime.class,         "DATA_SIZE", "ID_SIZE");

        F_TAMEABLE_FLAGS    = tryField(TamableAnimal.class, "DATA_FLAGS_ID");
        F_WOLF_COLLAR_COLOR = tryField(Wolf.class,          "DATA_COLLAR_COLOR");
        F_WOLF_ANGER_TIME   = firstField(Wolf.class,        "DATA_REMAINING_ANGER_TIME", "DATA_ANGER_TIME");

        F_ENDERMAN_CREEPY   = firstField(EnderMan.class,    "DATA_CREEPY", "DATA_CREEPY_STARE");

        // Dump all EntityDataAccessor fields on Player so we can find the right name
        com.coffee.disguises.DisguisesMod.LOGGER.info("[Disguises] Scanning Player fields for EntityDataAccessor:");
        for (java.lang.reflect.Field f : net.minecraft.world.entity.player.Player.class.getDeclaredFields()) {
            if (net.minecraft.network.syncher.EntityDataAccessor.class.isAssignableFrom(f.getType())) {
                com.coffee.disguises.DisguisesMod.LOGGER.info("[Disguises]   Player field: {}", f.getName());
            }
        }

        // Skin parts field not found in Player.class — also scan ServerPlayer
        // in case it was moved there in this MC version.
        if (F_PLAYER_SKIN_PARTS == null) {
            F_PLAYER_SKIN_PARTS = firstField(net.minecraft.server.level.ServerPlayer.class,
                    "DATA_PLAYER_MODE_CUSTOMISATION", "DATA_SKIN_PARTS",
                    "DATA_PLAYER_SKIN_CUSTOMISATION");
        }
        // Log all EntityDataAccessor fields on ServerPlayer to find the correct name
        if (F_PLAYER_SKIN_PARTS == null) {
            com.coffee.disguises.DisguisesMod.LOGGER.info("[Disguises] ServerPlayer EntityDataAccessor fields:");
            for (java.lang.reflect.Field f : net.minecraft.server.level.ServerPlayer.class.getDeclaredFields()) {
                if (net.minecraft.network.syncher.EntityDataAccessor.class.isAssignableFrom(f.getType())) {
                    com.coffee.disguises.DisguisesMod.LOGGER.info("[Disguises]   ServerPlayer field: {}", f.getName());
                }
            }
            com.coffee.disguises.DisguisesMod.LOGGER.warn("[Disguises] Skin parts accessor not found — outer layers will rely on pendingDataResend from a real online player.");
        }

        initialized = true;
    }

    /** Resolve a field, returning null (with a warning) if not found. */
    private static Field tryField(Class<?> clazz, String name) {
        try {
            return getField(clazz, name);
        } catch (Exception e) {
            com.coffee.disguises.DisguisesMod.LOGGER.warn(
                    "[Disguises] Could not resolve {}.{} — metadata entry will be skipped: {}",
                    clazz.getSimpleName(), name, e.getMessage());
            return null;
        }
    }

    /** Try each field name in order; return the first that resolves, or null. */
    private static Field firstField(Class<?> clazz, String... names) {
        for (String name : names) {
            try { return getField(clazz, name); } catch (Exception ignored) {}
        }
        com.coffee.disguises.DisguisesMod.LOGGER.warn(
                "[Disguises] Could not resolve {}.{any of {}} — metadata entry will be skipped.",
                clazz.getSimpleName(), java.util.Arrays.toString(names));
        return null;
    }

    private static Field getField(Class<?> clazz, String name) throws Exception {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    @SuppressWarnings("unchecked")
    private static <T> net.minecraft.network.syncher.EntityDataAccessor<T> accessor(Field f) {
        try {
            return (net.minecraft.network.syncher.EntityDataAccessor<T>) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read accessor from field: " + f.getName(), e);
        }
    }

    // -----------------------------------------------------------------------

    public static List<SynchedEntityData.DataValue<?>> build(FlagWatcher watcher) {
        return build(watcher, null);
    }

    public static List<SynchedEntityData.DataValue<?>> build(FlagWatcher watcher, com.coffee.disguises.disguise.DisguiseType type) {
        if (!initialized) init();

        List<SynchedEntityData.DataValue<?>> list = new ArrayList<>();

        // Entity base
        addEntry(list, F_ENTITY_FLAGS, EntityDataSerializers.BYTE, watcher.buildSharedFlags());

        if (watcher.getCustomName() != null) {
            addEntry(list, F_ENTITY_CUSTOM_NAME, EntityDataSerializers.OPTIONAL_COMPONENT,
                    Optional.of(Component.literal(watcher.getCustomName())));
            addEntry(list, F_ENTITY_CUSTOM_NAME_VISIBLE, EntityDataSerializers.BOOLEAN,
                    watcher.isCustomNameVisible());
        }
        if (watcher.isSilent()) {
            addEntry(list, F_ENTITY_SILENT, EntityDataSerializers.BOOLEAN, true);
        }
        if (watcher.isNoGravity()) {
            addEntry(list, F_ENTITY_NO_GRAVITY, EntityDataSerializers.BOOLEAN, true);
        }

        // LivingEntity
        if (watcher instanceof LivingEntityWatcher lew) {
            addEntry(list, F_LIVING_HEALTH, EntityDataSerializers.FLOAT, lew.getHealth());
            if (lew.getPotionEffectColor() != 0 && F_LIVING_EFFECT_COLOR != null && F_LIVING_EFFECT_AMBIENT != null) {
                addEntry(list, F_LIVING_EFFECT_COLOR,   EntityDataSerializers.INT,     lew.getPotionEffectColor());
                addEntry(list, F_LIVING_EFFECT_AMBIENT, EntityDataSerializers.BOOLEAN, lew.isPotionEffectAmbient());
            }
            if (lew.getArrowCount() > 0) {
                addEntry(list, F_LIVING_ARROW_COUNT, EntityDataSerializers.INT, lew.getArrowCount());
            }
        }

        // AgeableMob
        if (watcher instanceof AgeableWatcher aw) {
            addEntry(list, F_AGEABLE_BABY, EntityDataSerializers.BOOLEAN, aw.isBaby());
        }

        // Sheep
        if (watcher instanceof SheepWatcher sw) {
            addEntry(list, F_SHEEP_FLAGS, EntityDataSerializers.BYTE, sw.getSheepFlags());
        }

        // Creeper
        if (watcher instanceof CreeperWatcher cw) {
            addEntry(list, F_CREEPER_SWELL_DIR, EntityDataSerializers.INT,     -1);
            addEntry(list, F_CREEPER_POWERED,   EntityDataSerializers.BOOLEAN, cw.isPowered());
            addEntry(list, F_CREEPER_IGNITED,   EntityDataSerializers.BOOLEAN, cw.isIgnited());
        }

        // Slime / MagmaCube
        if (watcher instanceof SlimeWatcher slw) {
            addEntry(list, F_SLIME_SIZE, EntityDataSerializers.INT, slw.getSize());
        }

        // Wolf
        if (watcher instanceof WolfWatcher ww) {
            addEntry(list, F_TAMEABLE_FLAGS,    EntityDataSerializers.BYTE, ww.getTameableFlags());
            addEntry(list, F_WOLF_COLLAR_COLOR, EntityDataSerializers.INT,  ww.getCollarColor().getId());
            addEntry(list, F_WOLF_ANGER_TIME,   EntityDataSerializers.INT,  ww.isAngry() ? 400 : 0);
        }

        // Enderman
        if (watcher instanceof EndermanWatcher ew) {
            addEntry(list, F_ENDERMAN_CREEPY, EntityDataSerializers.BOOLEAN, ew.isScreaming());
        }

        // Player — skin overlay layers (hat, jacket, sleeves, pants legs)
        // Bit flags: 0x01=cape 0x02=jacket 0x04=leftSleeve 0x08=rightSleeve
        //            0x10=leftPants 0x20=rightPants 0x40=hat → 0x7f = all on
        if (type == com.coffee.disguises.disguise.DisguiseType.PLAYER) {
            addEntry(list, F_PLAYER_SKIN_PARTS, EntityDataSerializers.BYTE, (byte) 0x7f);
        }

        return list;
    }

    private static <T> void addEntry(List<SynchedEntityData.DataValue<?>> list,
                                     Field accessorField,
                                     net.minecraft.network.syncher.EntityDataSerializer<T> serializer,
                                     T value) {
        if (accessorField == null) return;
        try {
            net.minecraft.network.syncher.EntityDataAccessor<T> acc = accessor(accessorField);
            list.add(SynchedEntityData.DataValue.create(acc, value));
        } catch (Exception e) {
            com.coffee.disguises.DisguisesMod.LOGGER.warn(
                    "Disguises: skipping metadata entry for field '{}': {}", accessorField.getName(), e.getMessage());
        }
    }
}