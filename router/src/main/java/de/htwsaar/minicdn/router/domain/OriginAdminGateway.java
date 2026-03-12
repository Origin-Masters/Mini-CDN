package de.htwsaar.minicdn.router.domain;

import de.htwsaar.minicdn.router.dto.AdminFileResult;

/**
 * Port für die Kommunikation mit dem Origin (Admin-File-Operationen).
 *
 * <p>Die Fachlogik im Router hängt nur von diesem Interface ab und kennt keine konkreten
 * HTTP-Details (URLs, Header, JSON, HttpClient).</p>
 */
public interface OriginAdminGateway {

    /**
     * Lädt eine Datei über die Origin-Admin-API hoch.
     *
     * @param path Dateipfad (relativ, ohne führenden Slash)
     * @param body Dateiinhalt
     * @return Ergebnis inkl. HTTP-Statuscode (vom Origin) und ggf. Fehlermeldung
     */
    AdminFileResult uploadFile(String path, byte[] body);

    /**
     * Löscht eine Datei über die Origin-Admin-API.
     *
     * @param path Dateipfad (relativ, ohne führenden Slash)
     * @return Ergebnis inkl. HTTP-Statuscode (vom Origin) und ggf. Fehlermeldung
     */
    AdminFileResult deleteFile(String path);

    /**
     * Listet Dateien über die (read-only) Origin-File-API.
     *
     * @param page Seite (Paging)
     * @param size Page-Size
     * @return Ergebnis inkl. HTTP-Statuscode und Response-Body (String)
     */
    AdminFileResult listFiles(int page, int size);

    /**
     * Liefert Metadaten zu einer Datei.
     * @param path
     * @return
     */
    AdminFileResult getFileMetadata(String path);
}
