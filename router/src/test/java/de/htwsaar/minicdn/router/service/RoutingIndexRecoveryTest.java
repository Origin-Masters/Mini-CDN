package de.htwsaar.minicdn.router.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import de.htwsaar.minicdn.router.adapter.RouterRoutingStateStore;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit-Tests für Persistenz und Recovery des {@link RoutingIndex}.
 */
class RoutingIndexRecoveryTest {

    /**
     * Prüft, dass persistierter Routing-Zustand nach einem Neustart wieder geladen wird.
     */
    @Test
    void shouldPersistAndRecoverRoutingState() throws Exception {
        Path tmp = Files.createTempFile("routing-state", ".properties");
        RouterRoutingStateStore store = new RouterRoutingStateStore(tmp.toString());

        RoutingIndex first = new RoutingIndex(store);
        first.addEdge("eu-west", new EdgeNode("http://localhost:8081"));

        RoutingIndex recovered = new RoutingIndex(store);
        recovered.recoverOnStartup();

        assertEquals(1, recovered.getNodeCount("eu-west"));

        List<EdgeNode> nextNodes = recovered.getNextNodes("eu-west", 1);
        assertFalse(nextNodes.isEmpty());
        assertEquals("http://localhost:8081/", nextNodes.get(0).url());
    }
}
