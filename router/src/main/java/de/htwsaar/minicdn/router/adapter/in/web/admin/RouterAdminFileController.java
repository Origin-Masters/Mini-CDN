package de.htwsaar.minicdn.router.adapter.in.web.admin;

import de.htwsaar.minicdn.common.util.PathUtils;
import de.htwsaar.minicdn.router.application.admin.RouterAdminFileService;
import de.htwsaar.minicdn.router.application.admin.model.AdminFileResult;
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
    @PutMapping("/{*path}")
    public ResponseEntity<?> uploadFile(
            @PathVariable("path") String path,
            @RequestParam(value = "region", required = false) String region,
            @RequestBody byte[] body) {

        String cleanPath = PathUtils.stripLeadingSlash(path == null ? "" : path);
        var result = adminFileService.uploadAndInvalidate(cleanPath, body, region);

        if (result.success()) {
            return ResponseEntity.ok(result.toMap());
        }
        return ResponseEntity.status(toHttpStatus(result)).body(result.toMap());
    }

    /**
     * Löschen einer Datei vom Origin und invalidieren aller Edge-Caches in der Region (oder global).
     */
    @DeleteMapping("/{*path}")
    public ResponseEntity<?> deleteFile(
            @PathVariable("path") String path, @RequestParam(value = "region", required = false) String region) {

        String cleanPath = PathUtils.stripLeadingSlash(path == null ? "" : path);
        var result = adminFileService.deleteAndInvalidate(cleanPath, region);

        if (result.success()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(toHttpStatus(result)).body(result.toMap());
    }

    /**
     * Listet alle Dateien im Origin auf (inkl. Pagination). Ruft den Origin direkt über den Router-Admin-API-Endpunkt ab.
     */
    @GetMapping
    public ResponseEntity<?> listFiles(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {

        var result = adminFileService.listOriginFiles(page, size);
        return ResponseEntity.status(toHttpStatus(result)).body(result.body());
    }

    /**
     * Zeigt Metadaten einer Datei im Origin an. Ruft den Origin direkt über den Router-Admin-API-Endpunkt ab.
     */
    @GetMapping("/{*path}")
    public ResponseEntity<?> showFile(@PathVariable String path) {
        String cleanPath = PathUtils.stripLeadingSlash(path == null ? "" : path);
        var result = adminFileService.showOriginFile(cleanPath);
        return ResponseEntity.status(toHttpStatus(result)).body(result.body());
    }

    private static int toHttpStatus(AdminFileResult result) {
        if (result.outcome() == AdminFileResult.Outcome.SUCCESS) {
            return result.remoteCode() != null ? result.remoteCode() : 200;
        }
        if (result.outcome() == AdminFileResult.Outcome.REJECTED) {
            return result.remoteCode() != null ? result.remoteCode() : 502;
        }
        return 500;
    }
}
