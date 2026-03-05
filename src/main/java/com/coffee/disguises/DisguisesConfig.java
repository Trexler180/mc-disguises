package com.coffee.disguises;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DisguisesConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("disguises.json");

    // ---- General ----
    /** Show the disguised player/entity type in the action bar */
    public boolean showDisguiseActionBar = true;

    /** How many ticks between action bar reminder messages (0 = only on change) */
    public int actionBarIntervalTicks = 100;

    // ---- Sounds ----
    /** Substitute disguise-type sounds for ambient, hurt, death, step */
    public boolean disguiseSounds = true;

    // ---- Self-disguise ----
    /** Whether players see their own disguise by default (requires selfDisguise command) */
    public boolean selfDisguiseDefault = false;

    // ---- Player disguises ----
    /** Show the fake player entry in the tab list while it loads the skin */
    public boolean showDisguiseInTab = false;

    /** Ticks before removing the fake player entry from tab list after spawn */
    public int tabRemoveDelayTicks = 20;

    // ---- Vanish interaction ----
    /** When a disguised entity is vanished from an observer, send them nothing (no disguise, no real entity) */
    public boolean vanishedEntitiesHidden = true;

    // ---- Equipment ----
    /** Whether to show the real entity's equipment through the disguise */
    public boolean showEquipmentThroughDisguise = false;

    // ---- Persistence ----
    /** Save and restore disguises across server restarts */
    public boolean persistDisguises = false;

    // ---- Restrictions ----
    /** Entity types that cannot be used as disguises (e.g. "wither", "ender_dragon") */
    public List<String> disabledEntityTypes = new ArrayList<>();

    /** If true, per-type permission nodes (disguises.type.<type>) are enforced */
    public boolean enforceTypePermissions = false;

    // ---- Permission levels (fallback when LuckPerms is absent) ----
    public int permLevelSelf       = 0;
    public int permLevelOthers     = 2;
    public int permLevelEntity     = 2;
    public int permLevelRadius     = 2;
    public int permLevelAdmin      = 3;

    // -------------------------------------------------------------------------

    public static DisguisesConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
                DisguisesConfig cfg = GSON.fromJson(r, DisguisesConfig.class);
                if (cfg != null) {
                    cfg.save(); // back-fill any new fields
                    return cfg;
                }
            } catch (IOException e) {
                DisguisesMod.LOGGER.error("Failed to read disguises config, using defaults", e);
            }
        }
        DisguisesConfig defaults = new DisguisesConfig();
        defaults.save();
        return defaults;
    }

    public void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(this, w);
        } catch (IOException e) {
            DisguisesMod.LOGGER.error("Failed to save disguises config", e);
        }
    }
}
