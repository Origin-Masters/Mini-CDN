package de.htwsaar.minicdn.edge.web;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health- und Readiness-Probes der Edge-Node.
 * Bestehende Endpunkte {@code /api/edge/health} und {@code /api/edge/ready} bleiben kompatibel.
 */
@RestController
@RequestMapping("/api/edge")
@Profile("edge")
public class EdgeProbeController {

    /** @return HTTP 200 "ok" */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    /** @return HTTP 200 "ready" */
    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        return ResponseEntity.ok("ready");
    }
}
