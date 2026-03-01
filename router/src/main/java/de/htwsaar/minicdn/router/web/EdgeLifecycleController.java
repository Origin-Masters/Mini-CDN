package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.dto.AutoStartEdgesRequest;
import de.htwsaar.minicdn.router.dto.StartEdgeRequest;
import de.htwsaar.minicdn.router.service.EdgeLifecycleService;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP-Adapter zum Starten und Stoppen verwalteter Edge-Instanzen.
 */
@RestController
@RequestMapping("/api/cdn/admin/edges")
public class EdgeLifecycleController {

    private final EdgeLifecycleService lifecycleService;

    public EdgeLifecycleController(EdgeLifecycleService lifecycleService) {
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService must not be null");
    }

    /**
     * Startet eine verwaltete Edge-Instanz.
     *
     * @param req Startparameter fuer die Edge-Instanz
     * @return gestartete Instanz oder Fehlermeldung
     */
    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody StartEdgeRequest req) {
        lifecycleService.ensureEnabled();
        try {
            var started = lifecycleService.start(
                    req.region(), req.port(), req.originBaseUrl(), req.autoRegister(), req.waitUntilReady());
            return ResponseEntity.status(HttpStatus.CREATED).body(started);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Edge-Start fehlgeschlagen: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /**
     * Startet mehrere Edge-Instanzen automatisch.
     *
     * @param req Startparameter fuer den Auto-Start
     * @return Liste gestarteter Instanzen oder Fehlermeldung
     */
    @PostMapping("/start/auto")
    public ResponseEntity<?> startAuto(@RequestBody AutoStartEdgesRequest req) {
        lifecycleService.ensureEnabled();
        try {
            var started = lifecycleService.startAuto(
                    req.region(), req.count(), req.originBaseUrl(), req.autoRegister(), req.waitUntilReady());
            return ResponseEntity.status(HttpStatus.CREATED).body(started);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Auto-Start fehlgeschlagen: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /**
     * Stoppt eine verwaltete Edge-Instanz.
     *
     * @param instanceId ID der Instanz
     * @param deregister ob die Instanz deregistriert werden soll
     * @return OK bei Erfolg, sonst NOT_FOUND
     */
    @DeleteMapping("/{instanceId}")
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

    /**
     * Listet alle verwalteten Edge-Instanzen.
     *
     * @return Liste der Instanzen
     */
    @GetMapping("/managed")
    public ResponseEntity<?> listManaged() {
        lifecycleService.ensureEnabled();
        return ResponseEntity.ok(lifecycleService.listManaged());
    }
}
