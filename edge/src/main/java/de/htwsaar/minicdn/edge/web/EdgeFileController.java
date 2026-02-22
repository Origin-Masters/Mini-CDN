package de.htwsaar.minicdn.edge.web;

import de.htwsaar.minicdn.edge.EdgeMetricsService;
import de.htwsaar.minicdn.edge.domain.CacheDecision;
import de.htwsaar.minicdn.edge.domain.FilePayload;
import de.htwsaar.minicdn.edge.service.EdgeFileService;
import de.htwsaar.minicdn.edge.service.EdgeUpstreamException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
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
    @GetMapping("/files/{path:.+}")
    public ResponseEntity<byte[]> getFile(@PathVariable("path") String path) {
        try {
            FilePayload payload = fileService.getFile(path);
            recordDecision(payload.cache());
            return ResponseEntity.ok().headers(buildHeaders(payload)).body(payload.body());
        } catch (EdgeUpstreamException ex) {
            return ResponseEntity.status(ex.getStatusCode()).build();
        }
    }

    /**
     * Liefert nur die HTTP-Header einer Datei (kein Body).
     *
     * @param path angeforderter Dateipfad
     * @return HTTP-Header der Datei
     */
    @RequestMapping(value = "/files/{path:.+}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headFile(@PathVariable("path") String path) {
        try {
            FilePayload payload = fileService.headFile(path);
            recordDecision(payload.cache());
            return ResponseEntity.ok().headers(buildHeaders(payload)).build();
        } catch (EdgeUpstreamException ex) {
            return ResponseEntity.status(ex.getStatusCode()).build();
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

    private void recordDecision(CacheDecision decision) {
        if (decision == CacheDecision.HIT) metricsService.recordHit();
        else metricsService.recordMiss();
    }
}
