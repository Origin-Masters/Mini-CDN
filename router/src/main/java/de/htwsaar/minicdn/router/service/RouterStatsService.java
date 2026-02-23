package de.htwsaar.minicdn.router.service;

import java.time.Clock;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * Erfasst Router-seitige Betriebsmetriken in-memory.
 *
 * <p>Die Implementierung ist bewusst leichtgewichtig und thread-safe. Sie eignet sich für lokale
 * Entwicklungs- und Testumgebungen, in denen keine externe Metrik-Infrastruktur vorhanden ist.</p>
 */
@Service
public class RouterStatsService {

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong routingErrors = new AtomicLong(0);
    private final Map<String, AtomicLong> requestsByRegion = new ConcurrentHashMap<>();
    private final Map<String, Long> clientsLastSeenMs = new ConcurrentHashMap<>();
    private final Deque<Long> requestTimestampsMs = new ConcurrentLinkedDeque<>();
    private final Map<String, AtomicLong> downloadsByFile = new ConcurrentHashMap<>();
    private final Map<String, Map<String, AtomicLong>> downloadsByFileByEdge = new ConcurrentHashMap<>();
    private final Clock clock;

    /** Erstellt den Service mit der System-Uhr. */
    public RouterStatsService() {
        this(Clock.systemUTC());
    }

    /**
     * Erstellt den Service mit einer expliziten Uhr. Praktisch für deterministische Tests.
     *
     * @param clock Zeitquelle
     */
    public RouterStatsService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Erfasst eine erfolgreiche Routing-Anfrage.
     *
     * @param region Region der Anfrage
     * @param clientId optionale Client-ID (z. B. X-Client-Id)
     */
    public void recordRequest(String region, String clientId) {
        totalRequests.incrementAndGet();

        if (region != null && !region.isBlank()) {
            requestsByRegion
                    .computeIfAbsent(region, ignored -> new AtomicLong(0))
                    .incrementAndGet();
        }

        if (clientId != null && !clientId.isBlank()) {
            clientsLastSeenMs.put(clientId.trim(), clock.millis());
        }

        requestTimestampsMs.addLast(clock.millis());
    }

    /** Erfasst einen Routing-Fehler (z. B. fehlende Region, keine Edge-Node). */
    public void recordError() {
        routingErrors.incrementAndGet();
    }

    /**
     * Erfasst eine erfolgreich zugestellte Datei (Redirect an eine Edge-Node).
     *
     * @param path angefragter Dateipfad
     * @param edgeUrl URL der ausgewählten Edge-Node
     */
    public void recordDownload(String path, String edgeUrl) {
        if (path == null || path.isBlank() || edgeUrl == null || edgeUrl.isBlank()) {
            return;
        }

        String cleanPath = normalizePath(path);
        if (cleanPath.isBlank()) {
            return;
        }
        String cleanEdgeUrl = edgeUrl.trim();

        downloadsByFile.computeIfAbsent(cleanPath, ignored -> new AtomicLong(0)).incrementAndGet();
        downloadsByFileByEdge
                .computeIfAbsent(cleanPath, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(cleanEdgeUrl, ignored -> new AtomicLong(0))
                .incrementAndGet();
    }

    /**
     * Liefert eine Momentaufnahme als serialisierbares Objekt.
     *
     * @param windowSeconds Zeitfenster in Sekunden (z. B. 60 für exakte Requests/Minute)
     * @return Snapshot mit allen Router-Metriken
     */
    public RouterStatsSnapshot snapshot(int windowSeconds) {
        int safeWindow = Math.max(1, windowSeconds);
        long nowMs = clock.millis();

        purgeOldRequests(nowMs, safeWindow);
        purgeInactiveClients(nowMs, safeWindow);

        Map<String, Long> requestsByRegionSnapshot = new HashMap<>();
        requestsByRegion.forEach((region, counter) -> requestsByRegionSnapshot.put(region, counter.get()));

        Map<String, Long> downloadsByFileSnapshot = new TreeMap<>();
        downloadsByFile.forEach((path, counter) -> downloadsByFileSnapshot.put(path, counter.get()));

        Map<String, Map<String, Long>> downloadsByFileByEdgeSnapshot = new TreeMap<>();
        downloadsByFileByEdge.forEach((path, perEdge) -> {
            Map<String, Long> perEdgeSnapshot = new TreeMap<>();
            perEdge.forEach((edgeUrl, counter) -> perEdgeSnapshot.put(edgeUrl, counter.get()));
            downloadsByFileByEdgeSnapshot.put(path, perEdgeSnapshot);
        });

        return new RouterStatsSnapshot(
                totalRequests.get(),
                requestTimestampsMs.size(),
                routingErrors.get(),
                clientsLastSeenMs.size(),
                requestsByRegionSnapshot,
                downloadsByFileSnapshot,
                downloadsByFileByEdgeSnapshot);
    }


    /**
     * Normalisiert einen Dateipfad für die Statistik-Zählung.
     *
     * @param path roher Pfad
     * @return normalisierter Pfad ohne führende Slashes
     */
    private static String normalizePath(String path) {
        String value = path == null ? "" : path.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
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

    private void purgeInactiveClients(long nowMs, int windowSeconds) {
        long threshold = nowMs - (windowSeconds * 1000L);
        clientsLastSeenMs.entrySet().removeIf(entry -> entry.getValue() < threshold);
    }

    /**
     * Unveränderlicher Snapshot der Router-Metriken.
     *
     * @param totalRequests Gesamtanzahl Requests seit Prozessstart
     * @param requestsPerWindow exakte Anzahl Requests im angegebenen Zeitfenster
     * @param routingErrors Routing-Fehler seit Prozessstart
     * @param activeClients Anzahl eindeutiger Clients im Zeitfenster
     * @param requestsByRegion kumulierte Requests pro Region
     * @param downloadsByFile kumulierte Downloads pro Datei
     * @param downloadsByFileByEdge kumulierte Downloads pro Datei je Edge-URL
     */
    public record RouterStatsSnapshot(
            long totalRequests,
            long requestsPerWindow,
            long routingErrors,
            long activeClients,
            Map<String, Long> requestsByRegion,
            Map<String, Long> downloadsByFile,
            Map<String, Map<String, Long>> downloadsByFileByEdge) {}
}
