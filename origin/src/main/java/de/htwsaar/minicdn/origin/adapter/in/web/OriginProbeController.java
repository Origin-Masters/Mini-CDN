package de.htwsaar.minicdn.origin.adapter.in.web;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Health/Readiness HTTP-Adapter des Origin.
 */
@RestController
@RequestMapping("/api/origin")
@Profile("origin")
public class OriginProbeController {

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        return ResponseEntity.ok("ready");
    }
}
