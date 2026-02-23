package de.htwsaar.minicdn.edge.web;

import de.htwsaar.minicdn.edge.service.EdgeFileService;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-API zur Cache-Invalidierung.
 * Bestehende API {@code /api/edge/cache} bleibt vollständig kompatibel.
 *
 * <ul>
 *   <li>DELETE /api/edge/cache/files/{path} – einzelne Datei</li>
 *   <li>DELETE /api/edge/cache/prefix?value=… – Pfad-Prefix</li>
 *   <li>DELETE /api/edge/cache/all – gesamter Cache</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/edge/cache")
@Profile("edge")
public class EdgeCacheAdminController {

    private final EdgeFileService fileService;

    /**
     * Constructor Injection.
     *
     * @param fileService fachlicher Service (enthält Cache-Zugriff)
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
        boolean removed = fileService.invalidateFile(path);
        return ResponseEntity.ok(Map.of("path", path, "status", removed ? "invalidated" : "not in cache"));
    }

    /**
     * Invalidiert alle Cache-Einträge mit dem gegebenen Pfad-Prefix.
     *
     * @param value Pfad-Prefix (Query-Parameter)
     * @return Anzahl invalidierter Einträge
     */
    @DeleteMapping("/prefix")
    public ResponseEntity<Map<String, Object>> invalidateByPrefix(@RequestParam("value") String value) {
        int count = fileService.invalidatePrefix(value);
        return ResponseEntity.ok(Map.of(
                "prefix", value,
                "invalidatedCount", count));
    }

    /**
     * Leert den gesamten Cache.
     *
     * @return Bestätigung
     */
    @DeleteMapping("/all")
    public ResponseEntity<Map<String, String>> clearAll() {
        fileService.clearCache();
        return ResponseEntity.ok(Map.of("status", "cache cleared"));
    }
}
