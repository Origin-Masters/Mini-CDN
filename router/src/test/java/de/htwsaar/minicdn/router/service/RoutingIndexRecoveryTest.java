package de.htwsaar.minicdn.router.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.htwsaar.minicdn.router.adapter.RouterRoutingStateStore;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for verifying the persistence and recovery behavior of {@link RoutingIndex}.
 *
 * <p>These tests ensure that routing state (edge nodes and their associated regions)
 * can be persisted to a backing store and correctly recovered upon a subsequent
 * instantiation of {@link RoutingIndex}, simulating a router restart scenario.
 */
class RoutingIndexRecoveryTest {

    /**
     * Verifies that a {@link RoutingIndex} correctly persists its state via
     * {@link RouterRoutingStateStore} and that a newly created {@link RoutingIndex}
     * can recover the persisted state on startup.
     *
     * <p>Test steps:
     * <ol>
     *   <li>Create a temporary file to act as the state store backing file.</li>
     *   <li>Instantiate a {@link RouterRoutingStateStore} backed by the temp file.</li>
     *   <li>Create a {@link RoutingIndex} and register an edge node for the {@code eu-west} region.</li>
     *   <li>Create a second {@link RoutingIndex} using the same store and invoke recovery.</li>
     *   <li>Assert that the recovered index contains exactly one node for {@code eu-west}.</li>
     *   <li>Assert that the recovered node is not null and has the expected URL.</li>
     * </ol>
     *
     * @throws Exception if an I/O error occurs during temp file creation or state persistence
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
        EdgeNode next = recovered.getNextNode("eu-west");
        assertNotNull(next);
        assertEquals("http://localhost:8081/", next.url());
    }
}
