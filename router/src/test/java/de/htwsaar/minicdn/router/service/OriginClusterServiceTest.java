package de.htwsaar.minicdn.router.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.htwsaar.minicdn.router.adapter.RouterOriginClusterStateStore;
import de.htwsaar.minicdn.router.adapter.RouterRoutingStateStore;
import de.htwsaar.minicdn.router.domain.EdgeGateway;
import de.htwsaar.minicdn.router.domain.EdgeNodeStats;
import de.htwsaar.minicdn.router.domain.OriginAdminGateway;
import de.htwsaar.minicdn.router.dto.AdminFileResult;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class OriginClusterServiceTest {

    @Test
    void shouldFailOverToHealthySpareWhenActiveOriginIsDown() throws Exception {
        Path stateFile = Files.createTempFile("origin-cluster-state", ".properties");

        FakeOriginAdminGateway gateway = new FakeOriginAdminGateway();
        gateway.health.put("http://origin-a/", false);
        gateway.health.put("http://origin-b/", true);
        RoutingIndex routingIndex = new RoutingIndex(new RouterRoutingStateStore(stateFile.toString() + ".routing"));
        EdgeGateway edgeGateway = new FakeEdgeGateway();

        OriginClusterService service = new OriginClusterService(
                new RouterOriginClusterStateStore(stateFile.toString()),
                gateway,
                routingIndex,
                edgeGateway,
                "http://origin-a",
                "http://origin-b",
                500,
                500);
        service.recoverOnStartup();

        boolean changed = service.failoverIfActiveIsUnhealthy();

        assertTrue(changed);
        assertEquals("http://origin-b/", service.resolveActiveOrigin());
        assertEquals(1, service.spareOriginsSnapshot().size());
        assertEquals("http://origin-a/", service.spareOriginsSnapshot().get(0));
    }

    @Test
    void shouldPromoteRegisteredSpareToActive() throws Exception {
        Path stateFile = Files.createTempFile("origin-cluster-state", ".properties");

        FakeOriginAdminGateway gateway = new FakeOriginAdminGateway();
        gateway.health.put("http://origin-a/", true);
        gateway.health.put("http://origin-b/", true);
        RoutingIndex routingIndex = new RoutingIndex(new RouterRoutingStateStore(stateFile.toString() + ".routing"));
        EdgeGateway edgeGateway = new FakeEdgeGateway();

        OriginClusterService service = new OriginClusterService(
                new RouterOriginClusterStateStore(stateFile.toString()),
                gateway,
                routingIndex,
                edgeGateway,
                "http://origin-a",
                "http://origin-b",
                500,
                500);
        service.recoverOnStartup();

        boolean promoted = service.promoteToActive("http://origin-b");

        assertTrue(promoted);
        assertEquals("http://origin-b/", service.resolveActiveOrigin());
        assertEquals("http://origin-a/", service.spareOriginsSnapshot().get(0));
    }

    private static final class FakeOriginAdminGateway implements OriginAdminGateway {

        private final Map<String, Boolean> health = new HashMap<>();

        @Override
        public AdminFileResult uploadFile(String originBaseUrl, String path, byte[] body) {
            return AdminFileResult.success(201, null);
        }

        @Override
        public AdminFileResult deleteFile(String originBaseUrl, String path) {
            return AdminFileResult.success(204, null);
        }

        @Override
        public AdminFileResult listFiles(String originBaseUrl, int page, int size) {
            return AdminFileResult.success(200, "[]");
        }

        @Override
        public AdminFileResult getFileMetadata(String originBaseUrl, String path) {
            return AdminFileResult.success(200, "{}");
        }

        @Override
        public boolean isHealthy(String originBaseUrl, Duration timeout) {
            return health.getOrDefault(originBaseUrl, false);
        }
    }

    private static final class FakeEdgeGateway implements EdgeGateway {

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
}
