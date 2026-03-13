package de.htwsaar.minicdn.cli.service.admin;

import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.transport.TransportCallAdapter;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.common.util.PathUtils;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fachlicher Service für Admin-Dateioperationen über den Router.
 *
 * <p>Die Klasse kapselt ausschließlich den Zugriff auf die Router-Admin-API.
 * Sie enthält keine CLI-Ausgabe, keine Exit-Code-Logik und keine direkte
 * Kenntnis von Picocli-Kommandos.</p>
 */
public final class AdminFileService {

    private final TransportClient transportClient;
    private final Duration requestTimeout;
    private final URI routerBaseUrl;
    private final String adminToken;
    private final long loggedInUserId;

    /**
     * Erzeugt den Service mit allen benötigten technischen Abhängigkeiten.
     *
     * @param transportClient Transport-Abstraktion für HTTP-Aufrufe
     * @param requestTimeout Standard-Timeout für Requests
     * @param routerBaseUrl Basis-URL des Routers
     * @param adminToken Admin-Token für geschützte Endpunkte
     * @param loggedInUserId technische ID des aktuell eingeloggten Users
     */
    public AdminFileService(
            TransportClient transportClient,
            Duration requestTimeout,
            URI routerBaseUrl,
            String adminToken,
            long loggedInUserId) {

        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.routerBaseUrl = Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        this.adminToken = requireText(adminToken, "adminToken");
        this.loggedInUserId = loggedInUserId;
    }

    /**
     * Lädt eine Datei über die Router-Admin-API hoch und stößt die Cache-Invalidierung an.
     *
     * @param targetPath relativer Zielpfad im CDN
     * @param localFile lokale Quelldatei
     * @param region Zielregion für die Invalidierung
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult uploadViaRouter(String targetPath, Path localFile, String region) {
        Objects.requireNonNull(localFile, "localFile");

        String cleanPath = normalizeTargetPath(targetPath, "targetPath");
        String cleanRegion = requireText(region, "region");

        URI url = adminFileUrl(cleanPath, cleanRegion);
        return send(TransportRequest.putFile(url, requestTimeout, adminBinaryHeaders(), localFile));
    }

    /**
     * Löscht eine Datei über die Router-Admin-API und stößt die Cache-Invalidierung an.
     *
     * @param targetPath relativer Zielpfad im CDN
     * @param region Zielregion für die Invalidierung
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult deleteViaRouter(String targetPath, String region) {
        String cleanPath = normalizeTargetPath(targetPath, "path");
        String cleanRegion = requireText(region, "region");

        URI url = adminFileUrl(cleanPath, cleanRegion);
        return send(TransportRequest.delete(url, requestTimeout, adminHeaders()));
    }

    /**
     * Listet Dateien über die Router-Admin-API auf.
     *
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult listFilesRaw() {
        URI url = base().resolve("api/cdn/admin/files");
        return send(TransportRequest.get(url, requestTimeout, adminHeaders()));
    }

    /**
     * Liefert Metadaten einer Datei über die Router-Admin-API.
     *
     * @param targetPath relativer Zielpfad im CDN
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult showViaRouter(String targetPath) {
        String cleanPath = normalizeTargetPath(targetPath, "path");
        URI url = base().resolve("api/cdn/admin/files/" + cleanPath);
        return send(TransportRequest.get(url, requestTimeout, adminHeaders()));
    }

    /**
     * Liefert die normalisierte Router-Basis-URL mit Trailing Slash.
     *
     * @return normalisierte Router-Basis-URL
     */
    private URI base() {
        return UriUtils.ensureTrailingSlash(routerBaseUrl);
    }

    /**
     * Baut die Ziel-URL für Admin-Dateioperationen mit Regionsparameter.
     *
     * @param cleanPath bereits validierter relativer Zielpfad
     * @param cleanRegion bereits validierte Region
     * @return vollständige Ziel-URL
     */
    private URI adminFileUrl(String cleanPath, String cleanRegion) {
        String pathAndQuery = "api/cdn/admin/files/" + cleanPath + "?region=" + UriUtils.urlEncode(cleanRegion);
        return base().resolve(pathAndQuery);
    }

    /**
     * Führt einen Transport-Request aus und normalisiert das Ergebnis.
     *
     * @param request vorbereiteter Transport-Request
     * @return normiertes HTTP-Ergebnis
     */
    private CallResult send(TransportRequest request) {
        return TransportCallAdapter.execute(transportClient, request);
    }

    /**
     * Liefert Standard-Header für Admin-Requests.
     *
     * @return Header mit Admin-Token und User-ID
     */
    private Map<String, String> adminHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Admin-Token", adminToken);
        headers.put("X-User-Id", String.valueOf(loggedInUserId));
        return headers;
    }

    /**
     * Liefert Header für binäre Upload-Requests.
     *
     * @return Header mit Admin-Token, User-ID und Content-Type
     */
    private Map<String, String> adminBinaryHeaders() {
        Map<String, String> headers = new LinkedHashMap<>(adminHeaders());
        headers.put("Content-Type", "application/octet-stream");
        return headers;
    }

    /**
     * Validiert einen relativen Zielpfad.
     *
     * @param rawPath roher Zielpfad
     * @param fieldName Feldname für Fehlermeldungen
     * @return normalisierter relativer Zielpfad
     */
    private static String normalizeTargetPath(String rawPath, String fieldName) {
        String cleanPath =
                PathUtils.stripLeadingSlash(Objects.toString(rawPath, "").trim());
        if (cleanPath.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return cleanPath;
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
