package com.coffee.disguises.client.compat;

import com.coffee.disguises.DisguisesMod;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * ModMenu integration. Provides a Cloth Config screen for the disguises config.
 *
 * This class MUST be in src/client/java because it references Screen and other
 * client-only types.
 *
 * Both cloth-config2 and modmenu are optional at runtime — the class is only
 * loaded if ModMenu is present. If Cloth Config is absent, returns null
 * (ModMenu will show the default "no config" message).
 */
public class ModMenuApiImpl implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Guard: if Cloth Config isn't loaded, don't crash
        if (!FabricLoader.getInstance().isModLoaded("cloth-config2")) {
            return parent -> null;
        }
        return this::buildConfigScreen;
    }

    private Screen buildConfigScreen(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Disguises Configuration"))
                .setSavingRunnable(() -> DisguisesMod.CONFIG.save());

        ConfigEntryBuilder entries = builder.entryBuilder();

        // ---- General ----
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

        general.addEntry(entries
                .startBooleanToggle(Component.literal("Show Disguise Action Bar"),
                        DisguisesMod.CONFIG.showDisguiseActionBar)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Show a message in your action bar when disguised."))
                .setSaveConsumer(v -> DisguisesMod.CONFIG.showDisguiseActionBar = v)
                .build());

        general.addEntry(entries
                .startBooleanToggle(Component.literal("Substitute Sounds"),
                        DisguisesMod.CONFIG.disguiseSounds)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Play the disguise type's sounds instead of the real entity's sounds."))
                .setSaveConsumer(v -> DisguisesMod.CONFIG.disguiseSounds = v)
                .build());

        general.addEntry(entries
                .startBooleanToggle(Component.literal("Self-Disguise Default"),
                        DisguisesMod.CONFIG.selfDisguiseDefault)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Whether players see their own disguise by default."))
                .setSaveConsumer(v -> DisguisesMod.CONFIG.selfDisguiseDefault = v)
                .build());

        general.addEntry(entries
                .startBooleanToggle(Component.literal("Show Equipment Through Disguise"),
                        DisguisesMod.CONFIG.showEquipmentThroughDisguise)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Show the real entity's held items/armor on the disguise."))
                .setSaveConsumer(v -> DisguisesMod.CONFIG.showEquipmentThroughDisguise = v)
                .build());

        // ---- Player Disguises ----
        ConfigCategory playerDisguises = builder.getOrCreateCategory(Component.literal("Player Disguises"));

        playerDisguises.addEntry(entries
                .startBooleanToggle(Component.literal("Show in Tab List"),
                        DisguisesMod.CONFIG.showDisguiseInTab)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Keep the fake player entry in the tab list permanently."))
                .setSaveConsumer(v -> DisguisesMod.CONFIG.showDisguiseInTab = v)
                .build());

        playerDisguises.addEntry(entries
                .startIntSlider(Component.literal("Tab Entry Remove Delay (ticks)"),
                        DisguisesMod.CONFIG.tabRemoveDelayTicks, 0, 200)
                .setDefaultValue(20)
                .setTooltip(Component.literal("Ticks before removing the fake tab entry after spawning."))
                .setSaveConsumer(v -> DisguisesMod.CONFIG.tabRemoveDelayTicks = v)
                .build());

        // ---- Vanish ----
        ConfigCategory vanish = builder.getOrCreateCategory(Component.literal("Vanish"));

        vanish.addEntry(entries
                .startBooleanToggle(Component.literal("Hide Vanished Disguised Entities"),
                        DisguisesMod.CONFIG.vanishedEntitiesHidden)
                .setDefaultValue(true)
                .setTooltip(Component.literal("If an entity is vanished, don't send any packets to unauthorized observers."))
                .setSaveConsumer(v -> DisguisesMod.CONFIG.vanishedEntitiesHidden = v)
                .build());

        // ---- Persistence ----
        ConfigCategory persistence = builder.getOrCreateCategory(Component.literal("Persistence"));

        persistence.addEntry(entries
                .startBooleanToggle(Component.literal("Persist Disguises Across Restarts"),
                        DisguisesMod.CONFIG.persistDisguises)
                .setDefaultValue(false)
                .setTooltip(Component.literal("Save and restore active disguises when the server restarts."))
                .setSaveConsumer(v -> DisguisesMod.CONFIG.persistDisguises = v)
                .build());

        // ---- Permissions ----
        ConfigCategory perms = builder.getOrCreateCategory(Component.literal("Permission Levels"));

        perms.addEntry(entries
                .startIntSlider(Component.literal("Self Disguise OP Level"),
                        DisguisesMod.CONFIG.permLevelSelf, 0, 4)
                .setDefaultValue(0)
                .setTooltip(Component.literal("OP level required to disguise yourself (fallback when LuckPerms absent)."))
                .setSaveConsumer(v -> DisguisesMod.CONFIG.permLevelSelf = v)
                .build());

        perms.addEntry(entries
                .startIntSlider(Component.literal("Admin OP Level"),
                        DisguisesMod.CONFIG.permLevelAdmin, 0, 4)
                .setDefaultValue(3)
                .setSaveConsumer(v -> DisguisesMod.CONFIG.permLevelAdmin = v)
                .build());

        return builder.build();
    }
}
