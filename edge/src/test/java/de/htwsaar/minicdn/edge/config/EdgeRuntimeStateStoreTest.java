package de.htwsaar.minicdn.edge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.htwsaar.minicdn.edge.application.config.EdgeRuntimeConfig;
import de.htwsaar.minicdn.edge.infrastructure.cache.ReplacementStrategy;
import de.htwsaar.minicdn.edge.infrastructure.persistence.EdgeRuntimeStateStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EdgeRuntimeStateStore}.
 *
 * <p>Verifies that the store correctly persists and restores edge runtime
 * configuration and TTL policies to and from a properties file.
 */
class EdgeRuntimeStateStoreTest {

    /**
     * Verifies that an {@link EdgeRuntimeConfig} and a map of TTL policies can be
     * saved to a temporary properties file and subsequently loaded back with all
     * values intact (round-trip serialz).
     *
     * <p>Steps:
     * <ol>
     *   <li>Create a temporary directory and resolve a {@code state.properties} file path.</li>
     *   <li>Construct an {@link EdgeRuntimeStateStore} pointing at that file.</li>
     *   <li>Build an {@link EdgeRuntimeConfig} with a known zone ID, max size, origin TTL,
     *       and {@link ReplacementStrategy#LFU} strategy.</li>
     *   <li>Persist the config together with a single TTL policy entry ({@code "images/" -> 90_000}).</li>
     *   <li>Load the persisted state and assert that the restored config equals the original
     *       and that the TTL policy for {@code "images/"} is {@code 90_000}.</li>
     * </ol>
     *
     * @throws Exception if an I/O error occurs while creating the temporary directory
     *                   or reading/writing the properties file
     */
    @Test
    void shouldRoundTripConfigAndTtlPolicies() throws Exception {
        Path dir = Files.createTempDirectory("edge-runtime-state");
        Path file = dir.resolve("state.properties");

        EdgeRuntimeStateStore store = new EdgeRuntimeStateStore(file.toString());
        EdgeRuntimeConfig config =
                new EdgeRuntimeConfig("eu-west", 120_000, 150, ReplacementStrategy.LFU, "http://localhost:8080");

        store.save(config, Map.of("images/", 90_000L));

        EdgeRuntimeStateStore.RestoredState restored = store.load();
        assertNotNull(restored);
        assertEquals(config, restored.config());
        assertEquals(90_000L, restored.ttlPolicies().get("images/"));
    }
}
