package de.htwsaar.minicdn.router.service;

import de.htwsaar.minicdn.router.domain.EdgeGateway;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service zur automatisierten Überwachung der Verfügbarkeit von Edge-Knoten.
 * Überprüft periodisch alle registrierten Knoten und entfernt diese bei Ausfall.
 * Story: TS-C2 : Fehlertoleranz / Failover
 */
@Service
public class EdgeHealthChecker {

    private static final Logger log = LoggerFactory.getLogger(EdgeHealthChecker.class);

    private final RoutingIndex routingIndex;
    private final EdgeGateway edgeGateway;
    private final long healthTimeoutMs;

    public EdgeHealthChecker(
            RoutingIndex routingIndex,
            EdgeGateway edgeGateway,
            @Value("${cdn.health-check.timeout-ms:1000}") long healthTimeoutMs) {
        this.routingIndex = routingIndex;
        this.edgeGateway = edgeGateway;
        this.healthTimeoutMs = healthTimeoutMs;
    }

    /**
     * Führt regelmäßig einen Health-Check für alle registrierten Edges durch.
     *
     * <p>Wir prüfen bewusst alle 5 Sekunden, damit ein Ausfall sicher innerhalb
     * von 10 Sekunden erkannt werden kann.</p>
     */
    @Scheduled(fixedDelayString = "${cdn.health-check.interval-ms:5000}")
    public void checkAllEdges() {
        List<String> regions = routingIndex.getAllRegions();

        for (String region : regions) {
            List<EdgeNode> nodes = routingIndex.getAllNodes(region);
            for (EdgeNode node : nodes) {
                boolean healthy = edgeGateway.isNodeResponsive(node, Duration.ofMillis(healthTimeoutMs));

                // Wenn ein Knoten nicht mehr antwortet, nehmen wir ihn aus dem Routing raus.
                // Dadurch werden zukünftige Requests nur noch an funktionierende Edges geleitet.
                if (!healthy) {
                    log.warn(
                            "[HEALTH] Edge {} in Region {} ist nicht erreichbar. Entferne sie aus dem Routing-Index.",
                            node.url(),
                            region);
                    routingIndex.removeEdge(region, node, true);
                }
            }
        }
    }
}
