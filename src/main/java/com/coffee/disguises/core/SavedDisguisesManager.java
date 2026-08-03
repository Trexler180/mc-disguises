package com.coffee.disguises.core;

import com.coffee.disguises.DisguisesMod;
import com.coffee.disguises.util.JsonFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Stores named disguise presets per player (by UUID).
 *
 * Data format in disguises-saved.json:
 * {
 *   "PlayerUUID": {
 *     "presetName": "disguise_type [flags...]",
 *     ...
 *   },
 *   ...
 * }
 */
public class SavedDisguisesManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("disguises-saved.json");

    // INSTANCE must be declared AFTER the fields above: static initializers run
    // in textual order and the constructor calls load(), which reads SAVE_PATH.
    // Declaring INSTANCE first left SAVE_PATH null during construction, throwing
    // ExceptionInInitializerError out of the first /savedisguise command and
    // bricking the class (NoClassDefFoundError) for the rest of the session.
    public static final SavedDisguisesManager INSTANCE = new SavedDisguisesManager();

    // uuid → (presetName → "type [flags]")
    private final Map<UUID, Map<String, String>> data = new LinkedHashMap<>();

    private SavedDisguisesManager() {
        load();
    }

    /**
     * Save a preset for a player.
     *
     * @param maxPresets per-player cap, or 0 for unlimited. Overwriting an existing
     *                   preset is always permitted, so hitting the cap never makes
     *                   a player's current presets uneditable.
     * @return false if the cap would be exceeded by adding a new preset.
     */
    public boolean save(UUID playerUuid, String name, String disguiseString, int maxPresets) {
        String key = key(name);
        Map<String, String> playerData = data.get(playerUuid);
        if (playerData != null && maxPresets > 0
                && playerData.size() >= maxPresets && !playerData.containsKey(key)) {
            return false;
        }
        data.computeIfAbsent(playerUuid, k -> new LinkedHashMap<>()).put(key, disguiseString);
        persist();
        return true;
    }

    /** Get a preset for a player, or null if not found. */
    public String get(UUID playerUuid, String name) {
        Map<String, String> playerData = data.get(playerUuid);
        return playerData != null ? playerData.get(key(name)) : null;
    }

    /** Delete a preset for a player. Returns true if it existed. */
    public boolean delete(UUID playerUuid, String name) {
        Map<String, String> playerData = data.get(playerUuid);
        if (playerData == null) return false;
        boolean removed = playerData.remove(key(name)) != null;
        if (removed) persist();
        return removed;
    }

    /**
     * Preset names are case-insensitive. Locale.ROOT rather than the default
     * locale: under a Turkish locale "I".toLowerCase() is "ı", which would make
     * a preset saved on one server unreachable on another.
     */
    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** List all preset names for a player. */
    public List<String> list(UUID playerUuid) {
        Map<String, String> playerData = data.get(playerUuid);
        if (playerData == null) return Collections.emptyList();
        return new ArrayList<>(playerData.keySet());
    }

    private void persist() {
        // Convert UUID keys to strings for JSON
        Map<String, Map<String, String>> jsonMap = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<String, String>> e : data.entrySet()) {
            jsonMap.put(e.getKey().toString(), e.getValue());
        }
        try {
            JsonFiles.write(SAVE_PATH, GSON, jsonMap);
        } catch (Exception e) {
            DisguisesMod.LOGGER.error("Failed to save disguise presets", e);
        }
    }

    /**
     * Never lets an exception escape: a corrupt/unreadable presets file must not
     * take the command (or the server) down with it — presets just start empty.
     */
    private void load() {
        try {
            if (!Files.exists(SAVE_PATH)) return;
            try (Reader r = Files.newBufferedReader(SAVE_PATH)) {
                Type type = new TypeToken<Map<String, Map<String, String>>>() {}.getType();
                Map<String, Map<String, String>> jsonMap = GSON.fromJson(r, type);
                if (jsonMap != null) {
                    for (Map.Entry<String, Map<String, String>> e : jsonMap.entrySet()) {
                        try {
                            UUID uuid = UUID.fromString(e.getKey());
                            data.put(uuid, new LinkedHashMap<>(e.getValue()));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            DisguisesMod.LOGGER.error("Failed to load disguise presets", e);
        }
    }
}
