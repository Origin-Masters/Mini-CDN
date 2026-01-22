package de.htwsaar.minicdn.router;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
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
@RestController
@RequestMapping("/api/cdn")
@Profile("cdn")
public class CDNController {

    private final RoutingIndex routingIndex;

    /**
     * Repräsentiert einen Edge-Server im Netzwerk.
     */
    public record EdgeNode(String url) {}

    public CDNController() {
        this.routingIndex = new RoutingIndex();
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        return ResponseEntity.ok("ready");
    }

    /**
     * Routing-Logik: Wählt eine Edge-Node mittels Round-Robin aus der Region aus.
     */
    @GetMapping("/files/{path:.+}")
    public ResponseEntity<Void> routeToEdge(
            @PathVariable("path") String path,
            @RequestParam(value = "region", required = false) String regionQuery,
            @RequestHeader(value = "X-Client-Region", required = false) String regionHeader) {

        String region = (regionQuery != null && !regionQuery.isBlank()) ? regionQuery : regionHeader;

        if (region == null || region.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Suche nach der nächsten verfügbaren Node mittels Round-Robin
        EdgeNode selectedNode = routingIndex.getNextNode(region);

        if (selectedNode == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String location = selectedNode.url() + "/api/edge/files/" + path;

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(location));

        return new ResponseEntity<>(headers, HttpStatus.TEMPORARY_REDIRECT);
    }

    /**
     * Interne API zur dynamischen Verwaltung der Routing-Tabelle.
     */
    @RestController
    @RequestMapping("/api/cdn/routing")
    public class RoutingAdminApi {

        /** Records für Bulk-Updates */
        public record BulkRequest(String region, String url, String action) {}

        public record BulkResponse(String region, String url, String status) {}

        @PostMapping
        public ResponseEntity<Void> addEdgeNode(
                @RequestParam(value = "region", required = true) String region,
                @RequestParam(value = "url", required = true) String url) {
            routingIndex.addEdge(region, new EdgeNode(url));
            return ResponseEntity.status(HttpStatus.CREATED).build();
        }

        /**
         * Bulk-Update Methode für effiziente Verwaltung mehrerer Nodes.
         * Erwartet ein JSON Array von BulkRequest Objekten.
         */
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
        public ResponseEntity<Void> deleteEdgeNode(
                @RequestParam(value = "region", required = true) String region,
                @RequestParam(value = "url", required = true) String url) {
            boolean removed = routingIndex.removeEdge(region, new EdgeNode(url), true);
            if (removed) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        }

        @GetMapping
        public ResponseEntity<Map<String, Set<EdgeNode>>> getIndex() {
            return ResponseEntity.ok(routingIndex.getRawIndex());
        }
    }

    /**
     * Interne Klasse zur Verwaltung der Region-zu-EdgeNode-Zuordnung und Round-Robin Logik.
     */
    public static class RoutingIndex {
        private final Map<String, Set<EdgeNode>> regionToNodes = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> regionCounters =
                new ConcurrentHashMap<>(); // laufende index im Set für RR

        /**
         * Fügt einer Region eine Edge-Node hinzu.
         */
        public void addEdge(String region, EdgeNode node) {
            if (region != null && node != null) {
                regionToNodes
                        .computeIfAbsent(region, k -> new CopyOnWriteArraySet<>())
                        .add(node);
                regionCounters.putIfAbsent(region, new AtomicInteger(0));
            }
        }

        /**
         * Wählt die nächste Node für eine Region basierend auf Round-Robin aus.
         */
        public EdgeNode getNextNode(String region) {
            Set<EdgeNode> nodes = regionToNodes.get(region);
            if (nodes == null || nodes.isEmpty()) {
                return null;
            }

            // Umwandlung in Liste für indexbasierten Zugriff
            List<EdgeNode> nodeList = new ArrayList<>(nodes);
            AtomicInteger counter = regionCounters.get(region);

            if (counter == null) return nodeList.get(0);

            // Inkrementieren und Modulo-Operation für Round-Robin
            int index = counter.getAndIncrement() % nodeList.size();
            return nodeList.get(index);
        }

        /**
         * Entfernt eine spezifische Node aus einer Region.
         * @return true, wenn die Node gefunden und entfernt wurde, sonst false.
         */
        public boolean removeEdge(String region, EdgeNode node, boolean removeIfEmpty) {
            Set<EdgeNode> nodes = regionToNodes.get(region);
            if (nodes == null) {
                return false;
            }

            boolean removed = nodes.remove(node);
            if (removed && nodes.isEmpty() && removeIfEmpty) {
                regionToNodes.remove(region);
                regionCounters.remove(region);
            }
            return removed;
        }

        /**
         * Gibt den aktuellen Status des Index zurück.
         */
        public Map<String, Set<EdgeNode>> getRawIndex() {
            return Collections.unmodifiableMap(regionToNodes);
        }

        /**
         * Leert den gesamten Index.
         */
        public void clear() {
            regionToNodes.clear();
            regionCounters.clear();
        }
    }
}
