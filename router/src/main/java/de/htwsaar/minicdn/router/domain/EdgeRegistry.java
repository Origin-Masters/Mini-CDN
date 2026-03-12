package de.htwsaar.minicdn.router.domain;

import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.util.List;
import java.util.Map;

/**
 * Fachlicher Port für Verwaltung und Auswahl von Edge-Knoten.
 *
 * <p>Die Anwendungsschicht kennt nur diesen Vertrag und hängt damit nicht
 * von einer konkreten In-Memory-, HTTP- oder Persistenz-Implementierung ab.</p>
 */
public interface EdgeRegistry {

    /**
     * Registriert einen Edge-Knoten für eine Region.
     *
     * @param region Zielregion
     * @param node Edge-Knoten
     */
    void addEdge(String region, EdgeNode node);

    /**
     * Entfernt einen Edge-Knoten aus einer Region.
     *
     * @param region Zielregion
     * @param node Edge-Knoten
     * @param persist true, wenn der Zustand persistiert werden soll
     * @return true, wenn ein Eintrag entfernt wurde
     */
    boolean removeEdge(String region, EdgeNode node, boolean persist);

    /**
     * Markiert einen Edge-Knoten als gesund.
     *
     * @param region Zielregion
     * @param node Edge-Knoten
     */
    void markHealthy(String region, EdgeNode node);

    /**
     * Markiert einen Edge-Knoten als ungesund.
     *
     * @param region Zielregion
     * @param node Edge-Knoten
     */
    void markUnhealthy(String region, EdgeNode node);

    /**
     * Liefert die Anzahl aller registrierten Knoten einer Region.
     *
     * @param region Zielregion
     * @return Anzahl aller Knoten
     */
    int getNodeCount(String region);

    /**
     * Liefert die Anzahl aktuell gesunder Knoten einer Region.
     *
     * @param region Zielregion
     * @return Anzahl gesunder Knoten
     */
    int getHealthyNodeCount(String region);

    /**
     * Liefert die nächsten fachlichen Kandidaten in stabiler Round-Robin-Reihenfolge.
     *
     * @param region Zielregion
     * @param maxCandidates maximale Anzahl Kandidaten
     * @return Kandidatenliste ohne Duplikate
     */
    List<EdgeNode> getNextNodes(String region, int maxCandidates);

    /**
     * Liefert alle Knoten einer Region.
     *
     * @param region Zielregion
     * @return alle Knoten
     */
    List<EdgeNode> getAllNodes(String region);

    /**
     * Liefert alle bekannten Regionen.
     *
     * @return Regionen
     */
    List<String> getAllRegions();

    /**
     * Liefert eine Momentaufnahme des Index.
     *
     * @return Region -> Edge-Knoten
     */
    Map<String, List<EdgeNode>> getRawIndex();
}
