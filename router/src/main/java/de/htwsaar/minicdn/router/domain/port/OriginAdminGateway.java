package de.htwsaar.minicdn.router.domain.port;

import de.htwsaar.minicdn.router.application.admin.model.AdminFileResult;
import java.time.Duration;

/**
 * Port für die Kommunikation mit dem Origin (Admin-File-Operationen).
 *
 * <p>Die Fachlogik im Router hängt nur von diesem Interface ab und kennt keine konkreten
 * Transportdetails wie Adapter-Endpunkte, Headerformate oder Serialisierungsdetails.</p>
 */
public interface OriginAdminGateway {

    /**
     * Lädt eine Datei über die Origin-Admin-API hoch.
     *
     * @param path Dateipfad (relativ, ohne führenden Slash)
     * @param body Dateiinhalt
     * @return normiertes Ergebnis der Remote-Operation
     */
    AdminFileResult uploadFile(String originBaseUrl, String path, byte[] body);

    /**
     * Löscht eine Datei über die Origin-Admin-API.
     *
     * @param path Dateipfad (relativ, ohne führenden Slash)
     * @return normiertes Ergebnis der Remote-Operation
     */
    AdminFileResult deleteFile(String originBaseUrl, String path);

    /**
     * Listet Dateien über die (read-only) Origin-File-API.
     *
     * @param page Seite (Paging)
     * @param size Page-Size
     * @return normiertes Ergebnis der Remote-Operation
     */
    AdminFileResult listFiles(String originBaseUrl, int page, int size);

    /**
     * Liefert Metadaten zu einer Datei.
     * @param path Dateipfad (relativ, ohne führenden Slash)
     * @return normiertes Ergebnis der Remote-Operation mit Metadaten oder Fehlerdetails
     */
    AdminFileResult getFileMetadata(String originBaseUrl, String path);

    /**
     * Prüft die Erreichbarkeit eines Origin-Knotens über dessen Health-Endpunkt.
     *
     * @param originBaseUrl Basis-URL des Origin-Knotens
     * @param timeout Timeout für den Check
     * @return {@code true}, wenn der Knoten erreichbar und gesund ist
     */
    boolean isHealthy(String originBaseUrl, Duration timeout);
}
