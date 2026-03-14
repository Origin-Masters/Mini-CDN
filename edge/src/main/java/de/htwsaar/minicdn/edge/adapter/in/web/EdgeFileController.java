package de.htwsaar.minicdn.edge.adapter.in.web;

import de.htwsaar.minicdn.common.util.PathUtils;
import de.htwsaar.minicdn.edge.application.file.EdgeFileService;
import de.htwsaar.minicdn.edge.application.metrics.EdgeMetricsService;
import de.htwsaar.minicdn.edge.domain.exception.IntegrityCheckFailedException;
import de.htwsaar.minicdn.edge.domain.exception.OriginAccessException;
import de.htwsaar.minicdn.edge.domain.model.CacheDecision;
import de.htwsaar.minicdn.edge.domain.model.FilePayload;
import java.util.Objects;
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
    public ResponseEntity<byte[]> getFile(
            @PathVariable("path") String path,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        try {
            String cleanPath = PathUtils.normalizeRelativePath(path);
            FilePayload payload = fileService.getFile(cleanPath);
            recordDecision(cleanPath, payload.cache());

            if (rangeHeader != null && !rangeHeader.isBlank()) {
                ByteRange range = ByteRange.parse(rangeHeader, payload.body().length);
                byte[] rangeBytes = range.slice(payload.body());
                HttpHeaders partialHeaders = buildHeaders(payload, rangeBytes.length);
                partialHeaders.set(HttpHeaders.ACCEPT_RANGES, "bytes");
                partialHeaders.set(HttpHeaders.CONTENT_RANGE, range.contentRangeValue(payload.body().length));
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                        .headers(partialHeaders)
                        .body(rangeBytes);
            }

            HttpHeaders headers = buildHeaders(payload, payload.body().length);
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            return ResponseEntity.ok().headers(headers).body(payload.body());
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
            HttpHeaders headers = buildHeaders(payload, null);
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            return ResponseEntity.ok().headers(headers).build();
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (IntegrityCheckFailedException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        } catch (OriginAccessException ex) {
            return ResponseEntity.status(mapStatus(ex)).build();
        }
    }

    /**
     * Baut gemeinsame Response-Header für Dateiausgaben.
     *
     * @param payload Datei-Payload aus dem Fachservice
     * @param contentLength optionale Content-Length
     * @return vorbereitete HTTP-Header
     */
    private HttpHeaders buildHeaders(FilePayload payload, Integer contentLength) {
        HttpHeaders h = new HttpHeaders();
        String ct = payload.contentType();
        h.setContentType(
                ct != null && !ct.isBlank() ? MediaType.parseMediaType(ct) : MediaType.APPLICATION_OCTET_STREAM);
        if (contentLength != null && contentLength >= 0) {
            h.setContentLength(contentLength.longValue());
        }
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

    /**
     * Repräsentiert einen validierten Byte-Range für segmentierte Downloads.
     *
     * @param start inklusiver Startindex
     * @param end inklusiver Endindex
     */
    private record ByteRange(int start, int end) {

        /**
         * Parst einen einzelnen HTTP-Range-Header im Format {@code bytes=start-end}.
         *
         * @param header roher Headerwert
         * @param fileLength Gesamtlänge der Datei
         * @return validierter Byte-Range
         */
        static ByteRange parse(String header, int fileLength) {
            Objects.requireNonNull(header, "header");
            if (fileLength <= 0) {
                throw new IllegalArgumentException("range not satisfiable");
            }

            String value = header.trim();
            if (!value.startsWith("bytes=")) {
                throw new IllegalArgumentException("unsupported range unit");
            }

            String token = value.substring("bytes=".length());
            if (token.contains(",") || token.isBlank()) {
                throw new IllegalArgumentException("multiple ranges are not supported");
            }

            String[] parts = token.split("-", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IllegalArgumentException("invalid range");
            }

            int start = Integer.parseInt(parts[0]);
            int end = Integer.parseInt(parts[1]);

            if (start < 0 || end < start || end >= fileLength) {
                throw new IllegalArgumentException("range not satisfiable");
            }
            return new ByteRange(start, end);
        }

        /**
         * Schneidet den enthaltenen Datenbereich aus dem vollständigen Datei-Array.
         *
         * @param fullBody vollständige Datei-Bytes
         * @return Segment-Bytes entsprechend dieses Ranges
         */
        byte[] slice(byte[] fullBody) {
            int length = end - start + 1;
            byte[] segment = new byte[length];
            System.arraycopy(fullBody, start, segment, 0, length);
            return segment;
        }

        /**
         * Baut den HTTP-Headerwert für {@code Content-Range}.
         *
         * @param totalLength Gesamtlänge der Datei
         * @return Headerwert im RFC-konformen Format
         */
        String contentRangeValue(int totalLength) {
            return "bytes " + start + "-" + end + "/" + totalLength;
        }
    }
}
