package de.htwsaar.minicdn.router.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Probe-Endpunkte (Liveness/Readiness) für Orchestrierung und Monitoring.
 */
@RestController
@RequestMapping("/api/cdn")
public class CdnProbeController {

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
}
