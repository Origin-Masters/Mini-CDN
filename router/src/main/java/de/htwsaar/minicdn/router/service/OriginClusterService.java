package de.htwsaar.minicdn.router.service;

import de.htwsaar.minicdn.router.adapter.RouterOriginClusterStateStore;
import de.htwsaar.minicdn.router.domain.EdgeGateway;
import de.htwsaar.minicdn.router.domain.OriginAdminGateway;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.util.UrlUtil;
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
 * Verwaltet den aktiven Origin und registrierte Hot-Spares inkl. Failover.
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

    @PostConstruct
    void recoverOnStartup() {
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

    public String resolveActiveOrigin() {
        failoverIfActiveIsUnhealthy();
        return activeOrigin.get();
    }

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

    public synchronized void addSpare(String originBaseUrl) {
        String normalized = requireUrl(originBaseUrl);
        if (normalized.equals(activeOrigin.get()) || spareOrigins.contains(normalized)) {
            return;
        }
        spareOrigins.add(normalized);
        persist();
    }

    public synchronized boolean removeSpare(String originBaseUrl) {
        String normalized = requireUrl(originBaseUrl);
        boolean removed = spareOrigins.remove(normalized);
        if (removed) {
            persist();
        }
        return removed;
    }

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

    public List<String> spareOriginsSnapshot() {
        return List.copyOf(spareOrigins);
    }

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
        return UrlUtil.ensureTrailingSlash(clean);
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

    public record OriginClusterSnapshot(
            String activeOrigin, List<String> spareOrigins, List<OriginNodeHealth> health) {}

    public record OriginNodeHealth(String url, boolean active, boolean healthy) {}
}
