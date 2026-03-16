package de.htwsaar.minicdn.router.domain.port;

import java.util.List;

/**
 * Port für die Persistenz des Origin-Cluster-Zustands.
 */
public interface OriginClusterStateStore {

    /**
     * Persistiert aktiven Origin und Hot-Spares.
     *
     * @param activeOrigin aktuell aktiver Origin
     * @param spareOrigins registrierte Hot-Spares
     */
    void save(String activeOrigin, List<String> spareOrigins);

    /**
     * Lädt den zuletzt persistierten Origin-Cluster-Zustand.
     *
     * @return Snapshot des Cluster-Zustands
     */
    OriginClusterState load();

    /**
     * Persistierte Sicht auf den Zustand des Origin-Clusters.
     *
     * @param activeOrigin aktuell aktiver Origin
     * @param spareOrigins registrierte Hot-Spares
     */
    record OriginClusterState(String activeOrigin, List<String> spareOrigins) {}
}
