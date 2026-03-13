package de.htwsaar.minicdn.cli.service.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.transport.TransportCallAdapter;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fachlicher Service für Remote-Konfiguration von Origin- und Edge-Servern.
 *
 * <p>Die Klasse kapselt ausschließlich die Kommunikation mit den Admin-Endpunkten
 * der Origin- und Edge-Services. Sie kennt keine CLI-Ausgabe und keine Exit-Codes.
 * Authentifizierungsdaten und technische Infrastruktur werden vollständig per
 * Konstruktor injiziert.</p>
 */
public final class AdminConfigService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TransportClient transportClient;
    private final Duration requestTimeout;
    private final String adminToken;

    /**
     * Erzeugt den Service mit allen benötigten technischen Abhängigkeiten.
     *
     * @param transportClient Transport-Abstraktion für HTTP-Aufrufe
     * @param requestTimeout Standard-Timeout für Requests
     * @param adminToken Admin-Token für geschützte Endpunkte
     */
    public AdminConfigService(TransportClient transportClient, Duration requestTimeout, String adminToken) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.adminToken = requireText(adminToken, "adminToken");
    }

    /**
     * Liest die aktuelle Laufzeitkonfiguration des Origin-Servers.
     *
     * @param originBaseUrl Basis-URL des Origin-Servers
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult getOriginConfig(URI originBaseUrl) {
        return sendGet(originBaseUrl, "api/origin/admin/config");
    }

    /**
     * Aktualisiert Teile der Laufzeitkonfiguration des Origin-Servers.
     *
     * @param originBaseUrl Basis-URL des Origin-Servers
     * @param maxUploadBytes maximale Upload-Größe in Bytes, optional
     * @param logLevel Root-Log-Level, optional
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult patchOriginConfig(URI originBaseUrl, Long maxUploadBytes, String logLevel) {
        Map<String, Object> payload = new LinkedHashMap<>();

        if (maxUploadBytes != null) {
            payload.put("maxUploadBytes", maxUploadBytes);
        }
        if (hasText(logLevel)) {
            payload.put("logLevel", logLevel.trim());
        }
        if (payload.isEmpty()) {
            return CallResult.clientError("at least one field must be provided");
        }

        return sendPatch(originBaseUrl, "api/origin/admin/config", toJson(payload));
    }

    /**
     * Liest die aktuelle Laufzeitkonfiguration des Edge-Servers.
     *
     * @param edgeBaseUrl Basis-URL des Edge-Servers
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult getEdgeConfig(URI edgeBaseUrl) {
        return sendGet(edgeBaseUrl, "api/edge/admin/config");
    }

    /**
     * Aktualisiert Teile der Laufzeitkonfiguration des Edge-Servers.
     *
     * @param edgeBaseUrl Basis-URL des Edge-Servers
     * @param region Region des Edge-Servers, optional
     * @param defaultTtlMs Standard-TTL in Millisekunden, optional
     * @param maxEntries maximale Cache-Einträge, optional
     * @param replacementStrategy Ersetzungsstrategie, optional
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult patchEdgeConfig(
            URI edgeBaseUrl, String region, Long defaultTtlMs, Integer maxEntries, String replacementStrategy) {

        Map<String, Object> payload = new LinkedHashMap<>();

        if (hasText(region)) {
            payload.put("region", region.trim());
        }
        if (defaultTtlMs != null) {
            payload.put("defaultTtlMs", defaultTtlMs);
        }
        if (maxEntries != null) {
            payload.put("maxEntries", maxEntries);
        }
        if (hasText(replacementStrategy)) {
            payload.put("replacementStrategy", replacementStrategy.trim().toUpperCase());
        }
        if (payload.isEmpty()) {
            return CallResult.clientError("at least one field must be provided");
        }

        return sendPatch(edgeBaseUrl, "api/edge/admin/config", toJson(payload));
    }

    /**
     * Liest alle TTL-Präfixregeln des Edge-Servers.
     *
     * @param edgeBaseUrl Basis-URL des Edge-Servers
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult getEdgeTtlPolicies(URI edgeBaseUrl) {
        return sendGet(edgeBaseUrl, "api/edge/admin/config/ttl");
    }

    /**
     * Setzt eine TTL-Regel für ein Pfad-Präfix auf dem Edge-Server.
     *
     * @param edgeBaseUrl Basis-URL des Edge-Servers
     * @param prefix Pfad-Präfix
     * @param ttlMs TTL in Millisekunden
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult setEdgeTtlPolicy(URI edgeBaseUrl, String prefix, Long ttlMs) {
        String cleanPrefix = requireText(prefix, "prefix");
        if (ttlMs == null) {
            return CallResult.clientError("ttlMs must not be null");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("prefix", cleanPrefix);
        payload.put("ttlMs", ttlMs);

        return sendPut(edgeBaseUrl, "api/edge/admin/config/ttl", toJson(payload));
    }

    /**
     * Entfernt eine TTL-Regel für ein Pfad-Präfix auf dem Edge-Server.
     *
     * @param edgeBaseUrl Basis-URL des Edge-Servers
     * @param prefix Pfad-Präfix
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult removeEdgeTtlPolicy(URI edgeBaseUrl, String prefix) {
        String cleanPrefix = requireText(prefix, "prefix");
        URI url = base(edgeBaseUrl).resolve("api/edge/admin/config/ttl?prefix=" + UriUtils.urlEncode(cleanPrefix));

        return send(TransportRequest.delete(url, requestTimeout, adminHeaders()));
    }

    /**
     * Führt einen GET-Request gegen einen Admin-Endpunkt aus.
     *
     * @param baseUrl Basis-URL des Zielsystems
     * @param path relativer Endpunktpfad
     * @return normiertes HTTP-Ergebnis
     */
    private CallResult sendGet(URI baseUrl, String path) {
        URI url = base(baseUrl).resolve(path);
        return send(TransportRequest.get(url, requestTimeout, adminHeaders()));
    }

    /**
     * Führt einen PATCH-JSON-Request gegen einen Admin-Endpunkt aus.
     *
     * @param baseUrl Basis-URL des Zielsystems
     * @param path relativer Endpunktpfad
     * @param jsonBody JSON-Payload
     * @return normiertes HTTP-Ergebnis
     */
    private CallResult sendPatch(URI baseUrl, String path, String jsonBody) {
        URI url = base(baseUrl).resolve(path);
        return send(TransportRequest.patchJson(url, requestTimeout, adminJsonHeaders(), jsonBody));
    }

    /**
     * Führt einen PUT-JSON-Request gegen einen Admin-Endpunkt aus.
     *
     * @param baseUrl Basis-URL des Zielsystems
     * @param path relativer Endpunktpfad
     * @param jsonBody JSON-Payload
     * @return normiertes HTTP-Ergebnis
     */
    private CallResult sendPut(URI baseUrl, String path, String jsonBody) {
        URI url = base(baseUrl).resolve(path);
        return send(TransportRequest.putJson(url, requestTimeout, adminJsonHeaders(), jsonBody));
    }

    /**
     * Führt einen Transport-Request aus und wandelt das Ergebnis in das
     * gemeinsame CLI-Ergebnisformat um.
     *
     * @param request vorbereiteter Transport-Request
     * @return normiertes HTTP-Ergebnis
     */
    private CallResult send(TransportRequest request) {
        return TransportCallAdapter.execute(transportClient, request);
    }

    /**
     * Liefert die Admin-Header für einfache Requests.
     *
     * @return Header-Map mit Admin-Token
     */
    private Map<String, String> adminHeaders() {
        return Map.of("X-Admin-Token", adminToken);
    }

    /**
     * Liefert die Admin-Header für JSON-Requests.
     *
     * @return Header-Map mit Admin-Token und Content-Type
     */
    private Map<String, String> adminJsonHeaders() {
        Map<String, String> headers = new LinkedHashMap<>(adminHeaders());
        headers.put("Content-Type", "application/json");
        return headers;
    }

    /**
     * Normalisiert eine Basis-URL auf eine konsistente Form mit Trailing Slash.
     *
     * @param baseUrl rohe Basis-URL
     * @return normalisierte Basis-URL
     */
    private static URI base(URI baseUrl) {
        return UriUtils.ensureTrailingSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
    }

    /**
     * Serialisiert ein Objekt in JSON.
     *
     * @param payload zu serialisierende Daten
     * @return JSON-String
     */
    private static String toJson(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("failed to serialize JSON payload", ex);
        }
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

    /**
     * Validiert einen Pflichttext und gibt die getrimmte Form zurück.
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
