package com.coffee.disguises.core;

import com.coffee.disguises.DisguisesMod;
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

    public static final SavedDisguisesManager INSTANCE = new SavedDisguisesManager();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("disguises-saved.json");

    // uuid → (presetName → "type [flags]")
    private final Map<UUID, Map<String, String>> data = new LinkedHashMap<>();

    private SavedDisguisesManager() {
        load();
    }

    /** Save a preset for a player. */
    public void save(UUID playerUuid, String name, String disguiseString) {
        data.computeIfAbsent(playerUuid, k -> new LinkedHashMap<>())
                .put(name.toLowerCase(), disguiseString);
        persist();
    }

    /** Get a preset for a player, or null if not found. */
    public String get(UUID playerUuid, String name) {
        Map<String, String> playerData = data.get(playerUuid);
        return playerData != null ? playerData.get(name.toLowerCase()) : null;
    }

    /** Delete a preset for a player. Returns true if it existed. */
    public boolean delete(UUID playerUuid, String name) {
        Map<String, String> playerData = data.get(playerUuid);
        if (playerData == null) return false;
        boolean removed = playerData.remove(name.toLowerCase()) != null;
        if (removed) persist();
        return removed;
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
        try (Writer w = Files.newBufferedWriter(SAVE_PATH)) {
            GSON.toJson(jsonMap, w);
        } catch (IOException e) {
            DisguisesMod.LOGGER.error("Failed to save disguise presets", e);
        }
    }

    private void load() {
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
        } catch (IOException e) {
            DisguisesMod.LOGGER.error("Failed to load disguise presets", e);
        }
    }
}
