package de.htwsaar.minicdn.router.domain.port;

import java.util.List;
import java.util.Map;

/**
 * Port für die Persistenz des fachlichen Routing-Zustands.
 */
public interface RoutingStateStore {

    /**
     * Persistiert den Routing-Zustand.
     *
     * @param routingState Map von Region zu registrierten Edge-URLs
     */
    void save(Map<String, List<String>> routingState);

    /**
     * Lädt den zuletzt persistierten Routing-Zustand.
     *
     * @return Map von Region zu registrierten Edge-URLs
     */
    Map<String, List<String>> load();
}
