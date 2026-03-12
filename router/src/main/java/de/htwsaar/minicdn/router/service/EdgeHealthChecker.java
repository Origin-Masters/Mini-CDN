package de.htwsaar.minicdn.router.service;

import de.htwsaar.minicdn.router.domain.EdgeGateway;
import de.htwsaar.minicdn.router.domain.EdgeRegistry;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service zur periodischen Gesundheitsprüfung registrierter Edge-Knoten.
 *
 * <p>Nicht erreichbare Knoten werden aus dem Routing entfernt, damit
 * zukünftige Requests nur noch an funktionierende Edges gehen.</p>
 */
@Service
public class EdgeHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(EdgeHealthChecker.class);

    private final EdgeRegistry edgeRegistry;
    private final EdgeGateway edgeGateway;
    private final long healthTimeoutMs;

    /**
     * Erstellt den Health-Checker.
     *
     * @param edgeRegistry fachlicher Routing-Zustand
     * @param edgeGateway Port zur Erreichbarkeitsprüfung von Edge-Knoten
     * @param healthTimeoutMs Timeout je Health-Check in Millisekunden
     */
    public EdgeHealthChecker(
            EdgeRegistry edgeRegistry,
            EdgeGateway edgeGateway,
            @Value("${cdn.health-check.timeout-ms:1000}") long healthTimeoutMs) {
        this.edgeRegistry = edgeRegistry;
        this.edgeGateway = edgeGateway;
        this.healthTimeoutMs = healthTimeoutMs;
    }

    /**
     * Führt regelmäßig Health-Checks für alle registrierten Edge-Knoten aus.
     */
    @Scheduled(fixedDelayString = "${cdn.health-check.interval-ms:5000}")
    public void checkAllEdges() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (String region : edgeRegistry.getAllRegions()) {
            for (EdgeNode node : edgeRegistry.getAllNodes(region)) {
                CompletableFuture<Void> future = edgeGateway
                        .checkNodeHealth(node, Duration.ofMillis(healthTimeoutMs))
                        .exceptionally(ex -> {
                            log.warn(
                                    "[HEALTH] Health-Check für Edge {} in Region {} schlug fehl: {}",
                                    node.url(),
                                    region,
                                    ex.getMessage());
                            return false;
                        })
                        .thenAccept(healthy -> updateNodeHealth(region, node, healthy));

                futures.add(future);
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * Aktualisiert den Zustand eines Knotens im Routing.
     *
     * @param region Region des Knotens
     * @param node Edge-Knoten
     * @param healthy true, wenn der Knoten erreichbar ist
     */
    private void updateNodeHealth(String region, EdgeNode node, boolean healthy) {
        if (healthy) {
            edgeRegistry.markHealthy(region, node);
            log.debug("[HEALTH] Edge {} in Region {} ist gesund.", node.url(), region);
            return;
        }

        edgeRegistry.removeEdge(region, node, true);
        log.warn(
                "[HEALTH] Edge {} in Region {} ist nicht erreichbar und wurde aus dem Routing entfernt.",
                node.url(),
                region);
    }
}
