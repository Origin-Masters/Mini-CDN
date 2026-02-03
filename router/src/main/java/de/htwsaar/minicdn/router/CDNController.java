package de.htwsaar.minicdn.router;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Zentraler CDN Controller, der Anfragen an verfügbare Edge-Nodes delegiert.
 * Implementiert Round-Robin zur Lastverteilung innerhalb einer Region.
 */
@RestController // Webschittstelle
@RequestMapping("/api/cdn") // Basis Pfad für alle Endpunkte
@Profile("cdn")
public class CDNController {

    private final RoutingIndex routingIndex;
    private final HttpClient httpClient;
    private final MetricsService metricsService;

    public record EdgeNode(String url) {}

    public record EdgeNodeStatus(String url, boolean healthy) {}

    public CDNController() {
        this.routingIndex = new RoutingIndex();
        this.metricsService = new MetricsService();
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }
    // Prüfen, ob der Edge Server abgestürzt ist oder noch läuft
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    // Ist Edge Server bereit?
    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        return ResponseEntity.ok("ready");
    }

    /**
     * Routing-Logik: Wählt eine Edge-Node mittels Round-Robin aus der Region aus.
     */
    @GetMapping("/files/{path:.+}")
    public ResponseEntity<?> routeToEdge(
            @PathVariable("path") String path,
            // schauen nach der Region des Nutzers (Query Paramater)
            @RequestParam(value = "region", required = false) String regionQuery,
            // im HTTP-Header
            @RequestHeader(value = "X-Client-Region", required = false) String regionHeader) {

        // Region mitgeben oder automatisch mitsenden
        String region = (regionQuery != null && !regionQuery.isBlank()) ? regionQuery : regionHeader;

        if (region == null || region.isBlank()) {
            metricsService.recordError(); // ungültige Anfrage
            // Beispiel für Fehlermeldung im Body:
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Fehler: Region fehlt. Bitte 'region' Query-Parameter oder 'X-Client-Region' Header setzen.");
        }

        metricsService.recordRequest(region);
        EdgeNode selectedNode = routingIndex.getNextNode(region);

        if (selectedNode == null) {
            metricsService.recordError();
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Fehler: Keine verfügbaren Edge-Nodes für Region '" + region + "' gefunden.");
        }

        metricsService.recordNodeSelection(selectedNode.url());
        String location = selectedNode.url() + "/api/edge/files/" + path;

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(location));
        return new ResponseEntity<>(headers, HttpStatus.TEMPORARY_REDIRECT);
    }

    @RestController
    @RequestMapping("/api/cdn/routing")
    public class RoutingAdminApi {

        public record BulkRequest(String region, String url, String action) {}

        public record BulkResponse(String region, String url, String status) {}

        @PostMapping
        public ResponseEntity<Void> addEdgeNode(
                @RequestParam(value = "region") String region, @RequestParam(value = "url") String url) {
            routingIndex.addEdge(region, new EdgeNode(url));
            return ResponseEntity.status(HttpStatus.CREATED).build();
        }

        @PostMapping("/bulk")
        public ResponseEntity<List<BulkResponse>> bulkUpdate(@RequestBody List<BulkRequest> requests) {
            List<BulkResponse> results = new ArrayList<>();
            for (BulkRequest req : requests) {
                String status;
                if ("add".equalsIgnoreCase(req.action())) {
                    routingIndex.addEdge(req.region(), new EdgeNode(req.url()));
                    status = "added";
                } else if ("remove".equalsIgnoreCase(req.action())) {
                    boolean removed = routingIndex.removeEdge(req.region(), new EdgeNode(req.url()), true);
                    status = removed ? "removed" : "not found";
                } else {
                    status = "invalid action";
                }
                results.add(new BulkResponse(req.region(), req.url(), status));
            }
            return ResponseEntity.ok(results);
        }

        @DeleteMapping
        public ResponseEntity<?> deleteEdgeNode(
                @RequestParam(value = "region") String region, @RequestParam(value = "url") String url) {
            boolean removed = routingIndex.removeEdge(region, new EdgeNode(url), true);
            return removed
                    ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body("Knoten " + url + " in Region " + region + " nicht gefunden.");
        }

        @GetMapping
        public ResponseEntity<Map<String, List<EdgeNodeStatus>>> getIndex(
                @RequestParam(value = "checkHealth", defaultValue = "false") boolean checkHealth) {

            Map<String, Set<EdgeNode>> rawIndex = routingIndex.getRawIndex();
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
                    CompletableFuture<Void> future = checkNodeHealth(node)
                            .thenAccept(isHealthy -> statuses.add(new EdgeNodeStatus(node.url(), isHealthy)));
                    futures.add(future);
                }
            });
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            return ResponseEntity.ok(result);
        }

        @GetMapping("/metrics")
        public ResponseEntity<Map<String, Object>> getMetrics() {
            return ResponseEntity.ok(metricsService.getSnapshot());
        }

        private CompletableFuture<Boolean> checkNodeHealth(EdgeNode node) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(node.url() + "/api/edge/health"))
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build();

            return httpClient
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> response.statusCode() == 200)
                    .exceptionally(ex -> false);
        }
    }

    public static class MetricsService {
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong routingErrors = new AtomicLong(0);
        private final Map<String, AtomicLong> regionStats = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> nodeSelectionStats = new ConcurrentHashMap<>();

        public void recordRequest(String region) {
            totalRequests.incrementAndGet();
            regionStats.computeIfAbsent(region, k -> new AtomicLong(0)).incrementAndGet();
        }

        public void recordNodeSelection(String url) {
            nodeSelectionStats.computeIfAbsent(url, k -> new AtomicLong(0)).incrementAndGet();
        }

        public void recordError() {
            routingErrors.incrementAndGet();
        }

        public Map<String, Object> getSnapshot() {
            Map<String, Object> snapshot = new java.util.HashMap<>();
            snapshot.put("totalRequests", totalRequests.get());
            snapshot.put("routingErrors", routingErrors.get());
            snapshot.put("requestsByRegion", regionStats);
            snapshot.put("selectionsByNode", nodeSelectionStats);
            return snapshot;
        }
    }

    public static class RoutingIndex {
        private final Map<String, Set<EdgeNode>> regionToNodes = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> regionCounters = new ConcurrentHashMap<>();

        public void addEdge(String region, EdgeNode node) {
            if (region != null && node != null) {
                regionToNodes
                        .computeIfAbsent(region, k -> new CopyOnWriteArraySet<>())
                        .add(node);
                regionCounters.putIfAbsent(region, new AtomicInteger(0));
            }
        }

        public EdgeNode getNextNode(String region) {
            Set<EdgeNode> nodes = regionToNodes.get(region);
            if (nodes == null || nodes.isEmpty()) return null;

            List<EdgeNode> nodeList = new ArrayList<>(nodes);
            AtomicInteger counter = regionCounters.get(region);
            int index = Math.abs(counter.getAndIncrement() % nodeList.size());
            return nodeList.get(index);
        }

        public boolean removeEdge(String region, EdgeNode node, boolean removeIfEmpty) {
            Set<EdgeNode> nodes = regionToNodes.get(region);
            if (nodes == null) return false;

            boolean removed = nodes.remove(node);
            if (removed && nodes.isEmpty() && removeIfEmpty) {
                regionToNodes.remove(region);
                regionCounters.remove(region);
            }
            return removed;
        }

        public Map<String, Set<EdgeNode>> getRawIndex() {
            return Collections.unmodifiableMap(regionToNodes);
        }

        public void clear() {
            regionToNodes.clear();
            regionCounters.clear();
        }
    }
}
