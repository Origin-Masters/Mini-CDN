package de.htwsaar.minicdn.router.service;

import de.htwsaar.minicdn.router.domain.EdgeGateway;
import de.htwsaar.minicdn.router.domain.RouteFileResult;
import de.htwsaar.minicdn.router.domain.RouteStatus;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.util.UrlUtil;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Fachlicher Use Case zum Routen einer Datei an eine Edge-Node.
 *
 * <p>Enthält keine HTTP-Response-Building-Logik.</p>
 */
@Service
public class CdnRoutingService {

    private static final Logger log = LoggerFactory.getLogger(CdnRoutingService.class);
    private static final String EDGE_FILES_PREFIX = "api/edge/files/";
    private static final String ORIGIN_FILES_PREFIX = "api/origin/files/";

    private final RoutingIndex routingIndex;
    private final RouterStatsService routerStatsService;
    private final EdgeGateway edgeGateway;
    private final long ackTimeoutMs;
    private final int maxRetries;
    private final long retryIntervalMs;

    // Wir brauchen die Origin-URL für den Fallback (NFA-S3)
    private final String originBaseUrl;

    public CdnRoutingService(
            RoutingIndex routingIndex,
            RouterStatsService routerStatsService,
            EdgeGateway edgeGateway,
            @Value("${cdn.delivery.ack-timeout-ms:500}") long ackTimeoutMs,
            @Value("${cdn.delivery.max-retries:3}") int maxRetries,
            @Value("${cdn.delivery.retry-interval-ms:100}") long retryIntervalMs,
            @Value("${cdn.origin.base-url:http://localhost:8080}") String originBaseUrl) {

        this.routingIndex = routingIndex;
        this.routerStatsService = routerStatsService;
        this.edgeGateway = edgeGateway;
        this.ackTimeoutMs = ackTimeoutMs;
        this.maxRetries = maxRetries;
        this.retryIntervalMs = retryIntervalMs;
        this.originBaseUrl = originBaseUrl;
    }

    /**
     * Führt die Routing-Entscheidung für eine Datei aus.
     *
     * @param path Dateipfad
     * @param region Zielregion
     * @param clientId optionale Client-ID
     * @return fachliches Routing-Ergebnis
     */
    public RouteFileResult route(String path, String region, String clientId) {
        log.info("START: Empfange Routing-Anfrage für Datei: {} [Region: {}]", path, region);

        if (region == null || region.isBlank()) {
            routerStatsService.recordError();
            log.warn("Anfrage abgebrochen: Region fehlt.");
            return new RouteFileResult(
                    RouteStatus.BAD_REQUEST,
                    null,
                    null,
                    0,
                    "Fehler: Region fehlt. Bitte 'region' Query-Parameter oder 'X-Client-Region' Header setzen.");
        }

        routerStatsService.recordRequest(region, clientId);

        int nodeCount = routingIndex.getNodeCount(region);

        // NFA-S3: Zustellgarantie mit Origin-Fallback

        // Wir berechnen, wie oft wir versuchen Edges zu erreichen
        int maxAllowedAttempts = Math.min(maxRetries, Math.max(1, nodeCount));
        int attempts = 0;
        boolean erfolgreich = false;

        // Schritt 1: Versuche alle verfügbaren Edges durch (Retries/Duplikate)
        while (attempts < maxAllowedAttempts) {
            EdgeNode selectedNode = routingIndex.getNextNode(region);

            if (selectedNode != null) {
                // Prüfen ob Edge antwortet (Acknowledgement)
                boolean ack = edgeGateway.isNodeResponsive(selectedNode, Duration.ofMillis(ackTimeoutMs));

                if (ack) {
                    erfolgreich = true;
                    routerStatsService.recordDownload(path, selectedNode.url());

                    URI baseUri = URI.create(UrlUtil.ensureTrailingSlash(selectedNode.url()));
                    String relativePath = EDGE_FILES_PREFIX + UrlUtil.stripLeadingSlash(path);
                    URI location = baseUri.resolve(UrlUtil.stripLeadingSlash(relativePath));

                    log.info("[NFA-S3] Zustellgarantie durch Edge erfüllt: {}", selectedNode.url());

                    return new RouteFileResult(
                            RouteStatus.REDIRECT, location, UUID.randomUUID().toString(), attempts, null);
                } else {
                    log.warn("[NFA-S3] Kein ACK von Edge {}. Versuch {} fehlgeschlagen.", selectedNode.url(), attempts + 1);
                }
            }

            attempts++;
            // Kurze Pause vor dem nächsten Duplikat-Versuch (Consumer-Restart Zeit geben)
            if (attempts < maxAllowedAttempts && retryIntervalMs > 0) {
                sleepQuietly(retryIntervalMs);
            }
        }

        // Schritt 2: Wenn alle Edges tot sind -> Fallback auf den Origin (Ultimative Zustellgarantie)
        log.error("[NFA-S3] Keine Edge erreichbar nach {} Versuchen. Nutze Origin-Fallback!", attempts);

        try {
            URI originBase = URI.create(UrlUtil.ensureTrailingSlash(originBaseUrl));
            String originPath = ORIGIN_FILES_PREFIX + UrlUtil.stripLeadingSlash(path);
            URI originLocation = originBase.resolve(UrlUtil.stripLeadingSlash(originPath));

            return new RouteFileResult(
                    RouteStatus.REDIRECT,
                    originLocation,
                    UUID.randomUUID().toString(),
                    attempts,
                    "Zustellgarantie via Origin-Fallback erfüllt (keine Edges verfügbar)");

        } catch (Exception e) {
            routerStatsService.recordError();
            log.error("FINISH: Routing komplett fehlgeschlagen. Auch Origin nicht erreichbar.");

            return new RouteFileResult(
                    RouteStatus.UNAVAILABLE,
                    null,
                    null,
                    attempts,
                    "Fehler: Zustellgarantie konnte nicht erfüllt werden. Weder Edges noch Origin erreichbar.");
        }
    }

    /**
     * Wartet eine feste Zeit und stellt den Interrupt-Status wieder her.
     *
     * @param millis Wartezeit in Millisekunden
     */
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}