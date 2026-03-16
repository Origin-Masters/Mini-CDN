package de.htwsaar.minicdn.cli.adapter.out.http;

import de.htwsaar.minicdn.cli.adapter.out.transport.TransportClient;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportRequest;
import de.htwsaar.minicdn.cli.adapter.out.transport.TransportResponse;
import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Gemeinsame Hilfen für HTTP-Adapter der CLI.
 */
final class HttpAdapterSupport {

    private HttpAdapterSupport() {}

    /**
     * Normalisiert eine Basis-URL auf eine Form mit Trailing Slash.
     *
     * @param baseUrl rohe Basis-URL
     * @return normalisierte Basis-URL
     */
    static URI base(URI baseUrl) {
        return UriUtils.ensureTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
    }

    /**
     * Validiert einen Pflichttext und liefert ihn getrimmt zurück.
     *
     * @param value Eingabewert
     * @param fieldName Feldname für Fehlermeldungen
     * @return getrimmter Pflichttext
     */
    static String requireText(String value, String fieldName) {
        String trimmed = Objects.toString(value, "").trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    /**
     * Liefert Standard-Header für Admin-Aufrufe.
     *
     * @param adminToken Admin-Token
     * @return Header-Map
     */
    static Map<String, String> adminHeaders(String adminToken) {
        return Map.of("X-Admin-Token", requireText(adminToken, "adminToken"));
    }

    /**
     * Liefert Standard-Header für Admin-Aufrufe mit User-Kontext.
     *
     * @param adminToken Admin-Token
     * @param loggedInUserId technische User-ID
     * @return Header-Map
     */
    static Map<String, String> adminHeaders(String adminToken, long loggedInUserId) {
        Map<String, String> headers = new LinkedHashMap<>(adminHeaders(adminToken));
        headers.put("X-User-Id", String.valueOf(loggedInUserId));
        return headers;
    }

    /**
     * Liefert JSON-Header ohne Authentifizierung.
     *
     * @return Header-Map
     */
    static Map<String, String> jsonHeaders() {
        return Map.of("Content-Type", "application/json");
    }

    /**
     * Liefert JSON-Header für Admin-Aufrufe.
     *
     * @param adminToken Admin-Token
     * @return Header-Map
     */
    static Map<String, String> adminJsonHeaders(String adminToken) {
        Map<String, String> headers = new LinkedHashMap<>(adminHeaders(adminToken));
        headers.put("Content-Type", "application/json");
        return headers;
    }

    /**
     * Liefert JSON-Header für Admin-Aufrufe mit User-Kontext.
     *
     * @param adminToken Admin-Token
     * @param loggedInUserId technische User-ID
     * @return Header-Map
     */
    static Map<String, String> adminJsonHeaders(String adminToken, long loggedInUserId) {
        Map<String, String> headers = new LinkedHashMap<>(adminHeaders(adminToken, loggedInUserId));
        headers.put("Content-Type", "application/json");
        return headers;
    }

    /**
     * Führt einen textbasierten Request aus und normiert das Ergebnis.
     *
     * @param transportClient Transportadapter
     * @param request Request-Beschreibung
     * @return normiertes Ergebnis
     */
    static CallResult execute(TransportClient transportClient, TransportRequest request) {
        Objects.requireNonNull(transportClient, "transportClient");
        Objects.requireNonNull(request, "request");
        try {
            return toCallResult(transportClient.send(request));
        } catch (RuntimeException ex) {
            return CallResult.transportError(ex.getMessage());
        }
    }

    private static CallResult toCallResult(TransportResponse response) {
        if (response == null) {
            return CallResult.transportError("response must not be null");
        }
        if (response.error() != null) {
            return CallResult.transportError(response.error());
        }
        int remoteCode = Objects.requireNonNull(response.statusCode(), "statusCode");
        if (response.is2xx()) {
            return CallResult.success(remoteCode, response.body());
        }
        return remoteCode >= 400 && remoteCode < 500
                ? CallResult.rejected(remoteCode, response.body())
                : CallResult.serverError(remoteCode, response.body());
    }
}
