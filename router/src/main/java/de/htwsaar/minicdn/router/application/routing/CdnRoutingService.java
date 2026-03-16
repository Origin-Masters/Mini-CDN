package de.htwsaar.minicdn.router.application.routing;

import de.htwsaar.minicdn.router.application.metrics.RouterStatsService;
import de.htwsaar.minicdn.router.domain.model.EdgeNode;
import de.htwsaar.minicdn.router.domain.model.RouteFileResult;
import de.htwsaar.minicdn.router.domain.model.RouteStatus;
import de.htwsaar.minicdn.router.domain.port.ActiveOriginResolver;
import de.htwsaar.minicdn.router.domain.port.EdgeGateway;
import de.htwsaar.minicdn.router.domain.port.EdgeRegistry;
import de.htwsaar.minicdn.router.domain.port.FileRouteLocationResolver;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Fachlicher Use Case für Datei-Routing.
 *
 * <p>Der Service arbeitet über fachliche Ports und stellt die Zustellgarantie
 * über Retries sowie Origin-Fallback sicher.</p>
 */
@Service
public class CdnRoutingService {

    private static final Logger log = LoggerFactory.getLogger(CdnRoutingService.class);

    private final EdgeRegistry edgeRegistry;
    private final RouterStatsService routerStatsService;
    private final EdgeGateway edgeGateway;
    private final FileRouteLocationResolver fileRouteLocationResolver;
    private final ActiveOriginResolver activeOriginResolver;
    private final long ackTimeoutMs;
    private final int maxRetries;
    private final long retryIntervalMs;

    /**
     * Erstellt den Routing-Service.
     *
     * @param edgeRegistry fachlicher Registry-Port für Edge-Knoten
     * @param routerStatsService Statistik-Service
     * @param edgeGateway Port zur Erreichbarkeitsprüfung von Edge-Knoten
     * @param fileRouteLocationResolver Port zur Ermittlung des Redirect-Ziels
     * @param ackTimeoutMs Timeout je Edge-ACK in Millisekunden
     * @param maxRetries maximale Anzahl zu prüfender Kandidaten
     * @param retryIntervalMs kurze Pause zwischen zwei Versuchen
     */
    public CdnRoutingService(
            EdgeRegistry edgeRegistry,
            RouterStatsService routerStatsService,
            EdgeGateway edgeGateway,
            FileRouteLocationResolver fileRouteLocationResolver,
            ActiveOriginResolver activeOriginResolver,
            @Value("${cdn.delivery.ack-timeout-ms:500}") long ackTimeoutMs,
            @Value("${cdn.delivery.max-retries:3}") int maxRetries,
            @Value("${cdn.delivery.retry-interval-ms:100}") long retryIntervalMs) {

        this.edgeRegistry = edgeRegistry;
        this.routerStatsService = routerStatsService;
        this.edgeGateway = edgeGateway;
        this.fileRouteLocationResolver = fileRouteLocationResolver;
        this.activeOriginResolver = activeOriginResolver;
        this.ackTimeoutMs = ackTimeoutMs;
        this.maxRetries = maxRetries;
        this.retryIntervalMs = retryIntervalMs;
    }

    /**
     * Führt die Routing-Entscheidung für eine Datei aus.
     *
     * @param path Dateipfad
     * @param region Zielregion
     * @param clientId optionale Client-ID
     * @return fachliches Ergebnis
     */
    public RouteFileResult route(String path, String region, String clientId) {
        return route(path, region, clientId, null);
    }

    /**
     * Führt die Routing-Entscheidung für eine Datei mit optionalem User-Kontext aus.
     *
     * @param path Dateipfad
     * @param region Zielregion
     * @param clientId optionale Client-ID
     * @param userId optionale technische User-ID
     * @return fachliches Ergebnis
     */
    public RouteFileResult route(String path, String region, String clientId, Long userId) {
        String cleanRegion = normalize(region);
        log.info("START routing file={} region={}", path, cleanRegion);
        if (cleanRegion == null) {
            routerStatsService.recordError();
            log.info(
                    "END routing file={} region={} status={} attempts={}",
                    path,
                    cleanRegion,
                    RouteStatus.BAD_REQUEST,
                    0);
            return new RouteFileResult(RouteStatus.BAD_REQUEST, null, null, 0, "Fehler: Region fehlt.");
        }

        routerStatsService.recordRequest(cleanRegion, clientId, userId);

        int attemptsLimit = resolveAttemptsLimit(edgeRegistry.getNodeCount(cleanRegion));
        List<EdgeNode> candidates = edgeRegistry.getNextNodes(cleanRegion, attemptsLimit);

        int attempts = 0;
        for (EdgeNode candidate : candidates) {
            boolean responsive = edgeGateway.isNodeResponsive(candidate, Duration.ofMillis(ackTimeoutMs));
            if (responsive) {
                URI location = fileRouteLocationResolver.resolveEdgeFileLocation(candidate, path);
                routerStatsService.recordDownload(path, candidate.url(), userId);
                log.info(
                        "END routing file={} region={} status={} attempts={} target={}",
                        path,
                        cleanRegion,
                        RouteStatus.REDIRECT,
                        attempts,
                        candidate.url());
                return new RouteFileResult(
                        RouteStatus.REDIRECT, location, UUID.randomUUID().toString(), attempts, null);
            }

            edgeRegistry.markUnhealthy(cleanRegion, candidate);
            attempts++;

            if (attempts < candidates.size() && retryIntervalMs > 0) {
                sleepQuietly(retryIntervalMs);
            }
        }

        return routeToOrigin(path, attempts);
    }

    /**
     * Liefert einen Redirect auf den Origin, wenn keine Edge erreichbar war.
     *
     * @param path Dateipfad
     * @param attempts Anzahl der fehlgeschlagenen Edge-Versuche
     * @return Redirect oder UNAVAILABLE bei Origin-Fehler
     */
    private RouteFileResult routeToOrigin(String path, int attempts) {
        try {
            String activeOrigin = activeOriginResolver.resolveActiveOrigin();
            if (activeOrigin == null || activeOrigin.isBlank()) {
                throw new IllegalStateException("No active origin configured");
            }
            URI originLocation = fileRouteLocationResolver.resolveOriginFileLocation(activeOrigin, path);
            log.info(
                    "END routing file={} region={} status={} attempts={} target={}",
                    path,
                    "origin-fallback",
                    RouteStatus.REDIRECT,
                    attempts,
                    activeOrigin);
            return new RouteFileResult(
                    RouteStatus.REDIRECT,
                    originLocation,
                    UUID.randomUUID().toString(),
                    attempts,
                    "Origin-Fallback aktiv, weil keine erreichbare Edge verfügbar war.");
        } catch (Exception ex) {
            routerStatsService.recordError();
            log.info(
                    "END routing file={} region={} status={} attempts={}",
                    path,
                    "origin-fallback",
                    RouteStatus.UNAVAILABLE,
                    attempts);
            return new RouteFileResult(
                    RouteStatus.UNAVAILABLE,
                    null,
                    null,
                    attempts,
                    "Fehler: Weder Edge noch Origin-Fallback verfügbar.");
        }
    }

    /**
     * Bestimmt die maximale Anzahl zu prüfender Kandidaten.
     *
     * @param nodeCount Anzahl registrierter Knoten
     * @return obere Grenze für Routing-Versuche
     */
    private int resolveAttemptsLimit(int nodeCount) {
        if (nodeCount <= 0) {
            return 0;
        }
        if (maxRetries <= 0) {
            return nodeCount;
        }
        return Math.min(maxRetries, nodeCount);
    }

    /**
     * Normalisiert String-Eingaben.
     *
     * @param value Eingabewert
     * @return getrimmter Wert oder {@code null}
     */
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        return clean.isBlank() ? null : clean;
    }

    /**
     * Wartet kurz und stellt den Interrupt-Status wieder her.
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
