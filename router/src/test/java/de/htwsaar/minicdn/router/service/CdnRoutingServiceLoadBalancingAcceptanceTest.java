package de.htwsaar.minicdn.router.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.htwsaar.minicdn.router.adapter.RouterRoutingStateStore;
import de.htwsaar.minicdn.router.domain.EdgeGateway;
import de.htwsaar.minicdn.router.domain.EdgeNodeStats;
import de.htwsaar.minicdn.router.domain.FileRouteLocationResolver;
import de.htwsaar.minicdn.router.domain.OriginAdminGateway;
import de.htwsaar.minicdn.router.domain.RouteFileResult;
import de.htwsaar.minicdn.router.domain.RouteStatus;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Akzeptanznaher Lastverteilungs-Test über den vollständigen Routing-Service.
 */
class CdnRoutingServiceLoadBalancingAcceptanceTest {

    /**
     * Prüft, dass 1000 Requests innerhalb eines 60-Sekunden-Fensters
     * über 10 registrierte Edges innerhalb der 10-%-Toleranz verteilt werden.
     */
    @Test
    void shouldMeetLoadBalancingAcceptanceCriteriaWithinOneMinuteWindow() throws Exception {
        Path tmp = Files.createTempFile("routing-state-lb", ".properties");
        RoutingIndex routingIndex = new RoutingIndex(new RouterRoutingStateStore(tmp.toString()));

        String region = "eu-west";
        for (int i = 0; i < 10; i++) {
            routingIndex.addEdge(region, new EdgeNode("http://localhost:90" + i));
        }

        MutableClock clock = new MutableClock(Instant.parse("2026-03-12T15:00:00Z"), ZoneId.of("UTC"));

        RouterStatsService statsService = new RouterStatsService(clock);
        EdgeGateway edgeGateway = new AlwaysHealthyEdgeGateway();
        FileRouteLocationResolver resolver = new TestFileRouteLocationResolver();
        OriginClusterService originClusterService = new OriginClusterService(
                new de.htwsaar.minicdn.router.adapter.RouterOriginClusterStateStore(tmp.toString() + ".origin"),
                new AlwaysHealthyOriginGateway(),
                routingIndex,
                edgeGateway,
                "http://origin",
                "",
                500,
                500);
        originClusterService.recoverOnStartup();

        CdnRoutingService routingService = new CdnRoutingService(
                routingIndex, statsService, edgeGateway, resolver, originClusterService, 100, 1, 0);

        RouterAdminService adminService =
                new RouterAdminService(routingIndex, statsService, edgeGateway, originClusterService);

        for (int i = 0; i < 1000; i++) {
            RouteFileResult result = routingService.route("/asset-" + (i % 5) + ".txt", region, "client-" + i);
            assertEquals(RouteStatus.REDIRECT, result.status());
            clock.advanceMillis(10);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = adminService.getStats(60, false);

        @SuppressWarnings("unchecked")
        Map<String, Object> loadBalancing = (Map<String, Object>) stats.get("loadBalancing");

        assertEquals(1000L, loadBalancing.get("requestsRoutedToEdgesInWindow"));
        assertEquals(true, loadBalancing.get("minimumSampleSizeReached"));
        assertEquals(true, loadBalancing.get("balancedWithinTolerance"));
        assertEquals(true, loadBalancing.get("acceptanceCriteriaMet"));

        @SuppressWarnings("unchecked")
        Map<String, Long> requestsByEdge = (Map<String, Long>) loadBalancing.get("requestsByEdgeInWindow");

        assertEquals(10, requestsByEdge.size());

        for (Long count : requestsByEdge.values()) {
            assertTrue(Math.abs(count - 100L) <= 10L, "Verteilung außerhalb der Toleranz: " + count);
        }
    }

    /**
     * Test-Uhr für deterministische Fenster-Tests.
     */
    private static final class MutableClock extends Clock {

        private Instant currentInstant;
        private final ZoneId zoneId;

        private MutableClock(Instant currentInstant, ZoneId zoneId) {
            this.currentInstant = currentInstant;
            this.zoneId = zoneId;
        }

        @Override
        public ZoneId getZone() {
            return zoneId;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(currentInstant, zone);
        }

        @Override
        public Instant instant() {
            return currentInstant;
        }

        private void advanceMillis(long millis) {
            currentInstant = currentInstant.plusMillis(millis);
        }
    }

    /**
     * Fake-Gateway mit immer gesunden Edges.
     */
    private static final class AlwaysHealthyEdgeGateway implements EdgeGateway {

        @Override
        public boolean isNodeResponsive(EdgeNode node, Duration timeout) {
            return true;
        }

        @Override
        public CompletableFuture<Boolean> checkNodeHealth(EdgeNode node, Duration timeout) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public EdgeNodeStats fetchAdminStats(EdgeNode node, int windowSec, Duration timeout) {
            return new EdgeNodeStats(0, 0, 0, Map.of());
        }

        @Override
        public CompletableFuture<Boolean> invalidateFile(EdgeNode node, String path) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> invalidatePrefix(EdgeNode node, String prefix) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Boolean> clearCache(EdgeNode node) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public boolean updateOriginBaseUrl(EdgeNode node, String originBaseUrl, Duration timeout) {
            return true;
        }

        @Override
        public boolean isReady(URI baseUrl, Duration timeout) {
            return true;
        }
    }

    /**
     * Einfacher Resolver für Testzwecke.
     */
    private static final class TestFileRouteLocationResolver implements FileRouteLocationResolver {

        @Override
        public URI resolveEdgeFileLocation(EdgeNode node, String path) {
            String cleanPath = path.startsWith("/") ? path.substring(1) : path;
            return URI.create(node.url() + "/api/edge/files/" + cleanPath);
        }

        @Override
        public URI resolveOriginFileLocation(String originBaseUrl, String path) {
            String cleanPath = path.startsWith("/") ? path.substring(1) : path;
            return URI.create(
                    (originBaseUrl == null ? "http://origin/" : originBaseUrl) + "api/origin/files/" + cleanPath);
        }
    }

    private static final class AlwaysHealthyOriginGateway implements OriginAdminGateway {

        @Override
        public de.htwsaar.minicdn.router.dto.AdminFileResult uploadFile(
                String originBaseUrl, String path, byte[] body) {
            return de.htwsaar.minicdn.router.dto.AdminFileResult.success(201, null);
        }

        @Override
        public de.htwsaar.minicdn.router.dto.AdminFileResult deleteFile(String originBaseUrl, String path) {
            return de.htwsaar.minicdn.router.dto.AdminFileResult.success(204, null);
        }

        @Override
        public de.htwsaar.minicdn.router.dto.AdminFileResult listFiles(String originBaseUrl, int page, int size) {
            return de.htwsaar.minicdn.router.dto.AdminFileResult.success(200, "[]");
        }

        @Override
        public de.htwsaar.minicdn.router.dto.AdminFileResult getFileMetadata(String originBaseUrl, String path) {
            return de.htwsaar.minicdn.router.dto.AdminFileResult.success(200, "{}");
        }

        @Override
        public boolean isHealthy(String originBaseUrl, Duration timeout) {
            return true;
        }
    }
}
