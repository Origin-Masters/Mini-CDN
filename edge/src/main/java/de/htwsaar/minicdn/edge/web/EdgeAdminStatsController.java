package de.htwsaar.minicdn.edge.web;

import de.htwsaar.minicdn.edge.EdgeMetricsService;
import de.htwsaar.minicdn.edge.service.EdgeFileService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-API für Edge-Metriken.
 * Bestehende API {@code /api/edge/admin/stats} bleibt kompatibel.
 */
@RestController
@RequestMapping("/api/edge/admin/stats")
@Profile("edge")
public class EdgeAdminStatsController {

    private final EdgeMetricsService metricsService;
    private final EdgeFileService fileService;

    /**
     * Constructor Injection.
     *
     * @param metricsService Metriken-Service
     * @param fileService    Service für Cache-Größe
     */
    public EdgeAdminStatsController(EdgeMetricsService metricsService, EdgeFileService fileService) {
        this.metricsService = metricsService;
        this.fileService = fileService;
    }

    /**
     * Liefert einen Metriken-Snapshot der Edge-Node.
     *
     * @param windowSec Zeitfenster in Sekunden für Request-Rate (Standard: 60)
     * @return Metriken-Snapshot
     */
    @GetMapping
    public ResponseEntity<EdgeMetricsService.EdgeStatsSnapshot> getStats(
            @RequestParam(value = "windowSec", defaultValue = "60") int windowSec) {
        return ResponseEntity.ok(metricsService.snapshot(windowSec, fileService.cacheSize()));
    }
}
