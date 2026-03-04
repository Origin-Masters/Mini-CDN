package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.service.RouterAdminFileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP-Adapter für Admin-File-Operationen über den Router.
 * Leitet Schreiboperationen an Origin weiter und invalidiert automatisch Edge-Caches.
 */
@RestController
@RequestMapping("/api/cdn/admin/files")
public class RouterAdminFileController {

    private final RouterAdminFileService adminFileService;

    public RouterAdminFileController(RouterAdminFileService adminFileService) {
        this.adminFileService = adminFileService;
    }

    /**
     * Hochladen einer Datei zum Origin und invalidieren aller Edge-Caches in der Region (oder global).
     */
    @PutMapping("/{path:.+}")
    public ResponseEntity<?> uploadFile(
            @PathVariable("path") String path,
            @RequestParam(value = "region", required = false) String region,
            @RequestBody byte[] body) {

        var result = adminFileService.uploadAndInvalidate(path, body, region);

        if (result.success()) {
            return ResponseEntity.ok(result.toMap());
        }
        return ResponseEntity.status(result.httpStatus()).body(result.toMap());
    }

    /**
     * Löschen einer Datei vom Origin und invalidieren aller Edge-Caches in der Region (oder global).
     */
    @DeleteMapping("/{path:.+}")
    public ResponseEntity<?> deleteFile(
            @PathVariable("path") String path, @RequestParam(value = "region", required = false) String region) {

        var result = adminFileService.deleteAndInvalidate(path, region);

        if (result.success()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(result.httpStatus()).body(result.toMap());
    }

    /**
     * Listet alle Dateien im Origin auf (inkl. Pagination). Ruft den Origin direkt über den Router-Admin-API-Endpunkt ab.
     */
    @GetMapping
    public ResponseEntity<?> listFiles(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {

        var result = adminFileService.listOriginFiles(page, size);
        return ResponseEntity.status(result.httpStatus()).body(result.body());
    }
}
