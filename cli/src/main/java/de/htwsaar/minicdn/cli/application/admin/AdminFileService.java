package de.htwsaar.minicdn.cli.application.admin;

import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.port.AdminOperations;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Fachlicher Service für Admin-Dateioperationen über den Router.
 *
 * <p>Der Service enthält nur noch Eingabevalidierung und fachliche Delegation.
 * Transportdetails bleiben vollständig im Adapter.</p>
 */
public final class AdminFileService {

    private final AdminOperations adminOperations;
    private final URI routerBaseUrl;
    private final String adminToken;
    private final long loggedInUserId;

    /**
     * Erzeugt den Service.
     *
     * @param adminOperations fachlicher Port für administrative Remote-Aufrufe
     * @param routerBaseUrl Basis-URL des Routers
     * @param adminToken Admin-Token für geschützte Operationen
     * @param loggedInUserId technische ID des aktuell eingeloggten Users
     */
    public AdminFileService(
            AdminOperations adminOperations, URI routerBaseUrl, String adminToken, long loggedInUserId) {
        this.adminOperations = Objects.requireNonNull(adminOperations, "adminOperations");
        this.routerBaseUrl = Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        this.adminToken = requireText(adminToken, "adminToken");
        this.loggedInUserId = loggedInUserId;
    }

    /**
     * Lädt eine Datei über die Router-Admin-API hoch.
     *
     * @param targetPath relativer Zielpfad im CDN
     * @param localFile lokale Quelldatei
     * @param region Zielregion für die Invalidierung
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult uploadViaRouter(String targetPath, Path localFile, String region) {
        Objects.requireNonNull(localFile, "localFile");
        return adminOperations.uploadFile(routerBaseUrl, adminToken, loggedInUserId, targetPath, localFile, region);
    }

    /**
     * Löscht eine Datei über die Router-Admin-API.
     *
     * @param targetPath relativer Zielpfad im CDN
     * @param region Zielregion für die Invalidierung
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult deleteViaRouter(String targetPath, String region) {
        return adminOperations.deleteFile(routerBaseUrl, adminToken, loggedInUserId, targetPath, region);
    }

    /**
     * Listet Dateien über die Router-Admin-API auf.
     *
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult listFilesRaw() {
        return adminOperations.listFiles(routerBaseUrl, adminToken, loggedInUserId);
    }

    /**
     * Liefert Metadaten einer Datei über die Router-Admin-API.
     *
     * @param targetPath relativer Zielpfad im CDN
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult showViaRouter(String targetPath) {
        return adminOperations.showFile(routerBaseUrl, adminToken, loggedInUserId, targetPath);
    }

    /**
     * Validiert einen Pflichttext und liefert die getrimmte Form zurück.
     *
     * @param value Eingabewert
     * @param fieldName Feldname für Fehlermeldungen
     * @return getrimmter Pflichttext
     */
    private static String requireText(String value, String fieldName) {
        String trimmed = Objects.toString(value, "").trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }
}
