package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.dto.BulkRequest;
import de.htwsaar.minicdn.router.dto.BulkResponse;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.dto.EdgeNodeStatus;
import de.htwsaar.minicdn.router.service.EdgeHttpClient;
import de.htwsaar.minicdn.router.service.MetricsService;
import de.htwsaar.minicdn.router.service.RoutingIndex;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Verwaltungs-API für die Pflege des Routingindexes.
 */
@RestController
@RequestMapping("/api/cdn/routing")
public class RoutingAdminController {

    private final RoutingIndex routingIndex;
    private final MetricsService metricsService;
    private final EdgeHttpClient edgeHttpClient;

    public RoutingAdminController(
            RoutingIndex routingIndex, MetricsService metricsService, EdgeHttpClient edgeHttpClient) {
        this.routingIndex = routingIndex;
        this.metricsService = metricsService;
        this.edgeHttpClient = edgeHttpClient;
    }

    /**
     * Registriert einen Edge-Knoten in einer Region.
     *
     * @param region Zielregion
     * @param url URL des Edge-Knotens
     * @return {@code 201 Created} bei Erfolg
     */
    @PostMapping
    public ResponseEntity<Void> addEdgeNode(
            @RequestParam(value = "region") String region, @RequestParam(value = "url") String url) {

        routingIndex.addEdge(region, new EdgeNode(url));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Verarbeitet eine Liste von Add/Remove-Anweisungen in einem Request.
     *
     * @param requests Bulk-Anweisungen
     * @return Ergebnisliste pro Anweisung
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<BulkResponse>> bulkUpdate(@RequestBody List<BulkRequest> requests) {
        List<BulkResponse> results = new ArrayList<>();
        if (requests == null) {
            return ResponseEntity.ok(results);
        }

        for (BulkRequest req : requests) {
            String status;
            if (req != null && "add".equalsIgnoreCase(req.action())) {
                routingIndex.addEdge(req.region(), new EdgeNode(req.url()));
                status = "added";
            } else if (req != null && "remove".equalsIgnoreCase(req.action())) {
                boolean removed = routingIndex.removeEdge(req.region(), new EdgeNode(req.url()), true);
                status = removed ? "removed" : "not found";
            } else {
                status = "invalid action";
            }
            results.add(new BulkResponse(req == null ? null : req.region(), req == null ? null : req.url(), status));
        }

        return ResponseEntity.ok(results);
    }

    /**
     * Entfernt einen Edge-Knoten aus einer Region.
     *
     * @param region Zielregion
     * @param url URL des zu entfernenden Knotens
     * @return {@code 200 OK} oder {@code 404 Not Found}
     */
    @DeleteMapping
    public ResponseEntity<?> deleteEdgeNode(
            @RequestParam(value = "region") String region, @RequestParam(value = "url") String url) {

        boolean removed = routingIndex.removeEdge(region, new EdgeNode(url), true);
        return removed
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Knoten " + url + " in Region " + region + " nicht gefunden.");
    }

    /**
     * Gibt den aktuellen Routingindex zurück und prüft optional die Knotergesundheit.
     *
     * @param checkHealth aktiviert aktive Health-Checks pro Knoten
     * @return Knotenstatus nach Region gruppiert
     */
    @GetMapping
    public ResponseEntity<Map<String, List<EdgeNodeStatus>>> getIndex(
            @RequestParam(value = "checkHealth", defaultValue = "false") boolean checkHealth) {

        Map<String, List<EdgeNode>> rawIndex = routingIndex.getRawIndex();
        Map<String, List<EdgeNodeStatus>> result = new ConcurrentHashMap<>();

        if (!checkHealth) {
            rawIndex.forEach((region, nodes) -> {
                List<EdgeNodeStatus> statuses = nodes.stream()
                        .map(n -> new EdgeNodeStatus(n.url(), true))
                        .collect(Collectors.toList());
                result.put(region, statuses);
            });
            return ResponseEntity.ok(result);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        rawIndex.forEach((region, nodes) -> {
            List<EdgeNodeStatus> statuses = Collections.synchronizedList(new ArrayList<>());
            result.put(region, statuses);

            for (EdgeNode node : nodes) {
                futures.add(edgeHttpClient
                        .checkNodeHealth(node, Duration.ofSeconds(1))
                        .thenAccept(isHealthy -> statuses.add(new EdgeNodeStatus(node.url(), isHealthy))));
            }
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return ResponseEntity.ok(result);
    }

    /**
     * Liefert eine Momentaufnahme der Router-Metriken.
     *
     * @return Metriken als Schlüssel/Wert-Struktur
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(metricsService.getSnapshot());
    }
}
