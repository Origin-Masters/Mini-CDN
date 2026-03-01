package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.service.EdgeHttpClient;
import de.htwsaar.minicdn.router.service.RouterStatsService;
import de.htwsaar.minicdn.router.service.RoutingIndex;
import de.htwsaar.minicdn.router.util.UrlUtil;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger; // für das Logging
import org.slf4j.LoggerFactory; // für das Logging
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Routing-Controller: Delegiert Datei-Anfragen an Edge-Nodes.
 */
@RestController
@RequestMapping("/api/cdn")
public class CdnRoutingController {

    // Logger initialisieren (Neu für US-S1 / NFA-S1 Nachweis)
    private static final Logger log = LoggerFactory.getLogger(CdnRoutingController.class);

    private static final String EDGE_FILES_PREFIX = "api/edge/files/";
    private static final String HEADER_MESSAGE_ID = "X-CDN-Message-ID";
    private static final String HEADER_RETRY_COUNT = "X-CDN-Retry-Count";

    private final RoutingIndex routingIndex;
    private final RouterStatsService routerStatsService;
    private final EdgeHttpClient edgeHttpClient;

    private final long ackTimeoutMs;
    private final int maxRetries;
    private final long retryIntervalMs;

    public CdnRoutingController(
            RoutingIndex routingIndex,
            RouterStatsService routerStatsService,
            EdgeHttpClient edgeHttpClient,
            @Value("${cdn.delivery.ack-timeout-ms:500}") long ackTimeoutMs,
            @Value("${cdn.delivery.max-retries:3}") int maxRetries,
            @Value("${cdn.delivery.retry-interval-ms:100}") long retryIntervalMs) {

        this.routingIndex = routingIndex;
        this.routerStatsService = routerStatsService;
        this.edgeHttpClient = edgeHttpClient;

        this.ackTimeoutMs = ackTimeoutMs;
        this.maxRetries = maxRetries;
        this.retryIntervalMs = retryIntervalMs;
    }

    @GetMapping("/files/{path:.+}")
    public ResponseEntity<?> routeToEdge(
            @PathVariable("path") String path,
            @RequestParam(value = "region", required = false) String regionQuery,
            @RequestParam(value = "clientId", required = false) String clientIdQuery,
            @RequestHeader(value = "X-Client-Region", required = false) String regionHeader,
            @RequestHeader(value = "X-Client-Id", required = false) String clientIdHeader) {

        // Log-Eintrag beim Start der Anfrage (Nachweis der Parallelität)
        log.info(
                "START: Empfange Routing-Anfrage für Datei: {} [Region: {}]",
                path,
                regionQuery != null ? regionQuery : regionHeader);

        String region = (regionQuery != null && !regionQuery.isBlank()) ? regionQuery : regionHeader;
        String clientId = firstNonBlank(clientIdQuery, clientIdHeader);

        if (region == null || region.isBlank()) {
            routerStatsService.recordError();
            log.warn("Anfrage abgebrochen: Region fehlt.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Fehler: Region fehlt. Bitte 'region' Query-Parameter oder 'X-Client-Region' Header setzen.");
        }

        routerStatsService.recordRequest(region, clientId);

        int nodeCount = routingIndex.getNodeCount(region);
        if (nodeCount <= 0) {
            routerStatsService.recordError();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("Fehler: Zustellgarantie konnte nicht erfüllt werden. Keine erreichbaren Knoten in Region '"
                            + region + "'.");
        }

        int maxAllowedAttempts = Math.min(maxRetries, nodeCount);

        int attempts = 0;
        while (attempts < maxAllowedAttempts) {
            EdgeNode selectedNode = routingIndex.getNextNode(region);
            if (selectedNode == null) {
                break;
            }

            boolean ack = edgeHttpClient.isNodeResponsive(selectedNode, Duration.ofMillis(ackTimeoutMs));
            if (ack) {
                routerStatsService.recordDownload(path, selectedNode.url());

                URI baseUri = URI.create(UrlUtil.ensureTrailingSlash(selectedNode.url()));
                String relativePath = EDGE_FILES_PREFIX + UrlUtil.stripLeadingSlash(path);
                URI location = baseUri.resolve(UrlUtil.stripLeadingSlash(relativePath));

                HttpHeaders headers = new HttpHeaders();
                headers.setLocation(location);
                headers.set(HEADER_MESSAGE_ID, UUID.randomUUID().toString());
                headers.set(HEADER_RETRY_COUNT, String.valueOf(attempts));

                // Log-Eintrag vor dem erfolgreichen Redirect (Ende der Verarbeitung)
                log.info(
                        "FINISH: Routing-Entscheidung erfolgreich für Datei: {} -> Edge: {}", path, selectedNode.url());

                return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                        .headers(headers)
                        .build();
            }

            attempts++;

            if (attempts < maxAllowedAttempts && retryIntervalMs > 0) {
                sleepQuietly(retryIntervalMs);
            }
        }

        routerStatsService.recordError();

        // Log-Eintrag im Fehlerfall (Ende der Verarbeitung)
        log.error("FINISH: Routing fehlgeschlagen für Datei: {} - Keine Edge erreichbar.", path);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Fehler: Zustellgarantie konnte nicht erfüllt werden. Keine erreichbaren Knoten in Region '"
                        + region + "'.");
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return null;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
