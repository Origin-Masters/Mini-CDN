package de.htwsaar.minicdn.edge;

import de.htwsaar.minicdn.common.util.Sha256Util;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

/**
 * REST-Controller für den Edge-Server.
 * Verarbeitet Datei-Anfragen mit Cache-Unterstützung und leitet Anfragen bei Cache-Miss an den Origin-Server weiter.
 */
@RestController
@RequestMapping("/api/edge")
@Profile("edge")
public class EdgeController {

    private static final String SHA256_HEADER = "X-Content-SHA256";
    private static final String X_CACHE_HEADER = "X-Cache";
    private static final String CACHE_HIT = "HIT";
    private static final String CACHE_MISS = "MISS";

    @Value("${origin.base-url}")
    private String originBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final EdgeCacheService edgeCacheService;

    /**
     * Konstruktor für Dependency Injection.
     *
     * @param edgeCacheService der Cache-Service für Edge-Anfragen
     */
    public EdgeController(EdgeCacheService edgeCacheService) {
        this.edgeCacheService = edgeCacheService;
    }

    /**
     * Liefert eine Datei aus dem Cache oder vom Origin-Server.
     * Prüft bei Cache-Miss die Integrität der Datei mittels SHA-256.
     *
     * @param path der Pfad zur angeforderten Datei
     * @return die Datei als Byte-Array mit entsprechenden HTTP-Headern
     */
    @GetMapping("/files/{path:.+}")
    public ResponseEntity<byte[]> getFile(@PathVariable("path") String path) {
        EdgeCacheService.CacheEntry cached = edgeCacheService.getFresh(path);
        if (cached != null) {
            return ResponseEntity.ok()
                    .headers(headersFromCacheEntry(cached, CACHE_HIT))
                    .body(cached.body());
        }

        ResponseEntity<byte[]> originResponse = restTemplate.getForEntity(originFileUrl(path), byte[].class);

        if (!originResponse.getStatusCode().is2xxSuccessful()) {
            return passThrough(originResponse, CACHE_MISS);
        }

        byte[] body = originResponse.getBody();
        if (body == null) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(null);
        }

        String expectedSha = originResponse.getHeaders().getFirst(SHA256_HEADER);
        if (expectedSha == null || expectedSha.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(null);
        }

        String actualSha = Sha256Util.sha256Hex(body);
        if (!expectedSha.equalsIgnoreCase(actualSha)) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(null);
        }

        String contentType = originResponse.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        edgeCacheService.put(path, body, contentType, actualSha);

        return passThrough(originResponse, CACHE_MISS);
    }

    /**
     * Verarbeitet HEAD-Anfragen für Dateien.
     * Liefert nur HTTP-Header ohne Dateiinhalt.
     *
     * @param path der Pfad zur angeforderten Datei
     * @return HTTP-Header der Datei
     */
    @RequestMapping(value = "/files/{path:.+}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headFile(@PathVariable("path") String path) {
        EdgeCacheService.CacheEntry cached = edgeCacheService.getFresh(path);
        if (cached != null) {
            return ResponseEntity.ok()
                    .headers(headersFromCacheEntry(cached, CACHE_HIT))
                    .build();
        }

        ResponseEntity<Void> originResponse =
                restTemplate.exchange(originFileUrl(path), HttpMethod.HEAD, HttpEntity.EMPTY, Void.class);

        return passThrough(originResponse, CACHE_MISS);
    }

    /**
     * Health-Check-Endpoint.
     *
     * @return HTTP 200 mit "ok"
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    /**
     * Readiness-Check-Endpoint.
     *
     * @return HTTP 200 mit "ready"
     */
    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        return ResponseEntity.ok("ready");
    }

    /**
     * Baut die vollständige URL zum Origin-Server für eine Datei.
     *
     * @param path der Dateipfad
     * @return die vollständige URL zum Origin-Server
     */
    private String originFileUrl(String path) {
        return originBaseUrl + "/api/origin/files/" + path;
    }

    /**
     * Erstellt HTTP-Header aus einem Cache-Eintrag.
     *
     * @param cached      der Cache-Eintrag
     * @param cacheStatus der Cache-Status (HIT oder MISS)
     * @return die HTTP-Header für die Response
     */
    private HttpHeaders headersFromCacheEntry(EdgeCacheService.CacheEntry cached, String cacheStatus) {
        HttpHeaders h = new HttpHeaders();

        String ct = cached.contentType();
        if (ct != null && !ct.isBlank()) {
            h.set(HttpHeaders.CONTENT_TYPE, ct);
        } else {
            h.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        }

        h.setContentLength(cached.body().length);
        h.set(SHA256_HEADER, cached.sha256());
        h.set(X_CACHE_HEADER, cacheStatus);

        return h;
    }

    /**
     * Leitet die Origin-Response durch und fügt den Cache-Status-Header hinzu.
     *
     * @param originResponse die Response vom Origin-Server
     * @param cacheStatus    der Cache-Status (HIT oder MISS)
     * @param <T>            der Typ der Response
     * @return die durchgeleitete Response mit Cache-Status-Header
     */
    private <T> ResponseEntity<T> passThrough(ResponseEntity<T> originResponse, String cacheStatus) {
        HttpHeaders out = new HttpHeaders();
        out.putAll(originResponse.getHeaders());
        out.set(X_CACHE_HEADER, cacheStatus);

        return ResponseEntity.status(originResponse.getStatusCode())
                .headers(out)
                .body(originResponse.getBody());
    }

    /**
     * Admin-Schnittstelle zur Cache-Invalidierung.
     */
    @RestController
    @RequestMapping("/api/edge/cache")
    public class CacheAdminApi {

        /**
         * Invalidiert eine spezifische Datei im Cache.
         * Beispiel: DELETE /api/edge/cache/files/video.mp4
         */
        @DeleteMapping("/files/{path:.+}")
        public ResponseEntity<Map<String, String>> invalidateFile(@PathVariable("path") String path) {
            boolean removed = edgeCacheService.remove(path);
            return ResponseEntity.ok(Map.of("path", path, "status", removed ? "invalidated" : "not in cache"));
        }

        /**
         * Invalidiert alle Dateien, die mit einem bestimmten Prefix beginnen.
         * Beispiel: DELETE /api/edge/cache/prefix?value=movies/
         */
        @DeleteMapping("/prefix")
        public ResponseEntity<Map<String, Object>> invalidateByPrefix(@RequestParam("value") String prefix) {
            int count = edgeCacheService.removeByPrefix(prefix);
            return ResponseEntity.ok(Map.of(
                    "prefix", prefix,
                    "invalidatedCount", count));
        }

        /**
         * Leert den gesamten Cache der Edge-Node.
         * Beispiel: DELETE /api/edge/cache/all
         */
        @DeleteMapping("/all")
        public ResponseEntity<Map<String, String>> clearAll() {
            edgeCacheService.clear();
            return ResponseEntity.ok(Map.of("status", "cache cleared"));
        }
    }
}
