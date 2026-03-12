package de.htwsaar.minicdn.router.service;

import de.htwsaar.minicdn.router.adapter.RouterRoutingStateStore;
import de.htwsaar.minicdn.router.domain.EdgeRegistry;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.util.UrlUtil;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * Thread-sichere In-Memory-Implementierung des fachlichen Edge-Registers.
 *
 * <p>Die Klasse hält alle registrierten Knoten pro Region, verwaltet deren
 * Health-Zustand und liefert Round-Robin-Kandidaten ohne Transportwissen.</p>
 */
@Service
public class RoutingIndex implements EdgeRegistry {

    private final RouterRoutingStateStore stateStore;
    private final Map<String, CopyOnWriteArrayList<RegisteredEdge>> regionToNodes = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> regionCounters = new ConcurrentHashMap<>();

    public RoutingIndex(RouterRoutingStateStore stateStore) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
    }

    /**
     * Lädt den persistierten Zustand beim Start.
     */
    @PostConstruct
    void recoverOnStartup() {
        stateStore.load().forEach((region, urls) -> {
            String cleanRegion = normalizeRegion(region);
            if (cleanRegion == null || urls == null) {
                return;
            }

            CopyOnWriteArrayList<RegisteredEdge> nodes =
                    regionToNodes.computeIfAbsent(cleanRegion, ignored -> new CopyOnWriteArrayList<>());

            for (String url : urls) {
                String cleanUrl = normalizeUrl(url);
                if (cleanUrl == null || containsUrl(nodes, cleanUrl)) {
                    continue;
                }
                nodes.add(new RegisteredEdge(new EdgeNode(cleanUrl), true));
            }

            if (!nodes.isEmpty()) {
                regionCounters.putIfAbsent(cleanRegion, new AtomicLong(0));
            }
        });
    }

    /**
     * {@inheritDoc}
     *
     * <p>Vorhandene Einträge werden nicht dupliziert; ein bereits registrierter Knoten
     * wird stattdessen wieder als gesund markiert.</p>
     */
    @Override
    public void addEdge(String region, EdgeNode node) {
        String cleanRegion = normalizeRegion(region);
        String cleanUrl = node == null ? null : normalizeUrl(node.url());
        if (cleanRegion == null || cleanUrl == null) {
            return;
        }

        CopyOnWriteArrayList<RegisteredEdge> nodes =
                regionToNodes.computeIfAbsent(cleanRegion, ignored -> new CopyOnWriteArrayList<>());

        if (containsUrl(nodes, cleanUrl)) {
            markHealthy(cleanRegion, new EdgeNode(cleanUrl));
            return;
        }

        nodes.add(new RegisteredEdge(new EdgeNode(cleanUrl), true));
        regionCounters.putIfAbsent(cleanRegion, new AtomicLong(0));
        persistState();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Nach erfolgreichem Entfernen wird der Round-Robin-Zaehler der Region
     * zurueckgesetzt; leere Regionen werden komplett entfernt.</p>
     */
    @Override
    public boolean removeEdge(String region, EdgeNode node, boolean persist) {
        String cleanRegion = normalizeRegion(region);
        String cleanUrl = node == null ? null : normalizeUrl(node.url());
        if (cleanRegion == null || cleanUrl == null) {
            return false;
        }

        CopyOnWriteArrayList<RegisteredEdge> nodes = regionToNodes.get(cleanRegion);
        if (nodes == null || nodes.isEmpty()) {
            return false;
        }

        boolean removed = nodes.removeIf(entry -> entry.node().url().equals(cleanUrl));
        if (!removed) {
            return false;
        }

        if (nodes.isEmpty()) {
            regionToNodes.remove(cleanRegion);
            regionCounters.remove(cleanRegion);
        } else {
            regionCounters
                    .computeIfAbsent(cleanRegion, ignored -> new AtomicLong(0))
                    .set(0);
        }

        if (persist) {
            persistState();
        }
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public void markHealthy(String region, EdgeNode node) {
        updateHealth(region, node, true);
    }

    /** {@inheritDoc} */
    @Override
    public void markUnhealthy(String region, EdgeNode node) {
        updateHealth(region, node, false);
    }

    /** {@inheritDoc} */
    @Override
    public int getNodeCount(String region) {
        String cleanRegion = normalizeRegion(region);
        if (cleanRegion == null) {
            return 0;
        }
        List<RegisteredEdge> nodes = regionToNodes.get(cleanRegion);
        return nodes == null ? 0 : nodes.size();
    }

    /** {@inheritDoc} */
    @Override
    public int getHealthyNodeCount(String region) {
        String cleanRegion = normalizeRegion(region);
        if (cleanRegion == null) {
            return 0;
        }
        List<RegisteredEdge> nodes = regionToNodes.get(cleanRegion);
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }

        int healthy = 0;
        for (RegisteredEdge node : nodes) {
            if (node.healthy()) {
                healthy++;
            }
        }
        return healthy;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Die Auswahl erfolgt nur aus gesunden Knoten und startet je Aufruf
     * am naechsten Round-Robin-Index der Region.</p>
     */
    @Override
    public List<EdgeNode> getNextNodes(String region, int maxCandidates) {
        String cleanRegion = normalizeRegion(region);
        if (cleanRegion == null || maxCandidates <= 0) {
            return List.of();
        }

        List<EdgeNode> healthyNodes = healthySnapshot(cleanRegion);
        if (healthyNodes.isEmpty()) {
            return List.of();
        }

        int size = healthyNodes.size();
        int limit = Math.min(maxCandidates, size);

        AtomicLong counter = regionCounters.computeIfAbsent(cleanRegion, ignored -> new AtomicLong(0));
        int startIndex = Math.floorMod(counter.getAndIncrement(), size);

        List<EdgeNode> candidates = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            candidates.add(healthyNodes.get((startIndex + i) % size));
        }

        return List.copyOf(candidates);
    }

    /** {@inheritDoc} */
    @Override
    public List<EdgeNode> getAllNodes(String region) {
        String cleanRegion = normalizeRegion(region);
        if (cleanRegion == null) {
            return List.of();
        }

        List<RegisteredEdge> nodes = regionToNodes.get(cleanRegion);
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }

        List<EdgeNode> result = new ArrayList<>(nodes.size());
        for (RegisteredEdge node : nodes) {
            result.add(node.node());
        }
        return List.copyOf(result);
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getAllRegions() {
        return regionToNodes.keySet().stream().sorted().toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Die Rueckgabe ist eine sortierte, unveraenderliche Momentaufnahme.</p>
     */
    @Override
    public Map<String, List<EdgeNode>> getRawIndex() {
        Map<String, List<EdgeNode>> snapshot = new TreeMap<>();
        regionToNodes.forEach((region, nodes) -> {
            List<EdgeNode> mapped = new ArrayList<>(nodes.size());
            for (RegisteredEdge node : nodes) {
                mapped.add(node.node());
            }
            snapshot.put(region, List.copyOf(mapped));
        });
        return Map.copyOf(snapshot);
    }

    /**
     * Aktualisiert den Health-Status eines vorhandenen Knotens in einer Region.
     */
    private void updateHealth(String region, EdgeNode node, boolean healthy) {
        String cleanRegion = normalizeRegion(region);
        String cleanUrl = node == null ? null : normalizeUrl(node.url());
        if (cleanRegion == null || cleanUrl == null) {
            return;
        }

        List<RegisteredEdge> nodes = regionToNodes.get(cleanRegion);
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        for (RegisteredEdge entry : nodes) {
            if (entry.node().url().equals(cleanUrl)) {
                entry.setHealthy(healthy);
                return;
            }
        }
    }

    /**
     * Erzeugt eine unveraenderliche Momentaufnahme aller gesunden Knoten einer Region.
     */
    private List<EdgeNode> healthySnapshot(String region) {
        List<RegisteredEdge> nodes = regionToNodes.get(region);
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }

        List<EdgeNode> healthy = new ArrayList<>(nodes.size());
        for (RegisteredEdge node : nodes) {
            if (node.healthy()) {
                healthy.add(node.node());
            }
        }
        return List.copyOf(healthy);
    }

    /**
     * Persistiert den aktuellen Index als Region-zu-URL-Abbildung.
     */
    private void persistState() {
        Map<String, List<String>> state = new TreeMap<>();
        regionToNodes.forEach((region, nodes) -> {
            List<String> urls = new ArrayList<>(nodes.size());
            for (RegisteredEdge node : nodes) {
                urls.add(node.node().url());
            }
            if (!urls.isEmpty()) {
                state.put(region, List.copyOf(urls));
            }
        });
        stateStore.save(state);
    }

    /**
     * Prueft, ob die URL in der gegebenen Knotenliste bereits registriert ist.
     */
    private static boolean containsUrl(List<RegisteredEdge> nodes, String url) {
        for (RegisteredEdge node : nodes) {
            if (node.node().url().equals(url)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Normalisiert Regionenamen durch Trimmen; leere Werte werden als {@code null} behandelt.
     */
    private static String normalizeRegion(String region) {
        if (region == null) {
            return null;
        }
        String clean = region.trim();
        return clean.isBlank() ? null : clean;
    }

    /**
     * Normalisiert URL-Werte durch Trimmen und erzwungenen trailing slash.
     */
    private static String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        String clean = UrlUtil.ensureTrailingSlash(url.trim());
        return clean.isBlank() ? null : clean;
    }

    /**
     * Interner Eintrag eines registrierten Edge-Knotens.
     */
    private static final class RegisteredEdge {
        private final EdgeNode node;
        private volatile boolean healthy;

        private RegisteredEdge(EdgeNode node, boolean healthy) {
            this.node = node;
            this.healthy = healthy;
        }

        public EdgeNode node() {
            return node;
        }

        public boolean healthy() {
            return healthy;
        }

        public void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }
    }
}
