package de.htwsaar.minicdn.edge.service;

import de.htwsaar.minicdn.common.util.Sha256Util;
import de.htwsaar.minicdn.edge.cache.CacheStore;
import de.htwsaar.minicdn.edge.cache.CachedFile;
import de.htwsaar.minicdn.edge.cache.LfuCacheStore;
import de.htwsaar.minicdn.edge.cache.LruCacheStore;
import de.htwsaar.minicdn.edge.cache.ReplacementStrategy;
import de.htwsaar.minicdn.edge.config.EdgeConfigService;
import de.htwsaar.minicdn.edge.config.TtlPolicyService;
import de.htwsaar.minicdn.edge.domain.CacheDecision;
import de.htwsaar.minicdn.edge.domain.FilePayload;
import de.htwsaar.minicdn.edge.domain.OriginClient;
import de.htwsaar.minicdn.edge.domain.OriginFileResponse;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Fachlicher Service: liefert Dateien aus Cache oder Origin inkl. Integritätsprüfung.
 *
 * <p><b>Kein</b> Spring-Web-Typ und kein {@code RestTemplate} hier –
 * der Zugriff auf den Origin erfolgt ausschließlich über den {@link OriginClient}-Port
 * (Adapter-Prinzip).</p>
 */
@Service
public class EdgeFileService {

    private final OriginClient originClient;
    private final EdgeConfigService configService;
    private final TtlPolicyService ttlPolicyService;
    private final Clock clock;

    /**
     * Cache-Store – wird bei Strategie-Wechsel live ausgetauscht.
     * {@code volatile} reicht, da die Store-Implementierungen intern {@code synchronized} sind.
     */
    private volatile CacheStore cacheStore;

    /**
     * Erstellt den Service mit Constructor Injection.
     *
     * @param originClient    Port zum Origin (darf nicht {@code null} sein)
     * @param configService   Live-Konfiguration (darf nicht {@code null} sein)
     * @param ttlPolicyService TTL-Policies (darf nicht {@code null} sein)
     * @param clock           Zeitquelle (darf nicht {@code null} sein)
     */
    public EdgeFileService(
            OriginClient originClient,
            EdgeConfigService configService,
            TtlPolicyService ttlPolicyService,
            Clock clock) {

        this.originClient = Objects.requireNonNull(originClient, "originClient must not be null");
        this.configService = Objects.requireNonNull(configService, "configService must not be null");
        this.ttlPolicyService = Objects.requireNonNull(ttlPolicyService, "ttlPolicyService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.cacheStore = new LruCacheStore(); // Default; wird durch Config überschrieben
    }

    /**
     * Liefert eine Datei aus dem Cache oder lädt sie vom Origin.
     * Abgelaufene oder nicht vorhandene Einträge werden automatisch neu geladen.
     *
     * @param path relativer Dateipfad
     * @return {@link FilePayload} mit HIT/MISS-Entscheidung
     * @throws EdgeUpstreamException bei Origin-Fehlern oder fehlgeschlagener Integritätsprüfung
     */
    public FilePayload getFile(String path) {
        String clean = normalizePath(path);
        long now = clock.millis();
        ensureStrategy();

        CachedFile cached = cacheStore.getFresh(clean, now);
        if (cached != null) {
            return new FilePayload(clean, cached.body(), cached.contentType(), cached.sha256(), CacheDecision.HIT);
        }

        OriginFileResponse origin = originClient.fetchFile(clean);
        validateOriginResponse(origin);

        String actualSha = Sha256Util.sha256Hex(origin.body());
        validateSha256(origin.sha256(), actualSha);

        var cfg = configService.current();
        long ttlMs = ttlPolicyService.resolveTtlMs(clean, cfg.defaultTtlMs());
        cacheStore.put(
                clean,
                new CachedFile(origin.body(), origin.contentType(), actualSha, now + ttlMs),
                cfg.maxEntries(),
                now);

        return new FilePayload(clean, origin.body(), origin.contentType(), actualSha, CacheDecision.MISS);
    }

    /**
     * Liefert nur Metadaten (HEAD) einer Datei.
     *
     * @param path relativer Dateipfad
     * @return {@link FilePayload} mit leerem Body-Ersatz, HIT/MISS-Entscheidung
     */
    public FilePayload headFile(String path) {
        String clean = normalizePath(path);
        long now = clock.millis();
        ensureStrategy();

        CachedFile cached = cacheStore.getFresh(clean, now);
        if (cached != null) {
            return new FilePayload(clean, cached.body(), cached.contentType(), cached.sha256(), CacheDecision.HIT);
        }

        OriginFileResponse origin = originClient.headFile(clean);
        if (origin.statusCode() < 200 || origin.statusCode() >= 300) {
            throw new EdgeUpstreamException("Origin HEAD responded with " + origin.statusCode(), origin.statusCode());
        }
        return new FilePayload(
                clean,
                new byte[0],
                origin.contentType(),
                origin.sha256() != null ? origin.sha256() : "",
                CacheDecision.MISS);
    }

    /**
     * Invalidiert eine einzelne Datei im Cache.
     *
     * @param path Dateipfad
     * @return {@code true} wenn ein Eintrag entfernt wurde
     */
    public boolean invalidateFile(String path) {
        return cacheStore.remove(normalizePath(path));
    }

    /**
     * Invalidiert alle Cache-Einträge, die mit dem gegebenen Prefix beginnen.
     *
     * @param prefix Pfad-Prefix
     * @return Anzahl entfernter Einträge
     */
    public int invalidatePrefix(String prefix) {
        return cacheStore.removeByPrefix(normalizePath(prefix));
    }

    /**
     * Leert den gesamten Cache.
     */
    public void clearCache() {
        cacheStore.clear();
    }

    /**
     * Gibt die aktuelle Anzahl der Cache-Einträge zurück.
     *
     * @return Eintragsanzahl
     */
    public int cacheSize() {
        return cacheStore.size();
    }

    /**
     * Wechselt den CacheStore, wenn die konfigurierte Strategie sich geändert hat.
     * Bestehende Einträge gehen dabei verloren – akzeptabler Trade-off für den Live-Wechsel.
     */
    private void ensureStrategy() {
        ReplacementStrategy strategy = configService.current().replacementStrategy();
        switch (strategy) {
            case LRU -> {
                if (!(cacheStore instanceof LruCacheStore)) cacheStore = new LruCacheStore();
            }
            case LFU -> {
                if (!(cacheStore instanceof LfuCacheStore)) cacheStore = new LfuCacheStore();
            }
        }
    }

    /**
     * Validiert den Origin-Statuscode und das Vorhandensein von Body und SHA256.
     *
     * @param origin Origin-Antwort
     * @throws EdgeUpstreamException bei ungültigem Status oder fehlendem Body/SHA256
     */
    private void validateOriginResponse(OriginFileResponse origin) {
        if (origin.statusCode() < 200 || origin.statusCode() >= 300) {
            throw new EdgeUpstreamException("Origin responded with " + origin.statusCode(), origin.statusCode());
        }
        if (origin.body() == null || origin.sha256() == null || origin.sha256().isBlank()) {
            throw new EdgeUpstreamException("Origin response missing body or sha256", 502);
        }
    }

    /**
     * Vergleicht den erwarteten SHA256-Hash mit dem berechneten Hash.
     *
     * @param expected erwarteter Hash vom Origin-Header
     * @param actual   berechneter Hash des empfangenen Bodys
     * @throws EdgeUpstreamException bei Hash-Mismatch
     */
    private void validateSha256(String expected, String actual) {
        if (!expected.equalsIgnoreCase(actual)) {
            throw new EdgeUpstreamException("Integrity check failed: sha256 mismatch", 502);
        }
    }

    /**
     * Normalisiert einen Pfad: trimmt Whitespace und entfernt führende Slashes.
     *
     * @param path roher Pfad
     * @return normalisierter Pfad
     */
    private static String normalizePath(String path) {
        if (path == null) return "";
        String p = path.trim();
        while (p.startsWith("/")) p = p.substring(1);
        return p;
    }
}
