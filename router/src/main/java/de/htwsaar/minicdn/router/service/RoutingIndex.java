package de.htwsaar.minicdn.router.service;

import de.htwsaar.minicdn.router.adapter.RouterRoutingStateStore;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.util.UrlUtil;
import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * In-Memory-Index für Edge-Knoten je Region inklusive Round-Robin-Zählern.
 */
@Service
public class RoutingIndex {

    /**
     * Persistenter Speicher für den Routing-Zustand.
     */
    private final RouterRoutingStateStore stateStore;
    /**
     * Registrierte Edge-Knoten pro Region.
     */
    private final Map<String, List<EdgeNode>> regionToNodes = new ConcurrentHashMap<>();
    /**
     * Round-Robin-Zähler pro Region.
     */
    private final Map<String, AtomicLong> regionCounters = new ConcurrentHashMap<>();

    /**
     * Erstellt einen RoutingIndex mit dem erforderlichen State-Store.
     *
     * @param stateStore Speicher für persistente Sicherungen des Index
     */
    public RoutingIndex(RouterRoutingStateStore stateStore) {
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
    }

    /**
     * Lädt beim Start den gespeicherten Zustand und baut den in-Memo index auf.
     */
    @PostConstruct
    void recoverOnStartup() {
        Map<String, List<String>> recovered = stateStore.load();
        recovered.forEach((region, urls) -> {
            if (region == null || region.isBlank() || urls == null || urls.isEmpty()) {
                return;
            }
            List<EdgeNode> nodes = new CopyOnWriteArrayList<>();
            for (String url : urls) {
                if (url == null || url.isBlank()) continue;
                nodes.add(new EdgeNode(UrlUtil.ensureTrailingSlash(url.trim())));
            }
            if (!nodes.isEmpty()) {
                regionToNodes.put(region.trim(), nodes);
                regionCounters.put(region.trim(), new AtomicLong(0));
            }
        });
    }

    /**
     * Fügt einen Knoten für eine Region hinzu, sofern er noch nicht existiert.
     *
     * @param region Zielregion
     * @param node   hinzuzufügender Knoten
     */
    public void addEdge(String region, EdgeNode node) {
        if (region == null || node == null) {
            return;
        }

        String cleanRegion = region.trim();
        if (cleanRegion.isBlank() || node.url() == null || node.url().isBlank()) {
            return;
        }
        List<EdgeNode> nodes = regionToNodes.computeIfAbsent(cleanRegion, ignored -> new CopyOnWriteArrayList<>());
        EdgeNode cleanNode = new EdgeNode(UrlUtil.ensureTrailingSlash(node.url()));
        if (!nodes.contains(cleanNode)) {
            nodes.add(cleanNode);
            persistState();
        }

        regionCounters.putIfAbsent(cleanRegion, new AtomicLong(0));
    }

    /**
     * Wählt den nächsten Knoten einer Region via Round-Robin aus.
     *
     * @param region Zielregion
     * @return nächster Knoten oder {@code null}, falls keiner vorhanden ist
     */
    public EdgeNode getNextNode(String region) {
        if (region == null) {
            return null;
        }
        String cleanRegion = region.trim();
        if (cleanRegion.isBlank()) {
            return null;
        }
        List<EdgeNode> nodes = regionToNodes.get(cleanRegion);
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        AtomicLong counter = regionCounters.computeIfAbsent(cleanRegion, ignored -> new AtomicLong(0));
        int index = Math.floorMod(counter.getAndIncrement(), nodes.size());
        return nodes.get(index);
    }

    /**
     * Liefert bis zu {@code maxCandidates} eindeutige Knoten einer Region in Round-Robin-Reihenfolge.
     *
     * <p>Die Methode eignet sich für Retry-Szenarien, in denen pro Anfrage keine unnötigen
     * Duplikate ausgewählt werden sollen.</p>
     *
     * @param region Zielregion
     * @param maxCandidates maximale Anzahl zurückzugebender Kandidaten
     * @return unveränderliche Kandidatenliste
     */
    public List<EdgeNode> getNextNodes(String region, int maxCandidates) {
        if (region == null || maxCandidates <= 0) {
            return List.of();
        }

        String cleanRegion = region.trim();
        if (cleanRegion.isBlank()) {
            return List.of();
        }

        List<EdgeNode> nodes = regionToNodes.get(cleanRegion);
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }

        int limit = Math.min(maxCandidates, nodes.size());
        AtomicLong counter = regionCounters.computeIfAbsent(cleanRegion, ignored -> new AtomicLong(0));
        int start = Math.floorMod(counter.getAndAdd(limit), nodes.size());

        List<EdgeNode> candidates = new java.util.ArrayList<>(limit);
        for (int offset = 0; offset < limit; offset++) {
            int index = (start + offset) % nodes.size();
            candidates.add(nodes.get(index));
        }

        return List.copyOf(candidates);
    }

    /**
     * Entfernt einen Knoten aus einer Region.
     *
     * @param region        Zielregion
     * @param node          zu entfernender Knoten
     * @param removeIfEmpty entfernt die Region vollständig, wenn sie leer ist
     * @return {@code true}, wenn der Knoten entfernt wurde
     */
    public boolean removeEdge(String region, EdgeNode node, boolean removeIfEmpty) {
        if (region == null || node == null || node.url() == null || node.url().isBlank()) {
            return false;
        }
        String cleanRegion = region.trim();
        List<EdgeNode> nodes = regionToNodes.get(cleanRegion);
        if (nodes == null) {
            return false;
        }

        EdgeNode cleanNode = new EdgeNode(UrlUtil.ensureTrailingSlash(node.url()));
        boolean removed = nodes.remove(cleanNode);

        if (removed && nodes.isEmpty() && removeIfEmpty) {
            regionToNodes.remove(cleanRegion);
            regionCounters.remove(cleanRegion);
        }
        if (removed) {
            persistState();
        }
        return removed;
    }

    /**
     * Gibt den unveränderlichen Blick auf den aktuellen Routingindex zurück.
     *
     * @return Regionen mit ihren registrierten Knoten
     */
    public Map<String, List<EdgeNode>> getRawIndex() {
        return Collections.unmodifiableMap(regionToNodes);
    }

    /**
     * Löscht den kompletten Routingindex inklusive Round-Robin-Zählern.
     */
    public void clear() {
        regionToNodes.clear();
        regionCounters.clear();
        persistState();
    }

    /**
     * Liefert die Anzahl Knoten in einer Region.
     *
     * @param region Zielregion
     * @return Anzahl Knoten (0, wenn Region unbekannt ist)
     */
    public int getNodeCount(String region) {
        if (region == null) {
            return 0;
        }
        String cleanRegion = region.trim();
        if (cleanRegion.isBlank()) {
            return 0;
        }
        List<EdgeNode> nodes = regionToNodes.get(cleanRegion);
        return nodes != null ? nodes.size() : 0;
    }

    /**
     * Persistiert den aktuellen in-memo Zustand im State-Store.
     */
    private void persistState() {
        Map<String, List<String>> snapshot = new ConcurrentHashMap<>();
        regionToNodes.forEach((region, nodes) -> snapshot.put(
                region, nodes.stream().map(EdgeNode::url).distinct().toList()));
        stateStore.save(snapshot);
    }

    /**
     * Liefert eine unveränderliche Liste aller bekannten Regionen.
     */
    public List<String> getAllRegions() {
        return List.copyOf(regionToNodes.keySet());
    }

    /**
     * Liefert eine unveränderliche Liste aller Knoten einer Region.
     *
     * @param region Zielregion
     * @return Liste von EdgeNode (leer, wenn Region unbekannt ist oder ungültig)
     */
    public List<EdgeNode> getAllNodes(String region) {
        if (region == null) {
            return List.of();
        }
        String cleanRegion = region.trim();
        if (cleanRegion.isBlank()) {
            return List.of();
        }
        List<EdgeNode> nodes = regionToNodes.get(cleanRegion);
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        // Rückgabe einer unveränderlichen Kopie, um die interne Liste vor Modifikationen zu schützen
        return List.copyOf(nodes);
    }
}
