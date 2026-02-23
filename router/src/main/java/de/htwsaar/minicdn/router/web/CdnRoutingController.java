package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.service.EdgeHttpClient;
import de.htwsaar.minicdn.router.service.MetricsService;
import de.htwsaar.minicdn.router.service.RouterStatsService;
import de.htwsaar.minicdn.router.service.RoutingIndex;
import de.htwsaar.minicdn.router.util.UrlUtil;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
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
 * <p>
 * Implementiert eine einfache Zustellgarantie über einen Ack-Check (Health-Check) gegen die Edge-Nodes:
 * Nur wenn ein Knoten innerhalb des Timeouts antwortet, wird ein Redirect (HTTP 307) zurückgegeben,
 * ansonsten nach Ausschöpfen der Versuche HTTP 503.
 * </p>
 */
@RestController
@RequestMapping("/api/cdn")
public class CdnRoutingController {

    private static final String EDGE_FILES_PREFIX = "api/edge/files/";
    private static final String HEADER_MESSAGE_ID = "X-CDN-Message-ID";
    private static final String HEADER_RETRY_COUNT = "X-CDN-Retry-Count";

    private final RoutingIndex routingIndex;
    private final MetricsService metricsService;
    private final RouterStatsService routerStatsService;
    private final EdgeHttpClient edgeHttpClient;

    private final long ackTimeoutMs;
    private final int maxRetries;
    private final long retryIntervalMs;

    /**
     * Erstellt den Controller mit allen Abhängigkeiten (Constructor Injection).
     *
     * @param routingIndex Routing-Index (Region -> Edge-Nodes)
     * @param metricsService Metriken für Requests/Errors/Selections
     * @param routerStatsService Router-Statistiken (z. B. aktive Clients)
     * @param edgeHttpClient Client für Edge-Health-Checks (Ack)
     * @param ackTimeoutMs Timeout für den Ack-/Health-Check in Millisekunden
     * @param maxRetries maximale Anzahl Versuche (alte Semantik: begrenzt zusätzlich durch Node-Count)
     * @param retryIntervalMs Pause zwischen Versuchen in Millisekunden
     */
    public CdnRoutingController(
            RoutingIndex routingIndex,
            MetricsService metricsService,
            RouterStatsService routerStatsService,
            EdgeHttpClient edgeHttpClient,
            @Value("${cdn.delivery.ack-timeout-ms:500}") long ackTimeoutMs,
            @Value("${cdn.delivery.max-retries:3}") int maxRetries,
            @Value("${cdn.delivery.retry-interval-ms:100}") long retryIntervalMs) {

        this.routingIndex = routingIndex;
        this.metricsService = metricsService;
        this.routerStatsService = routerStatsService;
        this.edgeHttpClient = edgeHttpClient;

        this.ackTimeoutMs = ackTimeoutMs;
        this.maxRetries = maxRetries;
        this.retryIntervalMs = retryIntervalMs;
    }

    /**
     * Routing-Logik: Wählt eine Edge-Node mittels Round-Robin aus der Region aus.
     * <p>
     * Alte Retry-Semantik: Anzahl Versuche = {@code min(maxRetries, nodeCount(region))}.
     * Bei ausbleibendem Ack wird nach Ausschöpfen der Versuche HTTP 503 zurückgegeben.
     * </p>
     *
     * @param path angefragter Dateipfad
     * @param regionQuery optionale Region als Query-Parameter
     * @param clientIdQuery optionale Client-ID als Query-Parameter
     * @param regionHeader optionale Region aus dem Header {@code X-Client-Region}
     * @param clientIdHeader optionale Client-ID aus dem Header {@code X-Client-Id}
     * @return Redirect auf die gewählte Edge-URL oder eine Fehlermeldung
     */
    @GetMapping("/files/{path:.+}")
    public ResponseEntity<?> routeToEdge(
            @PathVariable("path") String path,
            @RequestParam(value = "region", required = false) String regionQuery,
            @RequestParam(value = "clientId", required = false) String clientIdQuery,
            @RequestHeader(value = "X-Client-Region", required = false) String regionHeader,
            @RequestHeader(value = "X-Client-Id", required = false) String clientIdHeader) {

        // Alte Semantik: RegionQuery gewinnt, sonst Header (ohne „smartes“ Mergen).
        String region = (regionQuery != null && !regionQuery.isBlank()) ? regionQuery : regionHeader;
        String clientId = firstNonBlank(clientIdQuery, clientIdHeader);

        if (region == null || region.isBlank()) {
            metricsService.recordError();
            routerStatsService.recordError();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Fehler: Region fehlt. Bitte 'region' Query-Parameter oder 'X-Client-Region' Header setzen.");
        }

        metricsService.recordRequest(region);
        routerStatsService.recordRequest(region, clientId);

        int nodeCount = routingIndex.getNodeCount(region);
        if (nodeCount <= 0) {
            metricsService.recordError();
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
                metricsService.recordNodeSelection(selectedNode.url());
                routerStatsService.recordDownload(path, selectedNode.url());

                URI baseUri = URI.create(UrlUtil.ensureTrailingSlash(selectedNode.url()));
                String relativePath = EDGE_FILES_PREFIX + UrlUtil.stripLeadingSlash(path);
                URI location = baseUri.resolve(UrlUtil.stripLeadingSlash(relativePath));

                HttpHeaders headers = new HttpHeaders();
                headers.setLocation(location);
                headers.set(HEADER_MESSAGE_ID, UUID.randomUUID().toString());
                headers.set(HEADER_RETRY_COUNT, String.valueOf(attempts));

                return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                        .headers(headers)
                        .build();
            }

            attempts++;

            if (attempts < maxAllowedAttempts && retryIntervalMs > 0) {
                sleepQuietly(retryIntervalMs);
            }
        }

        metricsService.recordError();
        routerStatsService.recordError();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Fehler: Zustellgarantie konnte nicht erfüllt werden. Keine erreichbaren Knoten in Region '"
                        + region + "'.");
    }

    /**
     * Liefert den ersten nicht-leeren String aus bevorzugtem und alternativem Wert.
     *
     * @param preferred bevorzugter Wert
     * @param fallback alternativer Wert
     * @return bereinigter String oder {@code null}, wenn beide Werte leer sind
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

    /**
     * Schläft eine definierte Zeit, ohne {@link InterruptedException} nach außen zu werfen.
     * <p>
     * Bei Interrupt wird der Thread-Interrupt-Status wieder gesetzt.
     * </p>
     *
     * @param millis Dauer in Millisekunden
     */
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
