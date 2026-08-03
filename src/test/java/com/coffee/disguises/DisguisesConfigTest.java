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
}
