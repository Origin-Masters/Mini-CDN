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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Einfache Unit-Tests für den automatischen Health-Checker.
 *
 * <p>Die Tests pruefen nur die Kernidee der Story:
 * tote Replikate muessen erkannt und aus dem Routing entfernt werden.
 *
 * <p>Wichtig: Diese Testklasse startet keine echten Edge- oder Router-Server.
 * Stattdessen wird mit einem kleinen Fake simuliert, welche Edge erreichbar ist
 * und welche nicht. So testen wir gezielt nur die Logik des Health-Checkers.</p>
 */
class EdgeHealthCheckerTest {

    @Test
    void shouldRemoveDeadEdgeFromRoutingIndex() throws Exception {
        Path tmp = Files.createTempFile("routing-state-health", ".properties");

        RouterRoutingStateStore store = new RouterRoutingStateStore(tmp.toString());
        RoutingIndex routingIndex = new RoutingIndex(store);

        // Setup: Zwei Edges fuer dieselbe Region.
        // 8081 soll spaeter als gesund gelten, 8089 als ausgefallen.
        routingIndex.addEdge("eu-west", new EdgeNode("http://localhost:8081"));
        routingIndex.addEdge("eu-west", new EdgeNode("http://localhost:8089"));

        // Der Fake gibt fuer jede URL zurueck, ob sie auf einen Health-Check
        // antworten wuerde. So koennen wir den Fehlerfall gezielt nachbauen.
        EdgeGateway gateway = new FakeEdgeGateway(Map.of(
                "http://localhost:8081/", true,
                "http://localhost:8089/", false));

        // Der eigentliche Testschritt: Health-Check einmal ausfuehren.
        EdgeHealthChecker checker = new EdgeHealthChecker(routingIndex, gateway, 1000);
        checker.checkAllEdges();

        // Erwartung:
        // Die tote Edge wurde aus dem Routing entfernt.
        // Die gesunde Edge bleibt weiterhin registriert.
        assertEquals(1, routingIndex.getNodeCount("eu-west"));
        assertEquals(
                "http://localhost:8081/", routingIndex.getNextNode("eu-west").url());
    }

    @Test
    void shouldKeepHealthyEdgeInRoutingIndex() throws Exception {
        Path tmp = Files.createTempFile("routing-state-health", ".properties");

        RouterRoutingStateStore store = new RouterRoutingStateStore(tmp.toString());
        RoutingIndex routingIndex = new RoutingIndex(store);

        // Setup: In diesem Test gibt es nur eine gesunde Edge.
        routingIndex.addEdge("eu-west", new EdgeNode("http://localhost:8081"));

        EdgeGateway gateway = new FakeEdgeGateway(Map.of("http://localhost:8081/", true));

        // Auch nach dem Health-Check muss die Edge weiterhin im Index stehen,
        // weil sie erreichbar ist und daher nicht entfernt werden darf.
        EdgeHealthChecker checker = new EdgeHealthChecker(routingIndex, gateway, 1000);
        checker.checkAllEdges();

        assertEquals(1, routingIndex.getNodeCount("eu-west"));
    }

    /**
     * Sehr einfache Test-Implementierung des Ports.
     *
     * <p>Fuer diese Tests reicht es, wenn wir nur das Antwortverhalten pro URL simulieren.
     * Dadurch muessen wir keine echten HTTP-Requests absetzen und die Tests
     * bleiben klein, schnell und gut lesbar.</p>
     */
    private static final class FakeEdgeGateway implements EdgeGateway {

        private final Map<String, Boolean> healthByUrl;

        private FakeEdgeGateway(Map<String, Boolean> healthByUrl) {
            this.healthByUrl = healthByUrl;
        }

        @Override
        public boolean isNodeResponsive(EdgeNode node, Duration timeout) {
            // Wenn eine URL nicht in der Map steht, behandeln wir sie sicherheitshalber
            // als nicht erreichbar.
            return healthByUrl.getOrDefault(node.url(), false);
        }

        @Override
        public CompletableFuture<Boolean> checkNodeHealth(EdgeNode node, Duration timeout) {
            // Fuer den Test reicht ein sofort abgeschlossenes Future mit demselben Ergebnis.
            return CompletableFuture.completedFuture(isNodeResponsive(node, timeout));
        }

        @Override
        public EdgeNodeStats fetchAdminStats(EdgeNode node, int windowSec, Duration timeout) {
            throw new UnsupportedOperationException("Nicht Teil dieses Tests");
        }

        @Override
        public CompletableFuture<Boolean> invalidateFile(EdgeNode node, String path) {
            return null;
        }

        @Override
        public CompletableFuture<Boolean> invalidatePrefix(EdgeNode node, String prefix) {
            return null;
        }

        @Override
        public CompletableFuture<Boolean> clearCache(EdgeNode node) {
            return null;
        }

        @Override
        public CompletableFuture<Integer> sendDelete(EdgeNode node, String endpoint) {
            throw new UnsupportedOperationException("Nicht Teil dieses Tests");
        }

        @Override
        public boolean isReady(URI baseUrl, Duration timeout) {
            throw new UnsupportedOperationException("Nicht Teil dieses Tests");
        }
    }
}
