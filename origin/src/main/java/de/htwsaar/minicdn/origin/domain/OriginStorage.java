package de.htwsaar.minicdn.origin.domain;

import java.util.List;
import java.util.Optional;

/**
 * Outbound-Port für Storage-Zugriffe des Origin.
 *
 * <p>So bleibt die Fachlogik unabhängig vom konkreten Backend
 * (Filesystem, S3, Datenbank, Object Storage, ...).</p>
 */
public interface OriginStorage {

    /**
     * Liest den Dateiinhalt.
     *
     * @param path relativer Pfad
     * @return Bytes oder empty wenn nicht vorhanden
     */
    Optional<byte[]> read(String path);

    /**
     * Liest Dateimetadaten.
     *
     * @param path relativer Pfad
     * @return Metadaten oder empty wenn nicht vorhanden
     */
    Optional<OriginFileMeta> meta(String path);

    /**
     * Listet alle Dateien.
     *
     * @return alle Dateimetadaten
     */
    List<OriginFileMeta> listAll();

    /**
     * Schreibt oder überschreibt eine Datei.
     *
     * @param path relativer Pfad
     * @param body Dateiinhalt
     * @return true wenn neu angelegt, false wenn überschrieben
     */
    boolean write(String path, byte[] body);

    /**
     * Löscht eine Datei.
     *
     * @param path relativer Pfad
     * @return true wenn gelöscht, false wenn nicht vorhanden
     */
    boolean delete(String path);
}
