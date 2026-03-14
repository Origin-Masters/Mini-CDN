package de.htwsaar.minicdn.edge.infrastructure.cache;

import de.htwsaar.minicdn.edge.domain.model.CacheEntry;
import java.util.HashMap;
import java.util.Map;

/**
 * LFU-Cache mit Frequenzzählung pro Schlüssel.
 *
 * <p>Bei Eviction wird der Eintrag mit der kleinsten Zugriffszahl entfernt.
 * Bei Gleichstand entscheidet der älteste Zugriff.</p>
 * <p>Threadsicherheit erfolgt bewusst über einfaches {@code synchronized}.</p>
 */
public final class LfuCacheStore implements CacheStore {

    /** Interner Zustand je Cache-Schlüssel. */
    private static final class Node {
        CacheEntry value;
        long freq;
        long lastAccessCounter;

        Node(CacheEntry value, long counter) {
            this.value = value;
            this.freq = 1;
            this.lastAccessCounter = counter;
        }
    }

    private final Map<String, Node> map = new HashMap<>();
    private long counter = 0;

    @Override
    public synchronized CacheEntry getFresh(String key, long nowMs) {
        if (key == null || key.isBlank()) return null;
        Node n = map.get(key);
        if (n == null) return null;
        if (n.value.expiresAtMs() <= nowMs) {
            map.remove(key);
            return null;
        }
        n.freq++;
        n.lastAccessCounter = ++counter;
        return n.value;
    }

    @Override
    public synchronized void put(String key, CacheEntry value, int maxEntries, long nowMs) {
        if (key == null || key.isBlank() || value == null) return;
        Node existing = map.get(key);
        if (existing != null) {
            existing.value = value;
            existing.freq++;
            existing.lastAccessCounter = ++counter;
        } else {
            map.put(key, new Node(value, ++counter));
        }
        evictIfNeeded(maxEntries, nowMs);
    }

    @Override
    public synchronized boolean remove(String key) {
        if (key == null || key.isBlank()) return false;
        return map.remove(key) != null;
    }

    @Override
    public synchronized int removeByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return 0;
        int before = map.size();
        map.keySet().removeIf(k -> k.startsWith(prefix));
        return before - map.size();
    }

    @Override
    public synchronized void clear() {
        map.clear();
    }

    @Override
    public synchronized int size() {
        return map.size();
    }

    @Override
    public synchronized Map<String, CacheEntry> snapshot() {
        Map<String, CacheEntry> out = new HashMap<>();
        map.forEach((k, v) -> out.put(k, v.value));
        return Map.copyOf(out);
    }

    private void evictIfNeeded(int maxEntries, long nowMs) {
        if (maxEntries <= 0) return;
        map.entrySet().removeIf(e -> e.getValue().value.expiresAtMs() <= nowMs);
        while (map.size() > maxEntries) {
            String victim = null;
            long minFreq = Long.MAX_VALUE;
            long minCounter = Long.MAX_VALUE;
            for (var e : map.entrySet()) {
                Node n = e.getValue();
                if (n.freq < minFreq || (n.freq == minFreq && n.lastAccessCounter < minCounter)) {
                    minFreq = n.freq;
                    minCounter = n.lastAccessCounter;
                    victim = e.getKey();
                }
            }
            if (victim == null) break;
            map.remove(victim);
        }
    }
}
