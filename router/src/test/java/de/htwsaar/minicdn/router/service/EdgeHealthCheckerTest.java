package de.htwsaar.minicdn.router.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.htwsaar.minicdn.router.adapter.RouterRoutingStateStore;
import de.htwsaar.minicdn.router.domain.EdgeGateway;
import de.htwsaar.minicdn.router.domain.EdgeNodeStats;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Unit-Tests für den automatischen Health-Checker.
 */
class EdgeHealthCheckerTest {

    @Test
    void shouldRemoveDeadEdgeFromRoutingIndex() throws Exception {
        Path tmp = Files.createTempFile("routing-state-health", ".properties");

        RouterRoutingStateStore store = new RouterRoutingStateStore(tmp.toString());
        RoutingIndex routingIndex = new RoutingIndex(store);

        routingIndex.addEdge("eu-west", new EdgeNode("http://localhost:8081"));
        routingIndex.addEdge("eu-west", new EdgeNode("http://localhost:8089"));

        EdgeGateway gateway = new FakeEdgeGateway(Map.of(
                "http://localhost:8081/", true,
                "http://localhost:8089/", false));

        EdgeHealthChecker checker = new EdgeHealthChecker(routingIndex, gateway, 1000);
        checker.checkAllEdges();

        assertEquals(1, routingIndex.getNodeCount("eu-west"));
        assertEquals(1, routingIndex.getHealthyNodeCount("eu-west"));

        List<EdgeNode> nextNodes = routingIndex.getNextNodes("eu-west", 1);
        assertEquals(1, nextNodes.size());
        assertEquals("http://localhost:8081/", nextNodes.get(0).url());
    }

    @Test
    void shouldKeepHealthyEdgeHealthy() throws Exception {
        Path tmp = Files.createTempFile("routing-state-health", ".properties");

        RouterRoutingStateStore store = new RouterRoutingStateStore(tmp.toString());
        RoutingIndex routingIndex = new RoutingIndex(store);

        routingIndex.addEdge("eu-west", new EdgeNode("http://localhost:8081"));

        EdgeGateway gateway = new FakeEdgeGateway(Map.of("http://localhost:8081/", true));

        EdgeHealthChecker checker = new EdgeHealthChecker(routingIndex, gateway, 1000);
        checker.checkAllEdges();

        assertEquals(1, routingIndex.getNodeCount("eu-west"));
        assertEquals(1, routingIndex.getHealthyNodeCount("eu-west"));

        List<EdgeNode> nextNodes = routingIndex.getNextNodes("eu-west", 1);
        assertEquals(1, nextNodes.size());
        assertEquals("http://localhost:8081/", nextNodes.get(0).url());
    }

    private static final class FakeEdgeGateway implements EdgeGateway {

        private final Map<String, Boolean> healthByUrl;

        private FakeEdgeGateway(Map<String, Boolean> healthByUrl) {
            this.healthByUrl = healthByUrl;
        }

        @Override
        public boolean isNodeResponsive(EdgeNode node, Duration timeout) {
            return healthByUrl.getOrDefault(node.url(), false);
        }

        @Override
        public CompletableFuture<Boolean> checkNodeHealth(EdgeNode node, Duration timeout) {
            return CompletableFuture.completedFuture(isNodeResponsive(node, timeout));
        }

        @Override
        public EdgeNodeStats fetchAdminStats(EdgeNode node, int windowSec, Duration timeout) {
            throw new UnsupportedOperationException("Nicht Teil dieses Tests");
        }

        @Override
        public CompletableFuture<Boolean> invalidateFile(EdgeNode node, String path) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Boolean> invalidatePrefix(EdgeNode node, String prefix) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Boolean> clearCache(EdgeNode node) {
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public boolean isReady(URI baseUrl, Duration timeout) {
            throw new UnsupportedOperationException("Nicht Teil dieses Tests");
        }
    }
}
