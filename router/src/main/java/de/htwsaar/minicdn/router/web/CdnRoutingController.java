package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.domain.RouteFileResult;
import de.htwsaar.minicdn.router.domain.RouteStatus;
import de.htwsaar.minicdn.router.service.CdnRoutingService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP-Adapter für Dateirouting.
 */
@RestController
@RequestMapping("/api/cdn")
public class CdnRoutingController {

    private static final String HEADER_MESSAGE_ID = "X-CDN-Message-ID";
    private static final String HEADER_RETRY_COUNT = "X-CDN-Retry-Count";

    private final CdnRoutingService routingService;

    public CdnRoutingController(CdnRoutingService routingService) {
        this.routingService = routingService;
    }

    /**
     * Routet eine Datei zu einer passenden Edge-Instanz und setzt Redirect-Header.
     *
     * @param path Dateipfad relativ zum Origin
     * @param regionQuery Region aus Query-Parametern
     * @param clientIdQuery Client-ID aus Query-Parametern
     * @param regionHeader Region aus Request-Headern
     * @param clientIdHeader Client-ID aus Request-Headern
     * @return Redirect-Response oder Fehlermeldung
     */
    @GetMapping("/files/{path:.+}")
    public ResponseEntity<?> routeToEdge(
            @PathVariable("path") String path,
            @RequestParam(value = "region", required = false) String regionQuery,
            @RequestParam(value = "clientId", required = false) String clientIdQuery,
            @RequestHeader(value = "X-Client-Region", required = false) String regionHeader,
            @RequestHeader(value = "X-Client-Id", required = false) String clientIdHeader) {

        String region = firstNonBlank(regionQuery, regionHeader);
        String clientId = firstNonBlank(clientIdQuery, clientIdHeader);

        RouteFileResult result = routingService.route(path, region, clientId);

        if (result.status() == RouteStatus.BAD_REQUEST) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result.errorMessage());
        }

        if (result.status() == RouteStatus.UNAVAILABLE) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result.errorMessage());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(result.location());
        headers.set(HEADER_MESSAGE_ID, result.messageId());
        headers.set(HEADER_RETRY_COUNT, String.valueOf(result.retryCount()));

        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                .headers(headers)
                .build();
    }

    /**
     * Liefert den ersten nicht-leeren Wert und trimmt ihn.
     *
     * @param preferred bevorzugter Wert
     * @param fallback Fallback-Wert
     * @return getrimmter Wert oder {@code null}
     */
    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }
}
