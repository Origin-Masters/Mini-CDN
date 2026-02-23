package de.htwsaar.minicdn.edge;

import java.time.Clock;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Erfasst Laufzeitmetriken für eine einzelne Edge-Node.
 *
 * <p>Die Werte liegen nur im Speicher der laufenden Instanz. Das ist für lokale Entwicklung und
 * Demo-Betrieb ausreichend.</p>
 */
@Service
@Profile("edge")
public class EdgeMetricsService {

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final Map<String, AtomicLong> downloadsByFile = new ConcurrentHashMap<>();
    private final Deque<Long> requestTimestampsMs = new ConcurrentLinkedDeque<>();
    private final Clock clock;

    /** Erstellt den Service mit der System-Uhr. */
    public EdgeMetricsService() {
        this(Clock.systemUTC());
    }

    /**
     * Erstellt den Service mit einer expliziten Uhr (nützlich für Tests).
     *
     * @param clock Zeitquelle
     */
    public EdgeMetricsService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Erfasst einen Cache-Hit-Request für eine Datei.
     *
     * @param path Dateipfad der angeforderten Datei
     */
    public void recordHit(String path) {
        totalRequests.incrementAndGet();
        cacheHits.incrementAndGet();
        recordFileDownload(path);
        requestTimestampsMs.addLast(clock.millis());
    }

    /**
     * Erfasst einen Cache-Miss-Request für eine Datei.
     *
     * @param path Dateipfad der angeforderten Datei
     */
    public void recordMiss(String path) {
        totalRequests.incrementAndGet();
        cacheMisses.incrementAndGet();
        recordFileDownload(path);
        requestTimestampsMs.addLast(clock.millis());
    }

    /**
     * Liefert eine Momentaufnahme inklusive exakter Request-Zahl im Zeitfenster.
     *
     * @param windowSeconds Zeitfenster in Sekunden
     * @param filesCached aktuelle Anzahl gecachter Dateien
     * @return Snapshot
     */
    public EdgeStatsSnapshot snapshot(int windowSeconds, long filesCached) {
        int safeWindow = Math.max(1, windowSeconds);
        long nowMs = clock.millis();
        purgeOldRequests(nowMs, safeWindow);

        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long totalCacheDecisions = hits + misses;
        double hitRatio = totalCacheDecisions == 0 ? 0.0 : (double) hits / totalCacheDecisions;

        Map<String, Long> downloadsByFileSnapshot = new HashMap<>();
        downloadsByFile.forEach((path, counter) -> downloadsByFileSnapshot.put(path, counter.get()));

        return new EdgeStatsSnapshot(
                totalRequests.get(),
                requestTimestampsMs.size(),
                hits,
                misses,
                hitRatio,
                Math.max(0, filesCached),
                downloadsByFileSnapshot);
    }

    /**
     * Erfasst den Download-Zähler für eine Datei, sofern ein gültiger Pfad vorliegt.
     *
     * @param path Dateipfad
     */
    private void recordFileDownload(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        downloadsByFile.computeIfAbsent(path, ignored -> new AtomicLong(0)).incrementAndGet();
    }

    private void purgeOldRequests(long nowMs, int windowSeconds) {
        long threshold = nowMs - (windowSeconds * 1000L);
        while (true) {
            Long first = requestTimestampsMs.peekFirst();
            if (first == null || first >= threshold) {
                break;
            }
            requestTimestampsMs.pollFirst();
        }
    }

    /**
     * Unveränderlicher Snapshot der Edge-Metriken.
     *
     * @param totalRequests Gesamtanzahl Requests seit Start
     * @param requestsPerWindow exakte Anzahl Requests im Zeitfenster
     * @param cacheHits Anzahl Cache-Hits
     * @param cacheMisses Anzahl Cache-Misses
     * @param cacheHitRatio Trefferquote zwischen 0 und 1
     * @param filesCached aktuell im Cache liegende Dateien
     * @param downloadsByFile Download-Anzahl je Datei (kumuliert seit Start)
     */
    public record EdgeStatsSnapshot(
            long totalRequests,
            long requestsPerWindow,
            long cacheHits,
            long cacheMisses,
            double cacheHitRatio,
            long filesCached,
            Map<String, Long> downloadsByFile) {}
}
