package com.coffee.disguises.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerNamesTest {

    @Test
    void acceptsOrdinaryAndBedrockStyleNames() {
        assertTrue(PlayerNames.isValid("Notch"));
        assertTrue(PlayerNames.isValid("a"));
        assertTrue(PlayerNames.isValid("Sixteen_chars_12"));
        assertTrue(PlayerNames.isValid(".BedrockUser")); // Floodgate prefix
    }

    @Test
    void rejectsNamesThatWouldKickObservers() {
        assertFalse(PlayerNames.isValid(null));
        assertFalse(PlayerNames.isValid(""));
        assertFalse(PlayerNames.isValid("seventeen_chars_1"));
        assertFalse(PlayerNames.isValid("has space"));
        assertFalse(PlayerNames.isValid("§cRed"));
        assertFalse(PlayerNames.isValid("tab\tname"));
    }

    @Test
    void forProfileAlwaysProducesAWireSafeName() {
        assertEquals("Notch", PlayerNames.forProfile("Notch"));
        assertEquals("abcdefghijklmnop", PlayerNames.forProfile("abcdefghijklmnopqrstuvwxyz"));
        assertEquals("hasspace", PlayerNames.forProfile("has space"));
        assertEquals("cRed", PlayerNames.forProfile("§cRed"));
        assertEquals("Player", PlayerNames.forProfile(null));
        assertEquals("Player", PlayerNames.forProfile("   "));
        assertTrue(PlayerNames.isValid(PlayerNames.forProfile("a b c d e f g h i j k l m n o p q r")));
    }
}
