package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.dto.BulkRequest;
import de.htwsaar.minicdn.router.dto.BulkResponse;
import de.htwsaar.minicdn.router.dto.EdgeNodeStatus;
import de.htwsaar.minicdn.router.service.RouterAdminService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP-Adapter für Pflege und Abfrage des Routing-Indexes.
 */
@RestController
@RequestMapping("/api/cdn/routing")
public class RoutingAdminController {

    private final RouterAdminService routerAdminService;

    public RoutingAdminController(RouterAdminService routerAdminService) {
        this.routerAdminService = routerAdminService;
    }

    /**
     * Fuegt eine Edge-Instanz zur Region hinzu.
     *
     * @param region Region der Edge-Cluster
     * @param url Basis-URL der Edge-Instanz
     * @return Created bei Erfolg
     */
    @PostMapping
    public ResponseEntity<Void> addEdgeNode(
            @RequestParam(value = "region") String region, @RequestParam(value = "url") String url) {

        routerAdminService.addEdgeNode(region, url);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Fuehrt mehrere Routing-Updates in einem Request aus.
     *
     * @param requests Liste der Bulk-Updates
     * @return Ergebnisliste je Update
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<BulkResponse>> bulkUpdate(@RequestBody List<BulkRequest> requests) {
        return ResponseEntity.ok(routerAdminService.bulkUpdate(requests));
    }

    /**
     * Entfernt eine Edge-Instanz aus der Region.
     *
     * @param region Region der Edge-Cluster
     * @param url Basis-URL der Edge-Instanz
     * @return OK bei Erfolg, sonst NOT_FOUND
     */
    @DeleteMapping
    public ResponseEntity<?> deleteEdgeNode(
            @RequestParam(value = "region") String region, @RequestParam(value = "url") String url) {

        boolean removed = routerAdminService.deleteEdgeNode(region, url);
        return removed
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Knoten " + url + " in Region " + region + " nicht gefunden.");
    }

    /**
     * Liefert den aktuellen Routing-Index.
     *
     * @param checkHealth ob der Health-Check der Edges ausgefuehrt wird
     * @return Routing-Index nach Region
     */
    @GetMapping
    public ResponseEntity<Map<String, List<EdgeNodeStatus>>> getIndex(
            @RequestParam(value = "checkHealth", defaultValue = "false") boolean checkHealth) {
        return ResponseEntity.ok(routerAdminService.getIndex(checkHealth));
    }
}
