package com.coffee.disguises;

import com.coffee.disguises.util.JsonFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DisguisesConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
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

    // ---- Display names ----
    /**
     * Show a player's disguise name as the sender of normal signed chat messages.
     * This changes only the rendered chat decoration; signatures, reporting identity,
     * commands, logs, scoreboards and the player's GameProfile remain unchanged.
     */
    public boolean disguiseNamesInChat = false;

    /**
     * Use the global disguise name in player-facing system messages, including
     * death and advancement announcements. Observer-specific overrides are not
     * used because vanilla normally constructs one message for all recipients.
     *
     * The field keeps its original name for config compatibility.
     */
    public boolean disguiseNamesInDeathMessages = false;

    /** Reveal the real account name when hovering a substituted disguise name. */
    public boolean revealRealNameOnHover = true;

    // ---- Vanish interaction ----
    /** When a disguised entity is vanished from an observer, send them nothing (no disguise, no real entity) */
    public boolean vanishedEntitiesHidden = true;

    // ---- Equipment ----
    /** Whether to show the real entity's equipment through the disguise */
    public boolean showEquipmentThroughDisguise = false;

    // ---- Persistence ----
    /** Save and restore disguises across server restarts */
    public boolean persistDisguises = false;

    // ---- Auto-undisguise ----
    /** Automatically remove a player's disguise when they die */
    public boolean undisguiseOnDeath = false;

    /** Automatically remove a player's disguise when they change dimension */
    public boolean undisguiseOnWorldChange = false;

    // ---- Restrictions ----
    /** Entity types that cannot be used as disguises (e.g. "wither", "ender_dragon") */
    public List<String> disabledEntityTypes = new ArrayList<>();

    /** If true, per-type permission nodes (disguises.type.<type>) are enforced */
    public boolean enforceTypePermissions = false;

    /**
     * Maximum /savedisguise presets a single player may keep (0 = unlimited).
     * Presets live in disguises-saved.json, which is rewritten in full on every
     * save, so an unbounded count is both a disk-growth and a write-cost problem.
     * Overwriting an existing preset is always allowed, even at the limit.
     */
    public int maxSavedPresetsPerPlayer = 50;

    // ---- Permission levels (fallback when LuckPerms is absent) ----
    // When LuckPerms is installed these are ignored in favour of permission nodes:
    //   disguises.disguise.self, disguises.disguise.others,
    //   disguises.disguise.entity, disguises.disguise.radius,
    //   disguises.viewself, disguises.type.<type>
    // Vanilla op levels: 1=spawn-protection bypass, 2=standard commands, 3=player mgmt, 4=full admin
    public int permLevelSelf       = 2;
    public int permLevelOthers     = 2;
    public int permLevelEntity     = 2;
    public int permLevelRadius     = 2;
    public int permLevelAdmin      = 3;

    /**
     * Fallback op level for disguises.type.&lt;type&gt; when enforceTypePermissions is on
     * and no permissions mod is installed. Defaults to 4: without per-node checks
     * there is no way to distinguish one type from another, so the safe reading of
     * "enforce type permissions" is to allow only full admins.
     */
    public int permLevelType       = 4;

    // -------------------------------------------------------------------------

    public static DisguisesConfig load() {
        return load(configPath());
    }

    static DisguisesConfig load(Path path) {
        if (Files.exists(path)) {
            try (Reader r = Files.newBufferedReader(path)) {
                DisguisesConfig cfg = GSON.fromJson(r, DisguisesConfig.class);
                if (cfg != null) {
                    cfg.save(path); // back-fill any new fields
                    return cfg;
                }
            } catch (IOException | JsonParseException e) {
                DisguisesMod.LOGGER.error("Failed to read disguises config, using defaults", e);
            }
        }
        DisguisesConfig defaults = new DisguisesConfig();
        defaults.save(path);
        return defaults;
    }

    public void save() {
        save(configPath());
    }

    private void save(Path path) {
        try {
            JsonFiles.write(path, GSON, this);
        } catch (IOException e) {
            DisguisesMod.LOGGER.error("Failed to save disguises config", e);
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("disguises.json");
    }
}
