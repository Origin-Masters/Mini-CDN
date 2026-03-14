package de.htwsaar.minicdn.edge.application.file;

import de.htwsaar.minicdn.common.util.Sha256Util;
import de.htwsaar.minicdn.edge.application.config.EdgeConfigService;
import de.htwsaar.minicdn.edge.application.config.TtlPolicyService;
import de.htwsaar.minicdn.edge.domain.exception.IntegrityCheckFailedException;
import de.htwsaar.minicdn.edge.domain.exception.OriginAccessException;
import de.htwsaar.minicdn.edge.domain.model.CacheDecision;
import de.htwsaar.minicdn.edge.domain.model.CacheEntry;
import de.htwsaar.minicdn.edge.domain.model.FilePayload;
import de.htwsaar.minicdn.edge.domain.model.OriginContent;
import de.htwsaar.minicdn.edge.domain.model.OriginMetadata;
import de.htwsaar.minicdn.edge.domain.model.ReplacementStrategy;
import de.htwsaar.minicdn.edge.domain.port.EdgeCache;
import de.htwsaar.minicdn.edge.domain.port.EdgeCacheFactory;
import de.htwsaar.minicdn.edge.domain.port.EdgeCacheStatePort;
import de.htwsaar.minicdn.edge.domain.port.OriginClient;
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
    private final EdgeCacheFactory cacheFactory;
    private final EdgeCacheStatePort cacheStatePort;
    private final Clock clock;

    /**
     * Cache-Store – wird bei Strategie-Wechsel live ausgetauscht.
     * {@code volatile} reicht, da die Implementierungen intern synchronisiert sind.
     */
    private volatile EdgeCache cacheStore;

    private volatile ReplacementStrategy activeStrategy;

    /**
     * Erstellt den fachlichen Dateiservice der Edge-Node.
     *
     * @param originClient fachlicher Origin-Port
     * @param configService Runtime-Konfiguration
     * @param ttlPolicyService TTL-Policies
     * @param cacheFactory Fabrik für Cache-Implementierungen
     * @param cacheStatePort Persistenzport für Cache-Snapshots
     * @param clock Zeitquelle
     */
    public EdgeFileService(
            OriginClient originClient,
            EdgeConfigService configService,
            TtlPolicyService ttlPolicyService,
            EdgeCacheFactory cacheFactory,
            EdgeCacheStatePort cacheStatePort,
            Clock clock) {

        this.originClient = Objects.requireNonNull(originClient, "originClient must not be null");
        this.configService = Objects.requireNonNull(configService, "configService must not be null");
        this.ttlPolicyService = Objects.requireNonNull(ttlPolicyService, "ttlPolicyService must not be null");
        this.cacheFactory = Objects.requireNonNull(cacheFactory, "cacheFactory must not be null");
        this.cacheStatePort = Objects.requireNonNull(cacheStatePort, "cacheStatePort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.activeStrategy = ReplacementStrategy.LRU;
        this.cacheStore = cacheFactory.create(activeStrategy);
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

        CacheEntry cached = cacheStore.getFresh(clean, now);
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
                new CacheEntry(origin.body(), origin.contentType(), actualSha, now + ttlMs),
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

        CacheEntry cached = cacheStore.getFresh(clean, now);
        if (cached != null) {
            return new FilePayload(clean, new byte[0], cached.contentType(), cached.sha256(), CacheDecision.HIT);
        }

        OriginMetadata metadata = originClient.fetchMetadata(clean);
        validateOriginMetadata(metadata);

        return new FilePayload(clean, new byte[0], metadata.contentType(), metadata.sha256(), CacheDecision.MISS);
    }

    /**
     * Invalidiert genau eine Datei im Cache.
     *
     * @param path relativer Dateipfad
     * @return {@code true}, wenn ein Eintrag entfernt wurde
     */
    public boolean invalidateFile(String path) {
        boolean removed = cacheStore.remove(normalizePath(path));
        persistCacheSnapshot(clock.millis());
        return removed;
    }

    /**
     * Invalidiert alle Cache-Einträge unterhalb eines Präfixes.
     *
     * @param prefix Pfad-Präfix
     * @return Anzahl entfernter Einträge
     */
    public int invalidatePrefix(String prefix) {
        int removed = cacheStore.removeByPrefix(normalizePath(prefix));
        persistCacheSnapshot(clock.millis());
        return removed;
    }

    /** Leert den kompletten Cache und persistiert den neuen Zustand. */
    public void clearCache() {
        cacheStore.clear();
        persistCacheSnapshot(clock.millis());
    }

    /**
     * Gibt die aktuelle Anzahl der Cache-Einträge zurück.
     *
     * @return Anzahl gespeicherter Einträge
     */
    public int cacheSize() {
        return cacheStore.size();
    }

    /**
     * Lädt persistierten Cache-Zustand und setzt ihn in den aktiven Cache.
     */
    public void restoreCacheFromDisk() {
        ensureStrategy();
        long now = clock.millis();
        Map<String, CacheEntry> restored = cacheStatePort.load();
        if (restored.isEmpty()) return;
        var cfg = configService.current();
        int loaded = 0;
        for (var e : restored.entrySet()) {
            String key = e.getKey();
            CacheEntry value = e.getValue();
            if (key == null || key.isBlank() || value == null) continue;
            if (value.expiresAtMs() <= now) continue;
            cacheStore.put(key, value, cfg.maxEntries(), now);
            loaded++;
        }
        if (loaded > 0) {
            log.info("Wiederhergestellt: {} Cache-Einträge", loaded);
        }
    }

    /** Stellt sicher, dass die aktive Cache-Implementierung zur konfigurierten Strategie passt. */
    private void ensureStrategy() {
        ReplacementStrategy strategy = configService.current().replacementStrategy();
        if (strategy != activeStrategy) {
            cacheStore = cacheFactory.create(strategy);
            activeStrategy = strategy;
        }
    }

    /** Persistiert den aktuellen Cache-Snapshot und toleriert dabei I/O-Fehler. */
    private void persistCacheSnapshot(long now) {
        try {
            cacheStatePort.save(cacheStore.snapshot(), now);
        } catch (Exception ex) {
            log.warn("Persistieren des Cache-Zustands fehlgeschlagen", ex);
        }
    }

    /** Prüft, ob der Origin-Response die fachlich erforderlichen Hash-Metadaten enthält. */
    private void validateOriginContent(OriginContent origin) {
        if (origin.sha256() == null || origin.sha256().isBlank()) {
            throw new OriginAccessException(
                    OriginAccessException.Reason.INVALID_RESPONSE, "Origin response missing sha256");
        }
    }

    /** Prüft, ob die Origin-Metadaten die fachlich erforderlichen Hash-Informationen enthalten. */
    private void validateOriginMetadata(OriginMetadata metadata) {
        if (metadata.sha256() == null || metadata.sha256().isBlank()) {
            throw new OriginAccessException(
                    OriginAccessException.Reason.INVALID_RESPONSE, "Origin metadata missing sha256");
        }
    }

    /** Vergleicht erwarteten und tatsächlichen SHA-256-Hash. */
    private void validateSha256(String expected, String actual) {
        if (!expected.equalsIgnoreCase(actual)) {
            throw new IntegrityCheckFailedException("Integritätsprüfung fehlgeschlagen: SHA-256 stimmt nicht überein");
        }
    }

    /**
     * Normalisiert einen relativen Dateipfad für die fachliche Verarbeitung.
     *
     * @param path roher Dateipfad
     * @return normalisierter relativer Pfad
     */
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
