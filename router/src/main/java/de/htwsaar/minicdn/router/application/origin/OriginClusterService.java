package de.htwsaar.minicdn.router.application.origin;

import de.htwsaar.minicdn.common.util.UriUtils;
import de.htwsaar.minicdn.router.adapter.out.persistence.RouterOriginClusterStateStore;
import de.htwsaar.minicdn.router.application.routing.RoutingIndex;
import de.htwsaar.minicdn.router.domain.model.EdgeNode;
import de.htwsaar.minicdn.router.domain.port.EdgeGateway;
import de.htwsaar.minicdn.router.domain.port.OriginAdminGateway;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Orchestriert den aktiven Origin und registrierte Hot-Spares inklusive Failover.
 *
 * <p>Die fachliche Logik bleibt in diesem Service. Technische Kommunikation mit externen
 * Komponenten erfolgt ausschließlich über {@link OriginAdminGateway} und {@link EdgeGateway}.
 */
@Service
public class OriginClusterService {

    private static final Logger log = LoggerFactory.getLogger(OriginClusterService.class);

    private final RouterOriginClusterStateStore stateStore;
    private final OriginAdminGateway originAdminGateway;
    private final RoutingIndex routingIndex;
    private final EdgeGateway edgeGateway;
    private final String configuredPrimary;
    private final List<String> configuredSpares;
    private final Duration healthTimeout;
    private final Duration edgeOriginSyncTimeout;

    private final AtomicReference<String> activeOrigin = new AtomicReference<>();
    private final CopyOnWriteArrayList<String> spareOrigins = new CopyOnWriteArrayList<>();

    public OriginClusterService(
            RouterOriginClusterStateStore stateStore,
            OriginAdminGateway originAdminGateway,
            RoutingIndex routingIndex,
            EdgeGateway edgeGateway,
            @Value("${cdn.origin.base-url:http://localhost:8080}") String configuredPrimary,
            @Value("${cdn.origin.spares:}") String configuredSpares,
            @Value("${cdn.origin.health.timeout-ms:1000}") long healthTimeoutMs,
            @Value("${cdn.origin.edge-sync.timeout-ms:1500}") long edgeOriginSyncTimeoutMs) {

        this.stateStore = Objects.requireNonNull(stateStore, "stateStore");
        this.originAdminGateway = Objects.requireNonNull(originAdminGateway, "originAdminGateway");
        this.routingIndex = Objects.requireNonNull(routingIndex, "routingIndex");
        this.edgeGateway = Objects.requireNonNull(edgeGateway, "edgeGateway");
        this.configuredPrimary = normalizeUrl(configuredPrimary);
        this.configuredSpares = parseConfiguredSpares(configuredSpares);
        this.healthTimeout = Duration.ofMillis(Math.max(100, healthTimeoutMs));
        this.edgeOriginSyncTimeout = Duration.ofMillis(Math.max(100, edgeOriginSyncTimeoutMs));
    }

    /**
     * Stellt den Clusterzustand beim Start wieder her und synchronisiert anschließend Edges auf den
     * aktiven Origin.
     */
    @PostConstruct
    public void recoverOnStartup() {
        RouterOriginClusterStateStore.OriginClusterState restored = stateStore.load();

        String restoredActive = normalizeUrl(restored.activeOrigin());
        if (restoredActive != null) {
            activeOrigin.set(restoredActive);
        } else {
            activeOrigin.set(configuredPrimary);
        }

        Set<String> mergedSpares = new LinkedHashSet<>();
        for (String configuredSpare : configuredSpares) {
            if (!configuredSpare.equals(activeOrigin.get())) {
                mergedSpares.add(configuredSpare);
            }
        }
        for (String restoredSpare : restored.spareOrigins()) {
            String normalized = normalizeUrl(restoredSpare);
            if (normalized != null && !normalized.equals(activeOrigin.get())) {
                mergedSpares.add(normalized);
            }
        }

        spareOrigins.clear();
        spareOrigins.addAll(mergedSpares);
        persist();
        syncEdgesToActiveOrigin(activeOrigin.get());
    }

    /**
     * Liefert den aktuell aktiven Origin und führt bei Bedarf ein Failover durch.
     *
     * @return Basis-URL des aktiven Origins
     */
    public String resolveActiveOrigin() {
        failoverIfActiveIsUnhealthy();
        return activeOrigin.get();
    }

    /**
     * Erstellt eine Momentaufnahme des Origin-Clusters.
     *
     * @param includeHealth {@code true}, um zusätzlich Gesundheitsinformationen zu ermitteln
     * @return Snapshot mit aktivem Origin, Spares und optionalen Health-Daten
     */
    public synchronized OriginClusterSnapshot snapshot(boolean includeHealth) {
        String active = activeOrigin.get();
        List<String> spares = List.copyOf(spareOrigins);
        if (!includeHealth) {
            return new OriginClusterSnapshot(active, spares, List.of());
        }

        List<OriginNodeHealth> health = new ArrayList<>();
        if (active != null) {
            health.add(new OriginNodeHealth(active, true, isHealthy(active)));
        }
        for (String spare : spares) {
            health.add(new OriginNodeHealth(spare, false, isHealthy(spare)));
        }
        return new OriginClusterSnapshot(active, spares, List.copyOf(health));
    }

    /**
     * Registriert einen neuen Spare-Origin, sofern dieser noch nicht bekannt ist.
     *
     * @param originBaseUrl Basis-URL des Spare-Origins
     */
    public synchronized void addSpare(String originBaseUrl) {
        String normalized = requireUrl(originBaseUrl);
        if (normalized.equals(activeOrigin.get()) || spareOrigins.contains(normalized)) {
            return;
        }
        spareOrigins.add(normalized);
        persist();
    }

    /**
     * Entfernt einen Spare-Origin aus der Registrierung.
     *
     * @param originBaseUrl Basis-URL des zu entfernenden Spare-Origins
     * @return {@code true}, wenn ein Eintrag entfernt wurde
     */
    public synchronized boolean removeSpare(String originBaseUrl) {
        String normalized = requireUrl(originBaseUrl);
        boolean removed = spareOrigins.remove(normalized);
        if (removed) {
            persist();
        }
        return removed;
    }

    /**
     * Setzt einen registrierten Spare-Origin als aktiv und verschiebt den bisherigen aktiven Origin
     * in die Spare-Liste.
     *
     * @param originBaseUrl Basis-URL des zu aktivierenden Origins
     * @return {@code true}, wenn die Umschaltung durchgeführt wurde
     */
    public synchronized boolean promoteToActive(String originBaseUrl) {
        String normalized = requireUrl(originBaseUrl);
        String currentActive = activeOrigin.get();
        if (normalized.equals(currentActive)) {
            return false;
        }

        if (!spareOrigins.remove(normalized)) {
            return false;
        }

        if (currentActive != null && !currentActive.equals(normalized)) {
            spareOrigins.addIfAbsent(currentActive);
        }

        activeOrigin.set(normalized);
        persist();
        syncEdgesToActiveOrigin(normalized);
        return true;
    }

    /**
     * Prüft den aktiven Origin und schaltet bei Ungesundheit auf einen gesunden Spare-Origin um.
     *
     * @return {@code true}, wenn ein Failover erfolgt ist
     */
    public synchronized boolean failoverIfActiveIsUnhealthy() {
        String current = activeOrigin.get();
        if (current == null) {
            return false;
        }
        if (isHealthy(current)) {
            return false;
        }

        for (String candidate : List.copyOf(spareOrigins)) {
            if (isHealthy(candidate)) {
                spareOrigins.remove(candidate);
                spareOrigins.addIfAbsent(current);
                activeOrigin.set(candidate);
                persist();
                syncEdgesToActiveOrigin(candidate);
                return true;
            }
        }

        return false;
    }

    /**
     * Synchronisiert den aktuell aktiven Origin für eine einzelne Edge.
     *
     * <p>Die Methode trifft nur fachliche Entscheidungen; der Transport liegt im Gateway.
     *
     * @param node Ziel-Edge
     * @param region Region der Edge
     * @return {@code true}, wenn keine Aktion nötig war oder die Aktualisierung erfolgreich war
     */
    public boolean syncEdgeToActiveOrigin(EdgeNode node, String region) {
        if (node == null) {
            return false;
        }

        String currentActiveOrigin = resolveActiveOrigin();
        if (currentActiveOrigin == null || currentActiveOrigin.isBlank()) {
            return true;
        }

        if (!isHealthy(currentActiveOrigin)) {
            log.info(
                    "[ORIGIN-SYNC] Ueberspringe Sync fuer Edge {} in Region {}, da aktive Origin {} derzeit ungesund ist.",
                    node.url(),
                    region,
                    currentActiveOrigin);
            return true;
        }

        boolean updated = edgeGateway.updateOriginBaseUrl(node, currentActiveOrigin, edgeOriginSyncTimeout);
        if (!updated) {
            log.warn(
                    "[ORIGIN-SYNC] Konnte aktive Origin {} nicht an Edge {} in Region {} propagieren.",
                    currentActiveOrigin,
                    node.url(),
                    region);
        }
        return updated;
    }

    /**
     * Liefert eine unveränderliche Sicht auf die aktuell registrierten Spare-Origins.
     *
     * @return Liste der Spare-Origins
     */
    public List<String> spareOriginsSnapshot() {
        return List.copyOf(spareOrigins);
    }

    /**
     * Führt in festen Intervallen eine Gesundheitsprüfung des aktiven Origins aus.
     */
    @Scheduled(fixedDelayString = "${cdn.origin.health.interval-ms:5000}")
    public void periodicActiveOriginHealthCheck() {
        failoverIfActiveIsUnhealthy();
    }

    private boolean isHealthy(String baseUrl) {
        return originAdminGateway.isHealthy(baseUrl, healthTimeout);
    }

    private void persist() {
        stateStore.save(activeOrigin.get(), List.copyOf(spareOrigins));
    }

    private void syncEdgesToActiveOrigin(String newActiveOrigin) {
        if (newActiveOrigin == null || newActiveOrigin.isBlank()) {
            return;
        }

        if (!isHealthy(newActiveOrigin)) {
            log.warn(
                    "[ORIGIN-FAILOVER] Ueberspringe Edge-Sync, da aktive Origin {} derzeit ungesund ist.",
                    newActiveOrigin);
            return;
        }

        for (String region : routingIndex.getAllRegions()) {
            for (EdgeNode node : routingIndex.getAllNodes(region)) {
                boolean updated = edgeGateway.updateOriginBaseUrl(node, newActiveOrigin, edgeOriginSyncTimeout);
                if (!updated) {
                    log.warn(
                            "[ORIGIN-FAILOVER] Konnte Origin {} nicht an Edge {} in Region {} propagieren.",
                            newActiveOrigin,
                            node.url(),
                            region);
                }
            }
        }
    }

    private static String requireUrl(String originBaseUrl) {
        String normalized = normalizeUrl(originBaseUrl);
        if (normalized == null) {
            throw new IllegalArgumentException("origin url must not be blank");
        }
        return normalized;
    }

    private static String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        String clean = url.trim();
        if (clean.isBlank()) {
            return null;
        }
        return UriUtils.ensureTrailingSlash(clean);
    }

    private static List<String> parseConfiguredSpares(String configuredSpares) {
        if (configuredSpares == null || configuredSpares.isBlank()) {
            return List.of();
        }

        Set<String> uniques = new LinkedHashSet<>();
        for (String part : configuredSpares.split(",")) {
            String normalized = normalizeUrl(part);
            if (normalized != null) {
                uniques.add(normalized);
            }
        }
        return List.copyOf(uniques);
    }

    /**
     * Fachliche Sicht auf den Zustand des Origin-Clusters zu einem Zeitpunkt.
     *
     * @param activeOrigin aktuell aktiver Origin
     * @param spareOrigins registrierte Spare-Origins
     * @param health optionale Gesundheitsinformationen je Origin
     */
    public record OriginClusterSnapshot(
            String activeOrigin, List<String> spareOrigins, List<OriginNodeHealth> health) {}

    /**
     * Gesundheitsstatus eines einzelnen Origin-Knotens.
     *
     * @param url Basis-URL des Knotens
     * @param active {@code true}, wenn der Knoten aktuell aktiv ist
     * @param healthy {@code true}, wenn der Knoten als gesund bewertet wurde
     */
    public record OriginNodeHealth(String url, boolean active, boolean healthy) {}
}
