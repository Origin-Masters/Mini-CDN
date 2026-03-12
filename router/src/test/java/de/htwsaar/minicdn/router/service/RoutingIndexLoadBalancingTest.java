package de.htwsaar.minicdn.router.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.htwsaar.minicdn.router.adapter.RouterRoutingStateStore;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Prüft die Lastverteilung des {@link RoutingIndex} über mehrere Edge-Instanzen.
 */
class RoutingIndexLoadBalancingTest {

    /**
     * Stellt sicher, dass 1000 Requests über 10 Edges annähernd gleichverteilt werden.
     *
     * <p>Akzeptanzkriterium: Jede Edge darf maximal ±10% von der idealen Last (100 Requests)
     * abweichen.</p>
     */
    @Test
    void shouldDistributeThousandRequestsWithinTenPercentTolerance() throws Exception {
        Path tmp = Files.createTempFile("routing-state", ".properties");
        RoutingIndex routingIndex = new RoutingIndex(new RouterRoutingStateStore(tmp.toString()));

        String region = "eu-west";
        for (int i = 0; i < 10; i++) {
            routingIndex.addEdge(region, new EdgeNode("http://localhost:90" + i));
        }

        Map<String, Integer> distribution = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            EdgeNode node = routingIndex.getNextNode(region);
            distribution.merge(node.url(), 1, Integer::sum);
        }

        int idealPerNode = 100;
        int maxDeviation = 10;

        for (Integer count : distribution.values()) {
            assertTrue(
                    Math.abs(count - idealPerNode) <= maxDeviation,
                    () -> "Verteilung außerhalb der Toleranz. Erwartet: "
                            + idealPerNode
                            + " ±"
                            + maxDeviation
                            + ", tatsächlich: "
                            + count);
        }
    }
}
