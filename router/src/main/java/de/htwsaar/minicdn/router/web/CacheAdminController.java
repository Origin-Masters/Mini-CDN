package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.service.RouterAdminService;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP-Adapter für regionenweite Cache-Invalidierung.
 */
@RestController
@RequestMapping("/api/cdn/admin/cache")
public class CacheAdminController {

    private final RouterAdminService routerAdminService;

    public CacheAdminController(RouterAdminService routerAdminService) {
        this.routerAdminService = routerAdminService;
    }

    /**
     * Invalidiert eine konkrete Datei in der angegebenen Region.
     *
     * @param region Region der Edge-Cluster
     * @param path Pfad der Datei relativ zum Origin
     * @return Ergebnis der Invalidierung
     */
    @DeleteMapping("/region/{region}/files/{path:.+}")
    public ResponseEntity<?> invalidatePath(@PathVariable String region, @PathVariable String path) {
        return execute(() -> routerAdminService.invalidatePath(region, path));
    }

    /**
     * Invalidiert alle Dateien mit dem angegebenen Prefix in der Region.
     *
     * @param region Region der Edge-Cluster
     * @param value Prefix für die Invalidierung
     * @return Ergebnis der Invalidierung
     */
    @DeleteMapping("/region/{region}/prefix")
    public ResponseEntity<?> invalidatePrefix(@PathVariable String region, @RequestParam String value) {
        return execute(() -> routerAdminService.invalidatePrefix(region, value));
    }

    /**
     * Entfernt den kompletten Cache einer Region.
     *
     * @param region Region der Edge-Cluster
     * @return Ergebnis der Invalidierung
     */
    @DeleteMapping("/region/{region}/all")
    public ResponseEntity<?> clearRegion(@PathVariable String region) {
        return execute(() -> routerAdminService.clearRegion(region));
    }

    /**
     * Fuehrt eine Cache-Operation aus und mappt bekannte Fehler auf HTTP-Status.
     *
     * @param operation auszufuehrende Operation
     * @return ResponseEntity mit Ergebnis oder Fehlermeldung
     */
    private ResponseEntity<?> execute(CacheOperation operation) {
        try {
            return ResponseEntity.ok(operation.run());
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Funktionale Schnittstelle fuer Cache-Operationen.
     */
    @FunctionalInterface
    private interface CacheOperation {
        Map<String, Object> run();
    }
}
