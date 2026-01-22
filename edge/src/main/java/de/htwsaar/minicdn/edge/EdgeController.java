package de.htwsaar.minicdn.edge;

import de.htwsaar.minicdn.common.util.Sha256Util;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

/**
 * Edge-Controller des Mini-CDN.
 * Proxy zum Origin-Server.
 */
@RestController
@RequestMapping("/api/edge")
@Profile("edge")
public class EdgeController {

    // TODO : Use Streams instead of Byte Arrays

    @Value("${origin.base-url}")
    private String originBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String SHA256_HEADER = "X-Content-SHA256";

    // GET
    @GetMapping("/files/{path:.+}")
    public ResponseEntity<byte[]> getFile(@PathVariable("path") String path) {
        String url = originBaseUrl + "/api/origin/files/" + path;

        ResponseEntity<byte[]> originResponse = restTemplate.getForEntity(url, byte[].class);

        // Wenn Origin z.B. 404 liefert, direkt durchreichen
        if (!originResponse.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(originResponse.getStatusCode())
                    .headers(originResponse.getHeaders())
                    .body(originResponse.getBody());
        }

        byte[] body = originResponse.getBody();
        if (body == null) {
            return ResponseEntity.status(502).body(null);
        }

        String expectedSha = originResponse.getHeaders().getFirst(SHA256_HEADER);
        if (expectedSha == null || expectedSha.isBlank()) {
            // Origin liefert keinen Hash -> Integrität kann nicht geprüft werden
            return ResponseEntity.status(502).body(null);
        }

        String actualSha = Sha256Util.sha256Hex(body);

        if (!expectedSha.equalsIgnoreCase(actualSha)) {
            // Hash mismatch = Daten beschädigt / manipuliert / Übertragungsfehler
            return ResponseEntity.status(502).body(null);
        }

        // OK: Response inklusive Header vom Origin weitergeben
        return ResponseEntity.status(originResponse.getStatusCode())
                .headers(originResponse.getHeaders())
                .body(body);
    }

    // HEAD
    @RequestMapping(value = "/files/{path:.+}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headFile(@PathVariable("path") String path) {
        String url = originBaseUrl + "/api/origin/files/" + path;

        ResponseEntity<Void> originResponse = restTemplate.exchange(url, HttpMethod.HEAD, HttpEntity.EMPTY, Void.class);

        return ResponseEntity.status(originResponse.getStatusCode())
                .headers(originResponse.getHeaders())
                .build();
    }

    // HEALTH
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    // READY
    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        return ResponseEntity.ok("ready");
    }
}
