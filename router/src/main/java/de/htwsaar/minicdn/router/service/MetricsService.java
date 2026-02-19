package de.htwsaar.minicdn.router.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * Verwaltet einfache Router-Metriken im Speicher.
 */
@Service
public class MetricsService {

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong routingErrors = new AtomicLong(0);
    private final Map<String, AtomicLong> regionStats = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> nodeSelectionStats = new ConcurrentHashMap<>();

    /**
     * Erfasst eine neue Anfrage für eine Region.
     *
     * @param region angefragte Region
     */
    public void recordRequest(String region) {
        totalRequests.incrementAndGet();
        regionStats.computeIfAbsent(region, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Erhöht den Auswahlzähler für einen Edge-Knoten.
     *
     * @param url URL des ausgewählten Knotens
     */
    public void recordNodeSelection(String url) {
        nodeSelectionStats.computeIfAbsent(url, k -> new AtomicLong(0)).incrementAndGet();
    }

    /** Erfasst einen Routingfehler. */
    public void recordError() {
        routingErrors.incrementAndGet();
    }

    /**
     * Liefert eine aktuelle Kopie aller Router-Metriken.
     *
     * @return Snapshot der Metrikwerte
     */
    public Map<String, Object> getSnapshot() {
        Map<String, Object> snapshot = new java.util.HashMap<>();
        snapshot.put("totalRequests", totalRequests.get());
        snapshot.put("routingErrors", routingErrors.get());
        snapshot.put("requestsByRegion", regionStats);
        snapshot.put("selectionsByNode", nodeSelectionStats);
        return snapshot;
    }
}
