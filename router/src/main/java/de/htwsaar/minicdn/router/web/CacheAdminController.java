package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.service.EdgeHttpClient;
import de.htwsaar.minicdn.router.service.RoutingIndex;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-API zur Verwaltung des Cache-Status über ganze Regionen hinweg.
 */
@RestController
@RequestMapping("/api/cdn/admin/cache")
public class CacheAdminController {

    private final RoutingIndex routingIndex;
    private final EdgeHttpClient edgeHttpClient;

    public CacheAdminController(RoutingIndex routingIndex, EdgeHttpClient edgeHttpClient) {
        this.routingIndex = routingIndex;
        this.edgeHttpClient = edgeHttpClient;
    }

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

        List<CompletableFuture<String>> futures = nodes.stream()
                .map(node -> edgeHttpClient
                        .sendDelete(node, endpoint)
                        .thenApply(res -> node.url() + ": " + res.statusCode())
                        .exceptionally(ex -> node.url() + ": Fehler"))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<String> results = futures.stream().map(CompletableFuture::join).toList();

        return ResponseEntity.ok(Map.of("region", region, "results", results));
    }
}
