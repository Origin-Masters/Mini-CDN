package de.htwsaar.minicdn.edge.domain.port;

import de.htwsaar.minicdn.edge.domain.model.ReplacementStrategy;

/**
 * Erzeugt Cache-Implementierungen passend zur fachlichen Strategie.
 */
public interface EdgeCacheFactory {

    /**
     * Erstellt einen Cache für die gewünschte Replacement-Strategie.
     *
     * @param strategy fachliche Cache-Strategie
     * @return passende Cache-Implementierung
     */
    EdgeCache create(ReplacementStrategy strategy);
}
