package de.htwsaar.minicdn.edge.cache;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU Cache-Store via {@link LinkedHashMap} mit {@code accessOrder=true}.
 *
 * <p>Thread-Safety: einfaches {@code synchronized} – ausreichend für Demobetrieb.</p>
 */
public final class LruCacheStore implements CacheStore {

    private final Map<String, CachedFile> map = new LinkedHashMap<>(16, 0.75f, true);

    @Override
    public synchronized CachedFile getFresh(String key, long nowMs) {
        if (key == null || key.isBlank()) return null;
        CachedFile v = map.get(key);
        if (v == null) return null;
        if (v.expiresAtMs() <= nowMs) {
            map.remove(key);
            return null;
        }
        return v;
    }

    @Override
    public synchronized void put(String key, CachedFile value, int maxEntries, long nowMs) {
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

    private void evictIfNeeded(int maxEntries, long nowMs) {
        if (maxEntries <= 0) return;
        // opportunistisch abgelaufene Einträge entfernen
        map.entrySet().removeIf(e -> e.getValue().expiresAtMs() <= nowMs);
        // LRU-Eviction: ältester Zugriff fliegt zuerst raus
        while (map.size() > maxEntries) {
            Iterator<String> it = map.keySet().iterator();
            if (!it.hasNext()) break;
            it.next();
            it.remove();
        }
    }
}
