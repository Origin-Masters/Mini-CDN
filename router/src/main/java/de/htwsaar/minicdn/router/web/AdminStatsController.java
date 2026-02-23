package de.htwsaar.minicdn.router.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.dto.EdgeStatsPayload;
import de.htwsaar.minicdn.router.service.EdgeHttpClient;
import de.htwsaar.minicdn.router.service.RouterStatsService;
import de.htwsaar.minicdn.router.service.RoutingIndex;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-API für aggregierte Router-/Edge-Statistiken.
 */
@RestController
@RequestMapping("/api/cdn/admin")
public class AdminStatsController {

    private final RouterStatsService routerStatsService;
    private final RoutingIndex routingIndex;
    private final EdgeHttpClient edgeHttpClient;
    private final ObjectMapper objectMapper;

    public AdminStatsController(
            RouterStatsService routerStatsService,
            RoutingIndex routingIndex,
            EdgeHttpClient edgeHttpClient,
            ObjectMapper objectMapper) {

        this.routerStatsService = routerStatsService;
        this.routingIndex = routingIndex;
        this.edgeHttpClient = edgeHttpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Erstellt eine aggregierte Sicht auf Router- und optional Edge-Statistiken.
     *
     * @param windowSec Zeitfenster für gleitende Kennzahlen in Sekunden
     * @param aggregateEdge steuert das Einsammeln von Edge-Statistiken
     * @return kombinierter Statistik-Report
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestParam(value = "windowSec", defaultValue = "60") int windowSec,
            @RequestParam(value = "aggregateEdge", defaultValue = "true") boolean aggregateEdge) {

        int safeWindow = Math.max(1, windowSec);
        RouterStatsService.RouterStatsSnapshot routerSnapshot = routerStatsService.snapshot(safeWindow);

        Map<String, List<EdgeNode>> rawIndex = routingIndex.getRawIndex();

        long totalNodes = rawIndex.values().stream().mapToLong(List::size).sum();
        Map<String, Integer> nodesByRegion = new TreeMap<>();
        rawIndex.forEach((region, nodes) -> nodesByRegion.put(region, nodes.size()));

        long cacheHits = 0;
        long cacheMisses = 0;
        long filesCached = 0;
        List<String> edgeErrors = new ArrayList<>();

        if (aggregateEdge) {
            for (List<EdgeNode> nodes : rawIndex.values()) {
                for (EdgeNode node : nodes) {
                    String edgeUrl = node.url();
                    try {
                        var response = edgeHttpClient.fetchEdgeAdminStats(node, safeWindow, Duration.ofSeconds(2));

                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            EdgeStatsPayload payload = objectMapper.readValue(response.body(), EdgeStatsPayload.class);
                            cacheHits += payload.cacheHits();
                            cacheMisses += payload.cacheMisses();
                            filesCached += payload.filesCached();
                        } else {
                            edgeErrors.add(edgeUrl + " -> HTTP " + response.statusCode());
                        }
                    } catch (Exception ex) {
                        edgeErrors.add(edgeUrl + " -> " + ex.getClass().getSimpleName());
                    }
                }
            }
        }

        double cacheHitRatio = (cacheHits + cacheMisses) == 0 ? 0.0 : (double) cacheHits / (cacheHits + cacheMisses);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("timestamp", Instant.now().toString());
        response.put("windowSec", safeWindow);

        response.put(
                "router",
                Map.of(
                        "totalRequests", routerSnapshot.totalRequests(),
                        "requestsPerMinute", routerSnapshot.requestsPerWindow(),
                        "routingErrors", routerSnapshot.routingErrors(),
                        "activeClients", routerSnapshot.activeClients(),
                        "requestsByRegion", routerSnapshot.requestsByRegion()));

        response.put(
                "cache",
                Map.of(
                        "hits", cacheHits,
                        "misses", cacheMisses,
                        "hitRatio", cacheHitRatio,
                        "filesLoaded", filesCached));

        response.put(
                "downloads",
                Map.of(
                        "byFileTotal", routerSnapshot.downloadsByFile(),
                        "byFileByEdge", routerSnapshot.downloadsByFileByEdge()));

        response.put(
                "nodes",
                Map.of(
                        "total", totalNodes,
                        "byRegion", nodesByRegion));

        response.put(
                "edgeAggregation",
                Map.of(
                        "enabled", aggregateEdge,
                        "errors", edgeErrors));

        return ResponseEntity.ok(response);
    }
}
