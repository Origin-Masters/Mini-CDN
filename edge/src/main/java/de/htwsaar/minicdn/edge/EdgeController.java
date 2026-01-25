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
 *
 * <p>
 * Dieser Controller fungiert als Proxy zwischen Client und Origin-Server.
 * Dateien werden vom Origin geladen, deren Integrität mittels SHA-256 überprüft
 * und anschließend an den Client weitergeleitet.
 * </p>
 *
 * <p>
 * Zusätzlich stellt der Controller einfache Health- und Ready-Endpunkte bereit.
 * </p>
 *
 * <p>
 * Der Controller ist ausschließlich aktiv, wenn das Spring-Profil {@code edge}
 * gesetzt ist.
 * </p>
 */
@RestController
@RequestMapping("/api/edge")
@Profile("edge")
public class EdgeController {

    // TODO : Use Streams instead of Byte Arrays

    /**
     * Basis-URL des Origin-Servers.
     *
     * <p>
     * Wird über die Application-Konfiguration (Property {@code origin.base-url})
     * injiziert.
     * </p>
     */
    @Value("${origin.base-url}")
    private String originBaseUrl;

    /**
     * RestTemplate für HTTP-Anfragen an den Origin-Server.
     */
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Name des HTTP-Headers, der den erwarteten SHA-256 Hash der Datei enthält.
     */
    private static final String SHA256_HEADER = "X-Content-SHA256";

    /**
     * Liefert eine Datei über den Edge-Server aus.
     *
     * <p>
     * Die Anfrage wird an den Origin-Server weitergeleitet. Falls der Origin
     * keinen erfolgreichen Statuscode liefert (z.B. 404), wird dieser direkt
     * an den Client durchgereicht.
     * </p>
     *
     * <p>
     * Bei erfolgreicher Antwort wird der SHA-256 Hash der empfangenen Datei
     * mit dem vom Origin gelieferten Hash verglichen. Stimmen beide nicht
     * überein, wird {@code 502 Bad Gateway} zurückgegeben.
     * </p>
     *
     * @param path relativer Pfad der angeforderten Datei
     * @return HTTP-Response mit Dateiinhalt oder Fehlerstatus
     */
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

    /**
     * Führt eine HEAD-Anfrage auf dem Origin-Server aus und gibt Status und Header
     * unverändert an den Client weiter.
     *
     * <p>
     * Dieser Endpoint ermöglicht es, Metadaten einer Datei (z.B. Content-Length
     * oder Hash) abzufragen, ohne den eigentlichen Dateiinhalt zu übertragen.
     * </p>
     *
     * @param path relativer Pfad der Datei
     * @return HTTP-Response mit Headern und Statuscode
     */
    @RequestMapping(value = "/files/{path:.+}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headFile(@PathVariable("path") String path) {
        String url = originBaseUrl + "/api/origin/files/" + path;

        ResponseEntity<Void> originResponse = restTemplate.exchange(url, HttpMethod.HEAD, HttpEntity.EMPTY, Void.class);

        return ResponseEntity.status(originResponse.getStatusCode())
                .headers(originResponse.getHeaders())
                .build();
    }

    /**
     * Health-Endpunkt zur einfachen Prüfung, ob der Edge-Server läuft.
     *
     * @return String {@code "ok"}
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    /**
     * Ready-Endpunkt zur Signalisierung, dass der Edge-Server bereit ist,
     * Anfragen zu verarbeiten.
     *
     * @return String {@code "ready"}
     */
    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        return ResponseEntity.ok("ready");
    }
}
