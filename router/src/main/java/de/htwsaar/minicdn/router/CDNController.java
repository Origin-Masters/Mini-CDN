package de.htwsaar.minicdn.router;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
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
public class CDNController {

    private final RoutingIndex routingIndex;
    private final HttpClient httpClient;
    private final MetricsService metricsService;
    private final RouterStatsService routerStatsService;
    private final ObjectMapper objectMapper;

    /**
     * Repräsentiert einen registrierten Edge-Knoten über seine Basis-URL.
     *
     * @param url öffentlich erreichbare URL des Edge-Knotens
     */
    public record EdgeNode(String url) {}

    /**
     * Beschreibt den Gesundheitszustand eines Edge-Knotens.
     *
     * @param url URL des Edge-Knotens
     * @param healthy {@code true}, wenn der Knoten als gesund gilt
     */
    public record EdgeNodeStatus(String url, boolean healthy) {}

    /**
     * Erstellt den Controller mit In-Memory-Routingindex, Metrikdiensten und HTTP-Client.
     */
    public CDNController() {
        this.routingIndex = new RoutingIndex();
        this.metricsService = new MetricsService();
        this.routerStatsService = new RouterStatsService();
        this.objectMapper = new ObjectMapper();
        this.httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }
    /**
     * Einfacher Liveness-Endpunkt für Orchestrierung und Monitoring.
     *
     * @return immer {@code ok}
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    /**
     * Readiness-Endpunkt, der die Betriebsbereitschaft des Routers signalisiert.
     *
     * @return immer {@code ready}
     */
    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        return ResponseEntity.ok("ready");
    }

    /**
     * Routing-Logik: Wählt eine Edge-Node mittels Round-Robin aus der Region aus.
     *
     * @param path angefragter Dateipfad
     * @param regionQuery optionale Region als Query-Parameter
     * @param clientIdQuery optionale Client-ID als Query-Parameter
     * @param regionHeader optionale Region aus dem Header {@code X-Client-Region}
     * @param clientIdHeader optionale Client-ID aus dem Header {@code X-Client-Id}
     * @return Redirect auf die gewählte Edge-URL oder eine Fehlermeldung
     */
    @GetMapping("/files/{path:.+}")
    public ResponseEntity<?> routeToEdge(
            @PathVariable("path") String path,
            // schauen nach der Region des Nutzers (Query Paramater)
            @RequestParam(value = "region", required = false) String regionQuery,
            // optional explizite Client-ID (lokales Tracking ohne IP)
            @RequestParam(value = "clientId", required = false) String clientIdQuery,
            // im HTTP-Header
            @RequestHeader(value = "X-Client-Region", required = false) String regionHeader,
            @RequestHeader(value = "X-Client-Id", required = false) String clientIdHeader) {

        // Region mitgeben oder automatisch mitsenden
        String region = (regionQuery != null && !regionQuery.isBlank()) ? regionQuery : regionHeader;
        String clientId = selectNonBlank(clientIdQuery, clientIdHeader);

        if (region == null || region.isBlank()) {
            metricsService.recordError(); // ungültige Anfrage loggen
            routerStatsService.recordError();
            // Beispiel für Fehlermeldung im Body:
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Fehler: Region fehlt. Bitte 'region' Query-Parameter oder 'X-Client-Region' Header setzen.");
        }

        // Neue Anfrage für Region XY
        metricsService.recordRequest(region);
        routerStatsService.recordRequest(region, clientId);
        // Round Robin Algorithmus wird aufgerufen
        EdgeNode selectedNode = routingIndex.getNextNode(region);

        // Region ist bekannt, aber aktuell ist kein Server darin angemeldet
        if (selectedNode == null) {
            metricsService.recordError();
            routerStatsService.recordError();
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Fehler: Keine verfügbaren Edge-Nodes für Region '" + region + "' gefunden.");
        }

        // Zähler wird hochgezählt
        metricsService.recordNodeSelection(selectedNode.url());
        // Wegweise wird gebaut
        String location = selectedNode.url() + "/api/edge/files/" + path;

        HttpHeaders headers = new HttpHeaders();
        // Ziel URL wird wird in den Header der Antwort geschrieben
        headers.setLocation(URI.create(location));
        return new ResponseEntity<>(headers, HttpStatus.TEMPORARY_REDIRECT);
    }

    /**
     * Verwaltungs-API für die Pflege des Routingindexes.
     */
    @RestController
    @RequestMapping("/api/cdn/routing")
    public class RoutingAdminApi {

        /**
         * Einzelanweisung für den Bulk-Endpunkt.
         *
         * @param region Zielregion
         * @param url URL des Edge-Knotens
         * @param action Aktion ({@code add} oder {@code remove})
         */
        public record BulkRequest(String region, String url, String action) {}

        /**
         * Ergebnisdatensatz für eine verarbeitete Bulk-Anweisung.
         *
         * @param region betroffene Region
         * @param url betroffene Knoten-URL
         * @param status Verarbeitungsergebnis
         */
        public record BulkResponse(String region, String url, String status) {}

        // Wenn wir Kapazität in einer Region erhöhen wollen, senden wir POST-Request an diesen Endpunkt, region und url
        // wird entgegengenommen und im RoutingIndex eingetragen
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

        // Admin schickt eine Liste (JSON-Array) mit Befehlen an den Router, er geht die Liste durch und entscheidet, ob
        // Server hinzugefügt oder gelöscht wird
        // anstatt für jeden Server eine einzelne HTTP Anfrage zu senden, erlaubt dieser Endpunkt eine Sammel Anfrage
        // für große Netzwerkkonfigurationen
        /**
         * Verarbeitet eine Liste von Add/Remove-Anweisungen in einem Request.
         *
         * @param requests Bulk-Anweisungen
         * @return Ergebnisliste pro Anweisung
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

        // Server aus System entfernen
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

        // Router geht seine Liste durch und fragt (falls gewünscht) bei jedem einzelnen Edge Server, ob er noch gesund
        // ist
        /**
         * Gibt den aktuellen Routingindex zurück und prüft optional die Knotengesundheit.
         *
         * @param checkHealth aktiviert aktive Health-Checks pro Knoten
         * @return Knotenstatus nach Region gruppiert
         */
        @GetMapping
        public ResponseEntity<Map<String, List<EdgeNodeStatus>>> getIndex(
                @RequestParam(value = "checkHealth", defaultValue = "false") boolean checkHealth) {

            Map<String, List<EdgeNode>> rawIndex =
                    routingIndex.getRawIndex(); // alle regestrierte Server aus dem Speicher
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

        /**
         * Liefert eine Momentaufnahme der Router-Metriken.
         *
         * @return Metriken als Schlüssel/Wert-Struktur
         */
        @GetMapping("/metrics")
        public ResponseEntity<Map<String, Object>>
        getMetrics() { // Map<String, Object> ist ein Container, der die Statistiken in Schlüssel Wert Paaren
            // speichert
            return ResponseEntity.ok(
                    metricsService.getSnapshot()); // metricService nach Kopie fragen --> zurück an Admin in JSON Format
        }

        /**
         * Führt asynchron einen Health-Check gegen den angegebenen Edge-Knoten aus.
         *
         * @param node zu prüfender Knoten
         * @return Future mit {@code true}, wenn HTTP 200 zurückgegeben wurde
         */
        private CompletableFuture<Boolean> checkNodeHealth(EdgeNode node) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(node.url() + "/api/edge/health")) // Edge + Endpoint
                    .timeout(Duration.ofSeconds(1)) // wartet 1 sek, wenn Edge nicht antwortet --> krank/überlastet
                    .GET() // Einfache Abfrage ohne Daten zu senden
                    .build();

            return httpClient
                    // Router schickt abfrage ab, wartet aber nicht blockierend drauf
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    // Wenn eine Antwort kommt prüfen wir den Statuscode, falls 200 --> Server gesund
                    .thenApply(response -> response.statusCode() == 200)
                    // Bei keiner Antwort (Verbindungsfehler/Zeitüberschreitung) --> false
                    .exceptionally(ex -> false);
        }
    }

    /**
     * Liefert den ersten nicht-leeren String aus bevorzugtem und alternativem Wert.
     *
     * @param preferred bevorzugter Wert
     * @param fallback alternativer Wert
     * @return bereinigter String oder {@code null}, wenn beide Werte leer sind
     */
    private String selectNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    /**
     * Admin-API für aggregierte Router-/Edge-Statistiken.
     */
    @RestController
    @RequestMapping("/api/cdn/admin")
    public class AdminStatsApi {

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
            Map<String, Integer> nodesByRegion = new java.util.HashMap<>();
            rawIndex.forEach((region, nodes) -> nodesByRegion.put(region, nodes.size()));

            long cacheHits = 0;
            long cacheMisses = 0;
            long filesCached = 0;
            java.util.List<String> edgeErrors = new java.util.ArrayList<>();

            if (aggregateEdge) {
                for (List<EdgeNode> nodes : rawIndex.values()) {
                    for (EdgeNode node : nodes) {
                        try {
                            HttpRequest request = HttpRequest.newBuilder()
                                    .uri(URI.create(node.url() + "/api/edge/admin/stats?windowSec=" + safeWindow))
                                    .timeout(Duration.ofSeconds(2))
                                    .GET()
                                    .build();

                            HttpResponse<String> response =
                                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                                EdgeStatsPayload payload =
                                        objectMapper.readValue(response.body(), EdgeStatsPayload.class);

                                cacheHits += payload.cacheHits();
                                cacheMisses += payload.cacheMisses();
                                filesCached += payload.filesCached();
                            } else {
                                edgeErrors.add(node.url() + " -> HTTP " + response.statusCode());
                            }

                        } catch (Exception ex) {
                            edgeErrors.add(node.url() + " -> " + ex.getClass().getSimpleName());
                        }
                    }
                }
            }

            double cacheHitRatio =
                    (cacheHits + cacheMisses) == 0 ? 0.0 : (double) cacheHits / (cacheHits + cacheMisses);

            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("timestamp", java.time.Instant.now().toString());
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

    /**
     * JSON-Payload des Edge-Stats-Endpunkts, wie er vom Router eingelesen wird.
     *
     * @param cacheHits Anzahl Cache-Hits
     * @param cacheMisses Anzahl Cache-Misses
     * @param filesCached Anzahl aktuell gecachter Dateien
     */
    public record EdgeStatsPayload(long cacheHits, long cacheMisses, long filesCached) {}

    /**
     * Verwaltet einfache Router-Metriken im Speicher.
     */
    public static class MetricsService {
        private final AtomicLong totalRequests =
                new AtomicLong(0); // Zählt Anfragen, die den Router erreicht (Atomic: hochzählen passiert sicher)
        private final AtomicLong routingErrors =
                new AtomicLong(0); // Zählt routingErrors (Region vergessen, kein Server da)
        private final Map<String, AtomicLong> regionStats =
                new ConcurrentHashMap<>(); // Stats über Regionen "EU"--> 500 Anfragen
        private final Map<String, AtomicLong> nodeSelectionStats =
                new ConcurrentHashMap<>(); // Wie Edges gewählt werden (Round Robin) faire Verteilung

        /**
         * Erfasst eine neue Anfrage für eine Region.
         *
         * @param region angefragte Region
         */
        public void recordRequest(String region) {
            totalRequests.incrementAndGet(); // globale Zähler für alle Anfragen inkrementieren
            // Wenn noch kein Eintrag für RegionXY vorhanden ist, wird einer erstellt und Zähler auf 0 gesetzt, dann
            // neuen oder alten Zähler erhöhen
            regionStats.computeIfAbsent(region, k -> new AtomicLong(0)).incrementAndGet();
        }

        /**
         * Erhöht den Auswahlzähler für einen Edge-Knoten.
         *
         * @param url URL des ausgewählten Knotens
         */
        public void recordNodeSelection(String url) {
            // Wenn es noch keinen Zähler für diesen Server gibt, falls er zum ersten mal gewählt wurde, erstelle neuen
            // Eintrag mit 0, falls bekannt nimm bestehenden Zähler, danach erhöhen
            nodeSelectionStats.computeIfAbsent(url, k -> new AtomicLong(0)).incrementAndGet();
        }

        /**
         * Erfasst einen Routingfehler.
         */
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

    /**
     * In-Memory-Index für Edge-Knoten je Region inklusive Round-Robin-Zählern.
     */
    public static class RoutingIndex {
        // Key = Region, Value = Liste von Servern (O(1) Zugriff über Index möglich)
        private final Map<String, List<EdgeNode>> regionToNodes = new ConcurrentHashMap<>();
        // Für jede Region wird eine Zahl gespeichert, für das Durchzählen beim Round Robin
        // Atomic Integar, damit wird verhinder, dass Zählerstände falsch berechnet werden
        // ConcurrentHashMap, um Exception zu vermeiden, wenn Daten gelesen und gleichzeitig verändert werden
        private final Map<String, AtomicInteger> regionCounters = new ConcurrentHashMap<>();

        /**
         * Fügt einen Knoten für eine Region hinzu, sofern er noch nicht existiert.
         *
         * @param region Zielregion
         * @param node hinzuzufügender Knoten
         */
        public void addEdge(String region, EdgeNode node) {
            if (region != null && node != null) {
                List<EdgeNode> nodes = regionToNodes.computeIfAbsent(region, k -> new CopyOnWriteArrayList<>());

                // Duplikatsprüfung: Nur hinzufügen, wenn der Knoten noch nicht existiert
                if (!nodes.contains(node)) {
                    nodes.add(node);
                }

                // Zähler auf 0 setzen, falls Region neu
                regionCounters.putIfAbsent(region, new AtomicInteger(0));
            }
        }

        /**
         * Wählt den nächsten Knoten einer Region via Round-Robin aus.
         *
         * @param region Zielregion
         * @return nächster Knoten oder {@code null}, falls keiner vorhanden ist
         */
        public EdgeNode getNextNode(String region) {
            // schaut nach der angefragten region und holt die liste der server
            List<EdgeNode> nodes = regionToNodes.get(region);

            // falls region nicht existiert oder keine server vorhanden (O(1) lookup)
            if (nodes == null || nodes.isEmpty()) return null;

            // holen uns den Zähler, der für Region XY zuständig ist
            AtomicInteger counter = regionCounters.get(region);

            // Round-Robin Logik: Index berechnen ohne Kopieren der Liste (O(1))
            // Die Liste 'nodes' ist thread-sicher (CopyOnWriteArrayList)
            int index = Math.abs(counter.getAndIncrement() % nodes.size());

            // Direktes Abgreifen des Elements über den Index (O(1))
            return nodes.get(index);
        }

        // Wenn Admin Server löscht, oder wenn System erkennt, dass ein Server offline ist
        /**
         * Entfernt einen Knoten aus einer Region.
         *
         * @param region Zielregion
         * @param node zu entfernender Knoten
         * @param removeIfEmpty entfernt die Region vollständig, wenn sie leer ist
         * @return {@code true}, wenn der Knoten entfernt wurde
         */
        public boolean removeEdge(String region, EdgeNode node, boolean removeIfEmpty) {
            // Gibt es Region Xy?
            List<EdgeNode> nodes = regionToNodes.get(region);
            // konnte nichts löschen, weil da war nichts
            if (nodes == null) return false;

            // spezifischer Server dieser Region wird gelöscht
            // true, wenn Server gefunden und gelöscht wurde
            boolean removed = nodes.remove(node);

            // wurde gerade erfolgreich Server gelöscht? Ist die Region jetzt komplett leer? wenn removeIfEmpty true
            // übergeben wurde --> ganzes Verzeichnis löschen
            // Dann werden die Region und der dazugehörige Zähler aus dem Speicher gelöscht
            if (removed && nodes.isEmpty() && removeIfEmpty) {
                regionToNodes.remove(region);
                regionCounters.remove(region);
            }
            return removed;
        }

        // gibt das gesamte Verzeichnis aller Regionen und Server zurück mit nur Lesezugriff
        /**
         * Gibt den unveränderlichen Blick auf den aktuellen Routingindex zurück.
         *
         * @return Regionen mit ihren registrierten Knoten
         */
        public Map<String, List<EdgeNode>> getRawIndex() {
            return Collections.unmodifiableMap(regionToNodes);
        }

        // Leert alle Verzeichnisse der Regionen als auch Zählerstände (Alles löschen Knopf)
        /**
         * Löscht den kompletten Routingindex inklusive Round-Robin-Zählern.
         */
        public void clear() {
            regionToNodes.clear();
            regionCounters.clear();
        }
    }
    /**
     * Admin-API zur Verwaltung des Cache-Status über ganze Regionen hinweg.
     */
    @RestController
    @RequestMapping("/api/cdn/admin/cache")
    public class CacheAdminApi {

        /**
         * Invalidiert eine konkrete Datei auf allen Knoten einer Region.
         *
         * @param region Zielregion
         * @param path zu löschender Dateipfad
         * @return Aggregiertes Ergebnis der Edge-Aufrufe
         */
        @DeleteMapping("/region/{region}/files/{path:.+}")
        public ResponseEntity<?> invalidatePath(@PathVariable String region, @PathVariable String path) {
            return broadcast(region, "/api/edge/cache/files/" + path);
        }

        /**
         * Invalidiert alle Cache-Einträge einer Region mit einem gemeinsamen Präfix.
         *
         * @param region Zielregion
         * @param value Präfix der zu löschenden Einträge
         * @return Aggregiertes Ergebnis der Edge-Aufrufe
         */
        @DeleteMapping("/region/{region}/prefix")
        public ResponseEntity<?> invalidatePrefix(@PathVariable String region, @RequestParam String value) {
            return broadcast(region, "/api/edge/cache/prefix?value=" + value);
        }

        /**
         * Löscht den kompletten Cache aller Knoten einer Region.
         *
         * @param region Zielregion
         * @return Aggregiertes Ergebnis der Edge-Aufrufe
         */
        @DeleteMapping("/region/{region}/all")
        public ResponseEntity<?> clearRegion(@PathVariable String region) {
            return broadcast(region, "/api/edge/cache/all");
        }

        /**
         * Sendet den Löschbefehl an alle Edge-Nodes der Region und sammelt die Statuscodes.
         *
         * @param region Zielregion
         * @param endpoint auf den Edge-Knoten aufzurufender Invalidation-Endpunkt
         * @return Ergebnisliste pro Edge-Knoten oder 404 bei unbekannter Region
         */
        private ResponseEntity<Map<String, Object>> broadcast(String region, String endpoint) {
            List<EdgeNode> nodes = routingIndex.getRawIndex().get(region);
            if (nodes == null || nodes.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Region nicht gefunden"));
            }

            /*API geht jeden gefundenen Server (node) einzeln durch und schickt ihm einen Löschbefehl:
             * Sie schickt diese asynchron (sendAsync) ab, damit sie nicht warten muss, bis ein Server fertig ist, bevor sie den nächsten fragt.
             */

            // Sende Anfragen parallel und warte auf alle Ergebnisse
            List<String> results = nodes.stream()
                    .map(node -> httpClient
                            .sendAsync(
                                    HttpRequest.newBuilder()
                                            .uri(URI.create(node.url() + endpoint))
                                            .DELETE()
                                            .build(),
                                    HttpResponse.BodyHandlers.ofString())
                            .thenApply(res -> node.url() + ": " + res.statusCode())
                            .exceptionally(ex -> node.url() + ": Fehler")
                            .join()) // Einfaches Warten pro Node im Stream
                    .toList();

            return ResponseEntity.ok(Map.of("region", region, "results", results));
        }
    }
}
