package de.htwsaar.minicdn.cli.service.user;

import de.htwsaar.minicdn.cli.dto.DownloadResult;
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
 * Fachlicher Service für Datei-Downloads über den Router.
 *
 * <p>Die Klasse implementiert den fachlichen Download-Flow über den Router.
 * Transport-spezifische Details wie Redirect-Following liegen im Transportadapter.
 * Sie enthält keine CLI-Ausgabe und keine Exit-Code-Logik.</p>
 */
public final class UserFileService {

    private static final String HEADER_REGION = "X-Client-Region";
    private static final String HEADER_CLIENT_ID = "X-Client-Id";

    private final TransportClient transportClient;
    private final Duration requestTimeout;

    /**
     * Erzeugt den Download-Service.
     *
     * @param transportClient Transport-Abstraktion für HTTP-Aufrufe
     * @param requestTimeout Standard-Timeout für Requests
     */
    public UserFileService(TransportClient transportClient, Duration requestTimeout) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    /**
     * Lädt eine Datei über den Router herunter.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param remotePath relativer Remote-Pfad der Datei
     * @param region Client-Region für das Routing
     * @param clientId optionale Client-ID für Statistikzwecke
     * @param out lokale Zieldatei
     * @param overwrite {@code true}, wenn eine bestehende Datei überschrieben werden darf
     * @return normiertes Download-Ergebnis
     */
    public DownloadResult downloadViaRouter(
            URI routerBaseUrl, String remotePath, String region, String clientId, Path out, boolean overwrite) {

        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        Objects.requireNonNull(out, "out");

        String cleanRemotePath = normalizeRemotePath(remotePath);
        String cleanRegion = requireText(region, "region");
        URI routingUri = routingUri(routerBaseUrl, cleanRemotePath);
        Map<String, String> routingHeaders = routingHeaders(cleanRegion, clientId);
        TransportRequest routingRequest = TransportRequest.get(routingUri, requestTimeout, routingHeaders);

        try {
            return transportClient.download(routingRequest, out, overwrite);
        } catch (Exception ex) {
            return DownloadResult.ioError(ex.getMessage());
        }
    }

    /**
     * Baut die Router-Download-URL aus Basis-URL und Remote-Pfad.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param cleanRemotePath validierter relativer Remote-Pfad
     * @return vollständige Router-Download-URL
     */
    private static URI routingUri(URI routerBaseUrl, String cleanRemotePath) {
        URI base = UriUtils.ensureTrailingSlash(routerBaseUrl);
        return base.resolve("api/cdn/files/" + cleanRemotePath);
    }

    /**
     * Baut die Header für den Router-Download-Request.
     *
     * @param region validierte Region
     * @param clientId optionale Client-ID
     * @return Header-Map für den Request
     */
    private static Map<String, String> routingHeaders(String region, String clientId) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_REGION, region);

        if (hasText(clientId)) {
            headers.put(HEADER_CLIENT_ID, clientId.trim());
        }

        return headers;
    }

    /**
     * Validiert und normalisiert einen relativen Remote-Pfad.
     *
     * @param remotePath roher Remote-Pfad
     * @return normalisierter relativer Pfad
     */
    private static String normalizeRemotePath(String remotePath) {
        String cleanPath =
                PathUtils.stripLeadingSlash(Objects.toString(remotePath, "").trim());
        if (cleanPath.isBlank()) {
            throw new IllegalArgumentException("remotePath must not be blank");
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

    /**
     * Prüft, ob ein Text gesetzt ist.
     *
     * @param value zu prüfender Text
     * @return {@code true}, wenn der Text nicht leer ist
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
