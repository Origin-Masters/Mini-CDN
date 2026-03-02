package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.service.RouterAdminService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP-Adapter fuer aggregierte Router- und Edge-Statistiken.
 */
@RestController
@RequestMapping("/api/cdn/admin")
public class AdminStatsController {

    private final RouterAdminService routerAdminService;

    public AdminStatsController(RouterAdminService routerAdminService) {
        this.routerAdminService = routerAdminService;
    }

    /**
     * Liefert aggregierte Statistikdaten für den angegebenen Zeitbereich.
     *
     * @param windowSec Zeitfenster in Sekunden für die Aggregation
     * @param aggregateEdge ob Edge-Statistiken serverseitig aggregiert werden
     * @return Map mit Statistikdaten
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(
            @RequestParam(value = "windowSec", defaultValue = "60") int windowSec,
            @RequestParam(value = "aggregateEdge", defaultValue = "true") boolean aggregateEdge) {
        return ResponseEntity.ok(routerAdminService.getStats(windowSec, aggregateEdge));
    }
}
