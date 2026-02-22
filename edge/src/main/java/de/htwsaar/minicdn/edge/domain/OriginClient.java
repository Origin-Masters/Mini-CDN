package de.htwsaar.minicdn.edge.domain;

/**
 * Port zur Abstraktion der Transport-Schicht zum Origin.
 * HTTP heute – gRPC morgen; die Fachlogik bleibt unberührt.
 */
public interface OriginClient {

    /**
     * Lädt eine Datei vom Origin.
     *
     * @param path relativer Pfad (ohne führenden Slash)
     * @return Origin-Antwort inkl. Status, Body und Integritäts-Headern
     */
    OriginFileResponse fetchFile(String path);

    /**
     * Führt einen HEAD-Call für Metadaten aus.
     *
     * @param path relativer Pfad (ohne führenden Slash)
     * @return Origin-Antwort mit Status und Headern, i. d. R. ohne Body
     */
    OriginFileResponse headFile(String path);
}
