package com.coffee.disguises.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundedCacheTest {

    @Test
    void evictsLeastRecentlyUsedEntry() {
        BoundedCache<String, Integer> cache = new BoundedCache<>(2);
        cache.put("first", 1);
        cache.put("second", 2);

        assertEquals(1, cache.get("first"));
        cache.put("third", 3);

        assertNull(cache.get("second"));
        assertEquals(1, cache.get("first"));
        assertEquals(3, cache.get("third"));
    }

    @Test
    void clearRemovesEveryEntry() {
        BoundedCache<String, Integer> cache = new BoundedCache<>(2);
        cache.put("first", 1);
        cache.put("second", 2);

        cache.clear();

        assertEquals(0, cache.size());
    }

    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedCache<>(0));
    }
}
