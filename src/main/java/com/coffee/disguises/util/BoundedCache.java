package com.coffee.disguises.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small thread-safe access-ordered cache with a fixed entry limit.
 */
public final class BoundedCache<K, V> {

    private final Map<K, V> entries;

    public BoundedCache(int maximumSize) {
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("maximumSize must be positive");
        }
        entries = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maximumSize;
            }
        });
    }

    public V get(K key) {
        return entries.get(key);
    }

    public void put(K key, V value) {
        entries.put(key, value);
    }

    public V remove(K key) {
        return entries.remove(key);
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }
}
