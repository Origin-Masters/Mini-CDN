// src/main/java/de/htwsaar/minicdn/origin/web/OriginProbeController.java
package de.htwsaar.minicdn.origin.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Health/Readiness HTTP-Adapter des Origin.
 */
@RestController
@RequestMapping("/api/origin")
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
