package com.coffee.disguises;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisguisesConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void malformedJsonFallsBackToDefaultsAndRepairsFile() throws Exception {
        Path configPath = tempDir.resolve("disguises.json");
        Files.writeString(configPath, "{ definitely-not-json");

        DisguisesConfig config = DisguisesConfig.load(configPath);

        assertTrue(config.showDisguiseActionBar);
        assertDoesNotThrow(() -> JsonParser.parseString(Files.readString(configPath)));
    }

    @Test
    void missingFieldsKeepTheirDefaults() throws Exception {
        Path configPath = tempDir.resolve("disguises.json");
        Files.writeString(configPath, "{\"showDisguiseActionBar\":false}");

        DisguisesConfig config = DisguisesConfig.load(configPath);

        assertFalse(config.showDisguiseActionBar);
        assertTrue(config.disguiseSounds);
        assertFalse(config.disguiseNamesInChat);
        assertFalse(config.disguiseNamesInDeathMessages);
        assertTrue(config.revealRealNameOnHover);
    }

    @Test
    void loadBackFillsMissingFieldsIntoTheFile() throws Exception {
        // Regression: the back-fill save ran while the reader still held the file
        // open, which Windows rejects, so new fields never reached disk there.
        Path configPath = tempDir.resolve("disguises.json");
        Files.writeString(configPath, "{\"showDisguiseActionBar\":false}");

        DisguisesConfig.load(configPath);

        var saved = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
        assertFalse(saved.get("showDisguiseActionBar").getAsBoolean());
        assertTrue(saved.has("disguiseSounds"));
        assertFalse(Files.exists(tempDir.resolve("disguises.json.tmp")));
    }
}
