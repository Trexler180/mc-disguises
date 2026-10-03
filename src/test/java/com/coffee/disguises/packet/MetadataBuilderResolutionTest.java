package com.coffee.disguises.packet;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MetadataBuilder finds vanilla's EntityDataAccessor fields by name at runtime.
 * When a Minecraft update renames or removes one, the matching disguise flag
 * silently stops working — the only symptom is a log line.  This turns that into
 * a test failure on the primary target, so a version bump can't ship it unnoticed.
 */
class MetadataBuilderResolutionTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyAccessorFieldResolvesByName() throws Exception {
        MetadataBuilder.init();

        List<String> unresolved = new ArrayList<>();
        for (Field f : MetadataBuilder.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != Field.class) continue;
            if (!f.getName().startsWith("F_")) continue;
            f.setAccessible(true);
            if (f.get(null) == null) unresolved.add(f.getName());
        }

        assertEquals(List.of(), unresolved, "accessor fields not found");
        // An index-scan hit can be an unrelated field of another type; a rename must
        // be handled by adding the new name to the resolve() call.
        assertEquals(List.of(), List.copyOf(MetadataBuilder.SCAN_RESOLVED), "accessor fields only found by index scan");
    }
}
