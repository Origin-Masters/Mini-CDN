package de.htwsaar.minicdn.origin.domain;

import java.util.Optional;

/**
 * Inbound-Port (Use-Case API) des Origin-Servers.
 *
 * <p>Transportadapter (HTTP/gRPC/CLI) dürfen nur dieses Interface verwenden.
 * Dadurch bleibt die Fachlogik unabhängig von Spring-Web, HTTP-Headern und Controllers.</p>
 */
public interface OriginFiles {

    /**
     * Listet alle Dateien paginiert.
     *
     * @param page Seite ab 1
     * @param size Seitengröße > 0
     * @return Paginierte Dateiliste
     * @throws IllegalArgumentException bei ungültigen Parametern
     */
    OriginFileList list(int page, int size);

    /**
     * Lädt den Dateiinhalt inkl. Metadaten.
     *
     * @param path relativer Pfad, z. B. "images/logo.png"
     * @return Datei oder empty wenn nicht vorhanden
     */
    Optional<OriginFileContent> get(String path);

    /**
     * Liefert nur fachliche Metadaten einer Datei.
     *
     * @param path relativer Pfad
     * @return Metadaten oder empty wenn nicht vorhanden
     */
    Optional<OriginFileMetadata> getMetadata(String path);

    /**
     * Schreibt/überschreibt eine Datei.
     *
     * @param path relativer Pfad
     * @param body Bytes
     * @return Ergebnis (created vs updated)
     */
    OriginPutResult put(String path, byte[] body);

    /**
     * Löscht eine Datei.
     *
     * @param path relativer Pfad
     * @return true wenn gelöscht
     */
    boolean delete(String path);
}
