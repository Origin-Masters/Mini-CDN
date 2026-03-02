package de.htwsaar.minicdn.edge.service;

import de.htwsaar.minicdn.common.util.Sha256Util;
import de.htwsaar.minicdn.edge.cache.CacheStore;
import de.htwsaar.minicdn.edge.cache.CachedFile;
import de.htwsaar.minicdn.edge.cache.EdgeCacheStateStore;
import de.htwsaar.minicdn.edge.cache.LfuCacheStore;
import de.htwsaar.minicdn.edge.cache.LruCacheStore;
import de.htwsaar.minicdn.edge.cache.ReplacementStrategy;
import de.htwsaar.minicdn.edge.config.EdgeConfigService;
import de.htwsaar.minicdn.edge.config.TtlPolicyService;
import de.htwsaar.minicdn.edge.domain.CacheDecision;
import de.htwsaar.minicdn.edge.domain.FilePayload;
import de.htwsaar.minicdn.edge.domain.IntegrityCheckFailedException;
import de.htwsaar.minicdn.edge.domain.OriginAccessException;
import de.htwsaar.minicdn.edge.domain.OriginClient;
import de.htwsaar.minicdn.edge.domain.OriginContent;
import de.htwsaar.minicdn.edge.domain.OriginMetadata;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fachlicher Service: liefert Dateien aus Cache oder Origin inkl. Integritätsprüfung.
 *
 * <p>Kein HTTP-Framework-Typ und keine HTTP-Statuscode-Logik hier.
 * Der Service arbeitet ausschließlich gegen den fachlichen Port {@link OriginClient}.</p>
 */
@Service
public class EdgeFileService {

    private static final Logger log = LoggerFactory.getLogger(EdgeFileService.class);

    private final OriginClient originClient;
    private final EdgeConfigService configService;
    private final TtlPolicyService ttlPolicyService;
    private final EdgeCacheStateStore cacheStateStore;
    private final Clock clock;

    /**
     * Cache-Store – wird bei Strategie-Wechsel live ausgetauscht.
     * {@code volatile} reicht, da die Implementierungen intern synchronisiert sind.
     */
    private volatile CacheStore cacheStore;

    public EdgeFileService(
            OriginClient originClient,
            EdgeConfigService configService,
            TtlPolicyService ttlPolicyService,
            EdgeCacheStateStore cacheStateStore,
            Clock clock) {

        this.originClient = Objects.requireNonNull(originClient, "originClient must not be null");
        this.configService = Objects.requireNonNull(configService, "configService must not be null");
        this.ttlPolicyService = Objects.requireNonNull(ttlPolicyService, "ttlPolicyService must not be null");
        this.cacheStateStore = Objects.requireNonNull(cacheStateStore, "cacheStateStore must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.cacheStore = new LruCacheStore();
    }

    /**
     * Liefert eine Datei aus dem Cache oder lädt sie vom Origin.
     *
     * @param path relativer Dateipfad
     * @return Datei-Payload mit HIT/MISS-Information
     */
    public FilePayload getFile(String path) {
        String clean = normalizePath(path);
        long now = clock.millis();
        ensureStrategy();

        CachedFile cached = cacheStore.getFresh(clean, now);
        if (cached != null) {
            return new FilePayload(clean, cached.body(), cached.contentType(), cached.sha256(), CacheDecision.HIT);
        }

        OriginContent origin = originClient.fetchFile(clean);
        validateOriginContent(origin);

        String actualSha = Sha256Util.sha256Hex(origin.body());
        validateSha256(origin.sha256(), actualSha);

        var cfg = configService.current();
        long ttlMs = ttlPolicyService.resolveTtlMs(clean, cfg.defaultTtlMs());

        cacheStore.put(
                clean,
                new CachedFile(origin.body(), origin.contentType(), actualSha, now + ttlMs),
                cfg.maxEntries(),
                now);
        persistCacheSnapshot(now);

        return new FilePayload(clean, origin.body(), origin.contentType(), actualSha, CacheDecision.MISS);
    }

    /**
     * Liefert nur Metadaten einer Datei.
     *
     * @param path relativer Dateipfad
     * @return Payload mit leerem Body und HIT/MISS-Information
     */
    public FilePayload headFile(String path) {
        String clean = normalizePath(path);
        long now = clock.millis();
        ensureStrategy();

        CachedFile cached = cacheStore.getFresh(clean, now);
        if (cached != null) {
            return new FilePayload(clean, new byte[0], cached.contentType(), cached.sha256(), CacheDecision.HIT);
        }

        OriginMetadata metadata = originClient.fetchMetadata(clean);
        validateOriginMetadata(metadata);

        return new FilePayload(clean, new byte[0], metadata.contentType(), metadata.sha256(), CacheDecision.MISS);
    }

    public boolean invalidateFile(String path) {
        boolean removed = cacheStore.remove(normalizePath(path));
        persistCacheSnapshot(clock.millis());
        return removed;
    }

    public int invalidatePrefix(String prefix) {
        int removed = cacheStore.removeByPrefix(normalizePath(prefix));
        persistCacheSnapshot(clock.millis());
        return removed;
    }

    public void clearCache() {
        cacheStore.clear();
        persistCacheSnapshot(clock.millis());
    }

    public int cacheSize() {
        return cacheStore.size();
    }

    /**
     * Lädt persistierten Cache-Zustand und setzt ihn in den aktiven Cache.
     */
    public void restoreCacheFromDisk() {
        ensureStrategy();
        long now = clock.millis();
        Map<String, CachedFile> restored = cacheStateStore.load();
        if (restored.isEmpty()) return;
        var cfg = configService.current();
        int loaded = 0;
        for (var e : restored.entrySet()) {
            String key = e.getKey();
            CachedFile value = e.getValue();
            if (key == null || key.isBlank() || value == null) continue;
            if (value.expiresAtMs() <= now) continue;
            cacheStore.put(key, value, cfg.maxEntries(), now);
            loaded++;
        }
        if (loaded > 0) {
            log.info("Recovered {} cache entries", loaded);
        }
    }

    private void ensureStrategy() {
        ReplacementStrategy strategy = configService.current().replacementStrategy();
        switch (strategy) {
            case LRU -> {
                if (!(cacheStore instanceof LruCacheStore)) {
                    cacheStore = new LruCacheStore();
                }
            }
            case LFU -> {
                if (!(cacheStore instanceof LfuCacheStore)) {
                    cacheStore = new LfuCacheStore();
                }
            }
        }
    }

    private void persistCacheSnapshot(long now) {
        try {
            cacheStateStore.save(cacheStore.snapshot(), now);
        } catch (Exception ex) {
            log.warn("Failed to persist cache state", ex);
        }
    }

    private void validateOriginContent(OriginContent origin) {
        if (origin.sha256() == null || origin.sha256().isBlank()) {
            throw new OriginAccessException(
                    OriginAccessException.Reason.INVALID_RESPONSE, "Origin response missing sha256");
        }
    }

    private void validateOriginMetadata(OriginMetadata metadata) {
        if (metadata.sha256() == null || metadata.sha256().isBlank()) {
            throw new OriginAccessException(
                    OriginAccessException.Reason.INVALID_RESPONSE, "Origin metadata missing sha256");
        }
    }

    private void validateSha256(String expected, String actual) {
        if (!expected.equalsIgnoreCase(actual)) {
            throw new IntegrityCheckFailedException("Integrity check failed: sha256 mismatch");
        }
    }

    private static String normalizePath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }

        String p = path.trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }

        if (p.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }

        return p;
    }
}
