package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.dto.AutoStartEdgesRequest;
import de.htwsaar.minicdn.router.dto.StartEdgeRequest;
import de.htwsaar.minicdn.router.service.EdgeLifecycleService;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-API zum Starten (Dev/Demo) und Verwalten von Edge-Instanzen über den Router.
 *
 * <p>Controller ist absichtlich thin: Validierung/Start/Stop/Readiness liegen im Service.</p>
 */
@RestController
@RequestMapping("api/cdn/admin/edges")
public class EdgeLifecycleController {

    private final EdgeLifecycleService lifecycleService;

    public EdgeLifecycleController(EdgeLifecycleService lifecycleService) {
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService must not be null");
    }

    @PostMapping("start")
    public ResponseEntity<?> start(@RequestBody StartEdgeRequest req) {
        lifecycleService.ensureEnabled();
        try {
            var started = lifecycleService.start(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(started);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Edge-Start fehlgeschlagen: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    @PostMapping("start/auto")
    public ResponseEntity<?> startAuto(@RequestBody AutoStartEdgesRequest req) {
        lifecycleService.ensureEnabled();
        try {
            var started = lifecycleService.startAuto(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(started);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Auto-Start fehlgeschlagen: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    @DeleteMapping("{instanceId}")
    public ResponseEntity<?> delete(
            @PathVariable("instanceId") String instanceId,
            @RequestParam(name = "deregister", defaultValue = "true") boolean deregister) {

        lifecycleService.ensureEnabled();

        boolean stopped = lifecycleService.stop(instanceId, deregister);
        if (!stopped) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Unbekannte instanceId (nicht managed): " + instanceId);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("managed")
    public ResponseEntity<?> listManaged() {
        lifecycleService.ensureEnabled();
        return ResponseEntity.ok(lifecycleService.listManaged());
    }
}
