package de.htwsaar.minicdn.edge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Service für das Caching von Dateiinhalten im Edge-Server.
 * Verwaltet einen In-Memory-Cache mit konfigurierbarer TTL und maximaler Anzahl von Einträgen.
 */
@Service
@Profile("edge")
public class EdgeCacheService {

    @Value("${edge.cache.ttl-ms:60000}")
    private long ttlMs;

    @Value("${edge.cache.max-entries:100}")
    private int maxEntries;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Eintrag im Cache, der den Dateiinhalt, Content-Type, SHA-256-Hash und Ablaufzeit speichert.
     *
     * @param body        der Dateiinhalt als Byte-Array
     * @param contentType der MIME-Type der Datei
     * @param sha256      der SHA-256-Hash der Datei
     * @param expiresAtMs Zeitstempel (in Millisekunden), wann der Eintrag abläuft
     */
    public record CacheEntry(byte[] body, String contentType, String sha256, long expiresAtMs) {}

    /**
     * Gibt einen Cache-Eintrag zurück, wenn dieser vorhanden und noch nicht abgelaufen ist.
     *
     * @param path der Dateipfad als Schlüssel
     * @return der Cache-Eintrag oder {@code null} bei Cache-Miss oder abgelaufenem Eintrag
     */
    public CacheEntry getFresh(String path) {
        if (path == null || path.isBlank()) return null;

        CacheEntry e = cache.get(path);
        if (e == null) return null;

        if (isExpired(e)) {
            cache.remove(path);
            return null;
        }
        return e;
    }

    /**
     * Speichert einen neuen Eintrag im Cache.
     * Führt ggf. eine Eviction durch, wenn die maximale Anzahl von Einträgen erreicht ist.
     *
     * @param path        der Dateipfad als Schlüssel
     * @param body        der Dateiinhalt als Byte-Array
     * @param contentType der MIME-Type der Datei
     * @param sha256      der SHA-256-Hash der Datei
     */
    public void put(String path, byte[] body, String contentType, String sha256) {
        if (path == null || path.isBlank()) return;
        if (body == null) return;
        if (sha256 == null || sha256.isBlank()) return;

        evictIfFull();

        long expiresAt = System.currentTimeMillis() + Math.max(0, ttlMs);
        cache.put(path, new CacheEntry(body, contentType, sha256, expiresAt));
    }

    /**
     * Entfernt einen Eintrag aus dem Cache.
     *
     * @param path der Dateipfad des zu entfernenden Eintrags
     */
    public void invalidate(String path) {
        if (path == null || path.isBlank()) return;
        cache.remove(path);
    }

    /**
     * Prüft, ob ein Cache-Eintrag abgelaufen ist.
     *
     * @param e der zu prüfende Cache-Eintrag
     * @return {@code true}, wenn der Eintrag abgelaufen ist
     */
    private boolean isExpired(CacheEntry e) {
        return e.expiresAtMs() <= System.currentTimeMillis();
    }

    /**
     * Leert den gesamten Cache, wenn die maximale Anzahl von Einträgen erreicht ist.
     * Verwendet eine einfache Eviction-Strategie (vollständiges Löschen statt LRU/LFU).
     */
    private void evictIfFull() {
        if (maxEntries > 0 && cache.size() >= maxEntries) {
            // Simple policy (wie vorher): alles löschen statt LRU/LFU
            cache.clear();
        }
    }
}
