package de.htwsaar.minicdn.router.domain;

import de.htwsaar.minicdn.router.dto.EdgeNode;
import java.net.URI;

/**
 * Port zur Ermittlung von Auslieferungszielen für Dateien.
 *
 * <p>Die Fachlogik kennt nur die fachliche Entscheidung, ob eine Datei
 * über eine Edge oder über den Origin ausgeliefert wird. Die konkrete
 * Bildung transportabhängiger Ziel-URIs liegt im Adapter.</p>
 */
public interface FileRouteLocationResolver {

    /**
     * Ermittelt das Auslieferungsziel für eine Datei über eine konkrete Edge-Instanz.
     *
     * @param node Edge-Knoten
     * @param path relativer Dateipfad
     * @return vollständige Ziel-URI
     */
    URI resolveEdgeFileLocation(EdgeNode node, String path);

    /**
     * Ermittelt das Auslieferungsziel für eine Datei über den Origin-Fallback.
     *
     * @param path relativer Dateipfad
     * @return vollständige Ziel-URI
     */
    URI resolveOriginFileLocation(String path);
}
