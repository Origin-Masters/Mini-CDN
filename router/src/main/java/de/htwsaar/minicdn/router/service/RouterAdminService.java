package de.htwsaar.minicdn.router.service;

import de.htwsaar.minicdn.router.domain.EdgeGateway;
import de.htwsaar.minicdn.router.domain.EdgeNodeStats;
import de.htwsaar.minicdn.router.dto.BulkRequest;
import de.htwsaar.minicdn.router.dto.BulkResponse;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.dto.EdgeNodeStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Bündelt die fachlichen Admin-Use-Cases des Routers.
 *
 * <p>Die Services arbeiten nur mit fachlichen Edge-Operationen.
 * Transportdetails liegen vollständig im Adapter.</p>
 */
@Service
public class RouterAdminService {

    private final RoutingIndex routingIndex;
    private final RouterStatsService routerStatsService;
    private final EdgeGateway edgeGateway;

    public RouterAdminService(
            RoutingIndex routingIndex, RouterStatsService routerStatsService, EdgeGateway edgeGateway) {

        this.routingIndex = routingIndex;
        this.routerStatsService = routerStatsService;
        this.edgeGateway = edgeGateway;
    }

    /**
     * Registriert eine Edge-URL für eine Region.
     *
     * @param region Zielregion
     * @param url Basis-URL der Edge
     */
    public void addEdgeNode(String region, String url) {
        routingIndex.addEdge(region, new EdgeNode(url));
    }

    /**
     * Verarbeitet Bulk-Aktionen für add/remove von Edges.
     *
     * @param requests Liste der Aktionen
     * @return Ergebnisliste
     */
    public List<BulkResponse> bulkUpdate(List<BulkRequest> requests) {
        List<BulkResponse> results = new ArrayList<>();
        if (requests == null) {
            return results;
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

        return results;
    }

    /**
     * Entfernt eine Edge-URL aus einer Region.
     *
     * @param region Zielregion
     * @param url Basis-URL der Edge
     * @return {@code true}, wenn entfernt
     */
    public boolean deleteEdgeNode(String region, String url) {
        return routingIndex.removeEdge(region, new EdgeNode(url), true);
    }

    /**
     * Liefert den Routing-Index, optional mit Health-Checks.
     *
     * @param checkHealth ob Edges geprüft werden
     * @return Index nach Region
     */
    public Map<String, List<EdgeNodeStatus>> getIndex(boolean checkHealth) {
        Map<String, List<EdgeNode>> rawIndex = routingIndex.getRawIndex();
        Map<String, List<EdgeNodeStatus>> result = new ConcurrentHashMap<>();

        if (!checkHealth) {
            rawIndex.forEach((region, nodes) -> {
                List<EdgeNodeStatus> statuses = nodes.stream()
                        .map(node -> new EdgeNodeStatus(node.url(), true))
                        .collect(Collectors.toList());
                result.put(region, statuses);
            });
            return result;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        rawIndex.forEach((region, nodes) -> {
            List<EdgeNodeStatus> statuses = Collections.synchronizedList(new ArrayList<>());
            result.put(region, statuses);

            for (EdgeNode node : nodes) {
                futures.add(edgeGateway
                        .checkNodeHealth(node, Duration.ofSeconds(1))
                        .thenAccept(healthy -> statuses.add(new EdgeNodeStatus(node.url(), healthy))));
            }
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return result;
    }

    /**
     * Invalidiert einen konkreten Pfad in einer Region.
     *
     * @param region Zielregion
     * @param path Pfad relativ zum Origin
     * @return Ergebnis der Broadcast-Operation
     */
    public Map<String, Object> invalidatePath(String region, String path) {
        return broadcast(region, node -> edgeGateway.invalidateFile(node, path));
    }

    /**
     * Invalidiert einen Prefix in einer Region.
     *
     * @param region Zielregion
     * @param value Prefix für die Invalidierung
     * @return Ergebnis der Broadcast-Operation
     */
    public Map<String, Object> invalidatePrefix(String region, String value) {
        return broadcast(region, node -> edgeGateway.invalidatePrefix(node, value));
    }

    /**
     * Leert den Cache einer Region.
     *
     * @param region Zielregion
     * @return Ergebnis der Broadcast-Operation
     */
    public Map<String, Object> clearRegion(String region) {
        return broadcast(region, edgeGateway::clearCache);
    }

    /**
     * Aggregiert Router- und Edge-Statistiken.
     *
     * @param windowSec Zeitfenster in Sekunden
     * @param aggregateEdge ob Edge-Statistiken gesammelt werden
     * @return Statistikdaten
     */
    public Map<String, Object> getStats(int windowSec, boolean aggregateEdge) {
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
                        EdgeNodeStats stats = edgeGateway.fetchAdminStats(node, safeWindow, Duration.ofSeconds(2));
                        cacheHits += stats.cacheHits();
                        cacheMisses += stats.cacheMisses();
                        filesCached += stats.filesCached();
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
                        "requestsPerWindow", routerSnapshot.requestsPerWindow(),
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

        Map<String, Object> downloads = new LinkedHashMap<>();
        downloads.put("byFileTotal", routerSnapshot.downloadsByFile());
        downloads.put("byFileByEdgeTotal", routerSnapshot.downloadsByFileByEdge());
        downloads.put("byFileByEdgeWindow", routerSnapshot.downloadsByFileByEdgeInWindow());
        response.put("downloads", downloads);

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

        response.put("loadBalancing", buildLoadBalancingStats(rawIndex, routerSnapshot));

        return response;
    }

    /**
     * Bewertet die Lastverteilung im angegebenen Zeitfenster gegen das Akzeptanzkriterium.
     *
     * @param rawIndex aktueller Routing-Index
     * @param routerSnapshot Router-Metriken des Zeitfensters
     * @return serialisierbare Auswertung des Load-Balancings
     */
    private Map<String, Object> buildLoadBalancingStats(
            Map<String, List<EdgeNode>> rawIndex, RouterStatsService.RouterStatsSnapshot routerSnapshot) {

        Map<String, Long> requestsByEdgeInWindow = new TreeMap<>();

        rawIndex.values().stream()
                .flatMap(List::stream)
                .map(EdgeNode::url)
                .sorted()
                .forEach(url -> requestsByEdgeInWindow.put(url, 0L));

        routerSnapshot
                .edgeRequestsInWindow()
                .forEach((edgeUrl, count) -> requestsByEdgeInWindow.merge(edgeUrl, count, Long::sum));

        long routedRequestsInWindow = requestsByEdgeInWindow.values().stream()
                .mapToLong(Long::longValue)
                .sum();

        int registeredEdges = requestsByEdgeInWindow.size();
        double idealPerEdge = registeredEdges == 0 ? 0.0 : (double) routedRequestsInWindow / registeredEdges;
        double toleranceAbsolute = idealPerEdge * 0.10d;

        boolean balancedWithinTolerance = registeredEdges > 0
                && routedRequestsInWindow > 0
                && requestsByEdgeInWindow.values().stream()
                        .allMatch(count -> Math.abs(count - idealPerEdge) <= toleranceAbsolute);

        boolean minimumSampleSizeReached = routedRequestsInWindow >= 1000;
        boolean acceptanceCriteriaMet = minimumSampleSizeReached && balancedWithinTolerance;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("registeredEdges", registeredEdges);
        result.put("requestsRoutedToEdgesInWindow", routedRequestsInWindow);
        result.put("requestsByEdgeInWindow", requestsByEdgeInWindow);
        result.put("idealPerEdge", idealPerEdge);
        result.put("tolerancePercent", 10);
        result.put("toleranceAbsolute", toleranceAbsolute);
        result.put("minimumSampleSize", 1000);
        result.put("minimumSampleSizeReached", minimumSampleSizeReached);
        result.put("balancedWithinTolerance", balancedWithinTolerance);
        result.put("acceptanceCriteriaMet", acceptanceCriteriaMet);

        return result;
    }

    /**
     * Führt eine fachliche Cache-Operation auf allen Edges einer Region aus.
     *
     * @param region Zielregion
     * @param operation fachliche Edge-Operation
     * @return Ergebnisliste pro Edge
     */
    private Map<String, Object> broadcast(String region, Function<EdgeNode, CompletableFuture<Boolean>> operation) {

        List<EdgeNode> nodes = routingIndex.getRawIndex().get(region);
        if (nodes == null || nodes.isEmpty()) {
            throw new NoSuchElementException("Region nicht gefunden");
        }

        List<CompletableFuture<String>> futures = nodes.stream()
                .map(node -> operation
                        .apply(node)
                        .thenApply(success -> node.url() + ": " + (success ? "OK" : "FAILED"))
                        .exceptionally(ex -> node.url() + ": FAILED"))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<String> results = futures.stream().map(CompletableFuture::join).toList();

        return Map.of("region", region, "results", results);
    }
}
