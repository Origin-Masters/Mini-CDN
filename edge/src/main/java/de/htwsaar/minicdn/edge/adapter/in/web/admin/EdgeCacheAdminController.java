package de.htwsaar.minicdn.edge.adapter.in.web.admin;

import de.htwsaar.minicdn.edge.application.file.EdgeFileService;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-API zur Cache-Invalidierung.
 *
 * <ul>
 *   <li>DELETE /api/edge/admin/cache/files/{path} – einzelne Datei</li>
 *   <li>DELETE /api/edge/admin/cache/prefixes?value=… – Pfad-Prefix</li>
 *   <li>DELETE /api/edge/admin/cache/files – gesamter Cache</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/edge/admin/cache")
@Profile("edge")
public class EdgeCacheAdminController {

    private final EdgeFileService fileService;

    /**
     * Erstellt den HTTP-Adapter für Cache-Administrations-Endpunkte.
     *
     * @param fileService fachlicher Service mit Cache-Zugriff
     */
    public EdgeCacheAdminController(EdgeFileService fileService) {
        this.fileService = fileService;
    }

    /**
     * Invalidiert eine einzelne Datei im Cache.
     *
     * @param path Dateipfad (z. B. {@code videos/intro.mp4})
     * @return Status-Nachricht
     */
    @DeleteMapping("/files/{path:.+}")
    public ResponseEntity<Map<String, String>> invalidateFile(@PathVariable("path") String path) {
        try {
            boolean removed = fileService.invalidateFile(path);
            return ResponseEntity.ok(Map.of("path", path, "status", removed ? "invalidated" : "not in cache"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Invalidiert alle Cache-Einträge mit dem gegebenen Pfad-Prefix.
     *
     * @param value Pfad-Prefix (Query-Parameter)
     * @return Anzahl invalidierter Einträge
     */
    @DeleteMapping("/prefixes")
    public ResponseEntity<Map<String, Object>> invalidateByPrefix(@RequestParam("value") String value) {
        try {
            int count = fileService.invalidatePrefix(value);
            return ResponseEntity.ok(Map.of(
                    "prefix", value,
                    "invalidatedCount", count));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Leert den gesamten Cache.
     *
     * @return Bestätigung
     */
    @DeleteMapping("/files")
    public ResponseEntity<Map<String, String>> clearAll() {
        fileService.clearCache();
        return ResponseEntity.ok(Map.of("status", "cache cleared"));
    }
}
