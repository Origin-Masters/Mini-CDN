package de.htwsaar.minicdn.edge.infrastructure.cache;

import de.htwsaar.minicdn.edge.domain.model.CacheEntry;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU-Cache auf Basis einer {@link LinkedHashMap} mit {@code accessOrder=true}.
 *
 * <p>Threadsicherheit erfolgt bewusst über einfaches {@code synchronized}.</p>
 */
public final class LruCacheStore implements CacheStore {

    private final Map<String, CacheEntry> map = new LinkedHashMap<>(16, 0.75f, true);

    @Override
    public synchronized CacheEntry getFresh(String key, long nowMs) {
        if (key == null || key.isBlank()) return null;
        CacheEntry v = map.get(key);
        if (v == null) return null;
        if (v.expiresAtMs() <= nowMs) {
            map.remove(key);
            return null;
        }
        return v;
    }

    @Override
    public synchronized void put(String key, CacheEntry value, int maxEntries, long nowMs) {
        if (key == null || key.isBlank() || value == null) return;
        map.put(key, value);
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
        return Map.copyOf(map);
    }

    private void evictIfNeeded(int maxEntries, long nowMs) {
        if (maxEntries <= 0) return;
        // Opportunistisch abgelaufene Einträge entfernen.
        map.entrySet().removeIf(e -> e.getValue().expiresAtMs() <= nowMs);
        // LRU-Eviction: Der am längsten nicht genutzte Eintrag wird zuerst entfernt.
        while (map.size() > maxEntries) {
            Iterator<String> it = map.keySet().iterator();
            if (!it.hasNext()) break;
            it.next();
            it.remove();
        }
    }
}
