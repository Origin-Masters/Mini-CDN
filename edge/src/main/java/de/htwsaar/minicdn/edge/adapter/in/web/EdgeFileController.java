package de.htwsaar.minicdn.edge.adapter.in.web;

import de.htwsaar.minicdn.common.util.PathUtils;
import de.htwsaar.minicdn.edge.application.file.EdgeFileService;
import de.htwsaar.minicdn.edge.application.metrics.EdgeMetricsService;
import de.htwsaar.minicdn.edge.domain.exception.IntegrityCheckFailedException;
import de.htwsaar.minicdn.edge.domain.exception.OriginAccessException;
import de.htwsaar.minicdn.edge.domain.model.CacheDecision;
import de.htwsaar.minicdn.edge.domain.model.FilePayload;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP-Adapter für Datei-Zugriffe.
 *
 * <p>Kein Fachcode hier – nur HTTP-Mapping und Fehlerbehandlung.
 * Bestehende API {@code /api/edge/files/{path}} bleibt vollständig kompatibel.</p>
 */
@RestController
@RequestMapping("/api/edge")
@Profile("edge")
public class EdgeFileController {

    private static final String X_CACHE = "X-Cache";

    private final EdgeFileService fileService;
    private final EdgeMetricsService metricsService;

    /**
     * Constructor Injection.
     *
     * @param fileService    fachlicher Datei-Service
     * @param metricsService Metriken-Service
     */
    public EdgeFileController(EdgeFileService fileService, EdgeMetricsService metricsService) {
        this.fileService = fileService;
        this.metricsService = metricsService;
    }

    /**
     * Liefert eine Datei aus dem Cache oder vom Origin.
     *
     * @param path angeforderter Dateipfad
     * @return Datei-Bytes mit Content-Type, SHA256 und X-Cache-Header
     */
    @GetMapping("/files/{*path}")
    public ResponseEntity<byte[]> getFile(@PathVariable("path") String path) {
        try {
            String cleanPath = PathUtils.normalizeRelativePath(path);
            FilePayload payload = fileService.getFile(cleanPath);
            recordDecision(cleanPath, payload.cache());
            return ResponseEntity.ok().headers(buildHeaders(payload)).body(payload.body());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IntegrityCheckFailedException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        } catch (OriginAccessException ex) {
            return ResponseEntity.status(mapStatus(ex)).build();
        }
    }

    /**
     * Liefert nur die HTTP-Header einer Datei (kein Body).
     *
     * @param path angeforderter Dateipfad
     * @return HTTP-Header der Datei
     */
    @RequestMapping(value = "/files/{*path}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headFile(@PathVariable("path") String path) {
        try {
            String cleanPath = PathUtils.normalizeRelativePath(path);
            FilePayload payload = fileService.headFile(cleanPath);
            recordDecision(null, payload.cache());
            return ResponseEntity.ok().headers(buildHeaders(payload)).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IntegrityCheckFailedException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        } catch (OriginAccessException ex) {
            return ResponseEntity.status(mapStatus(ex)).build();
        }
    }

    private HttpHeaders buildHeaders(FilePayload payload) {
        HttpHeaders h = new HttpHeaders();
        String ct = payload.contentType();
        h.setContentType(
                ct != null && !ct.isBlank() ? MediaType.parseMediaType(ct) : MediaType.APPLICATION_OCTET_STREAM);
        h.set("X-Content-SHA256", payload.sha256());
        h.set(X_CACHE, payload.cache().name());
        return h;
    }

    private HttpStatus mapStatus(OriginAccessException ex) {
        return switch (ex.getReason()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case UNAVAILABLE, INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
        };
    }
    /**
     * Erfasst die Cache-Entscheidung zusammen mit dem Dateipfad für Download-Statistiken.
     *
     * @param path Dateipfad
     * @param decision Cache-Entscheidung
     */
    private void recordDecision(String path, CacheDecision decision) {
        if (decision == CacheDecision.HIT) {
            metricsService.recordHit(path);
            return;
        }
        metricsService.recordMiss(path);
    }
}
