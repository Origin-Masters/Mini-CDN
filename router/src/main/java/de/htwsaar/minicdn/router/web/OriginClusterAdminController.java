package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.service.OriginClusterService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-API zur Runtime-Verwaltung von Origin-Hot-Spares über den Router.
 */
@RestController
@RequestMapping("/api/cdn/admin/origin")
public class OriginClusterAdminController {

    private final OriginClusterService originClusterService;

    public OriginClusterAdminController(OriginClusterService originClusterService) {
        this.originClusterService = originClusterService;
    }

    @GetMapping("/cluster")
    public ResponseEntity<OriginClusterService.OriginClusterSnapshot> getCluster(
            @RequestParam(value = "checkHealth", defaultValue = "false") boolean checkHealth) {
        return ResponseEntity.ok(originClusterService.snapshot(checkHealth));
    }

    @PostMapping("/spares")
    public ResponseEntity<?> addSpare(@RequestParam("url") String url) {
        try {
            originClusterService.addSpare(url);
            return ResponseEntity.status(HttpStatus.CREATED).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/spares")
    public ResponseEntity<?> removeSpare(@RequestParam("url") String url) {
        try {
            boolean removed = originClusterService.removeSpare(url);
            if (!removed) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "spare origin not found"));
            }
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/promote")
    public ResponseEntity<?> promote(@RequestParam("url") String url) {
        try {
            boolean promoted = originClusterService.promoteToActive(url);
            if (!promoted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "spare origin not found"));
            }
            return ResponseEntity.ok(originClusterService.snapshot(false));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/failover/check")
    public ResponseEntity<OriginClusterService.OriginClusterSnapshot> runFailoverCheck() {
        originClusterService.failoverIfActiveIsUnhealthy();
        return ResponseEntity.ok(originClusterService.snapshot(true));
    }
}
