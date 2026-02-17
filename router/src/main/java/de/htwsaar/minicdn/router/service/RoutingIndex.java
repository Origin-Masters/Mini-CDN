package de.htwsaar.minicdn.router.service;

import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * In-Memory-Index für Edge-Knoten je Region inklusive Round-Robin-Zählern.
 */
@Service
public class RoutingIndex {

    private final Map<String, List<EdgeNode>> regionToNodes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> regionCounters = new ConcurrentHashMap<>();

    /**
     * Fügt einen Knoten für eine Region hinzu, sofern er noch nicht existiert.
     *
     * @param region Zielregion
     * @param node hinzuzufügender Knoten
     */
    public void addEdge(String region, EdgeNode node) {
        if (region == null || node == null) {
            return;
        }

        List<EdgeNode> nodes = regionToNodes.computeIfAbsent(region, ignored -> new CopyOnWriteArrayList<>());
        if (!nodes.contains(node)) {
            nodes.add(node);
        }

        regionCounters.putIfAbsent(region, new AtomicInteger(0));
    }

    /**
     * Wählt den nächsten Knoten einer Region via Round-Robin aus.
     *
     * @param region Zielregion
     * @return nächster Knoten oder {@code null}, falls keiner vorhanden ist
     */
    public EdgeNode getNextNode(String region) {
        List<EdgeNode> nodes = regionToNodes.get(region);
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        AtomicInteger counter = regionCounters.computeIfAbsent(region, ignored -> new AtomicInteger(0));
        int index = Math.abs(counter.getAndIncrement() % nodes.size());
        return nodes.get(index);
    }

    /**
     * Entfernt einen Knoten aus einer Region.
     *
     * @param region Zielregion
     * @param node zu entfernender Knoten
     * @param removeIfEmpty entfernt die Region vollständig, wenn sie leer ist
     * @return {@code true}, wenn der Knoten entfernt wurde
     */
    public boolean removeEdge(String region, EdgeNode node, boolean removeIfEmpty) {
        List<EdgeNode> nodes = regionToNodes.get(region);
        if (nodes == null) {
            return false;
        }

        boolean removed = nodes.remove(node);

        if (removed && nodes.isEmpty() && removeIfEmpty) {
            regionToNodes.remove(region);
            regionCounters.remove(region);
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
    }

    /**
     * Liefert die Anzahl Knoten in einer Region.
     *
     * @param region Zielregion
     * @return Anzahl Knoten (0, wenn Region unbekannt)
     */
    public int getNodeCount(String region) {
        List<EdgeNode> nodes = regionToNodes.get(region);
        return nodes != null ? nodes.size() : 0;
    }
}
