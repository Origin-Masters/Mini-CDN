package de.htwsaar.minicdn.cli.service.admin;

import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.transport.TransportCallAdapter;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.util.JsonUtils;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Fachlicher Service für die Router-Admin-API zum Starten, Stoppen und Auflisten
 * verwalteter Edge-Instanzen.
 *
 * <p>Die Klasse kapselt ausschließlich den technischen Zugriff auf die
 * Router-Admin-Endpunkte. Sie enthält keine CLI-Ausgabe, keine Exit-Codes
 * und keine Kenntnis über Picocli-Kommandos.</p>
 */
public final class AdminEdgeService {

    private final TransportClient transportClient;
    private final Duration requestTimeout;
    private final String adminToken;

    /**
     * Erzeugt den Service mit allen benötigten technischen Abhängigkeiten.
     *
     * @param transportClient Transport-Abstraktion für HTTP-Aufrufe
     * @param requestTimeout Standard-Timeout für Requests
     * @param adminToken Admin-Token für geschützte Router-Endpunkte
     */
    public AdminEdgeService(TransportClient transportClient, Duration requestTimeout, String adminToken) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.adminToken = requireText(adminToken, "adminToken");
    }

    /**
     * Startet eine einzelne verwaltete Edge-Instanz über den Router.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param region Zielregion der Edge
     * @param port HTTP-Port der Edge
     * @param originBaseUrl Basis-URL des Origin-Servers
     * @param autoRegister {@code true}, wenn der Router die Edge direkt registrieren soll
     * @param waitUntilReady {@code true}, wenn auf Bereitschaft gewartet werden soll
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult startEdge(
            URI routerBaseUrl,
            String region,
            int port,
            URI originBaseUrl,
            boolean autoRegister,
            boolean waitUntilReady) {

        String cleanRegion = requireText(region, "region");
        URI cleanOriginBaseUrl = Objects.requireNonNull(originBaseUrl, "originBaseUrl");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("region", cleanRegion);
        payload.put("port", port);
        payload.put("originBaseUrl", cleanOriginBaseUrl.toString());
        payload.put("autoRegister", autoRegister);
        payload.put("waitUntilReady", waitUntilReady);

        return sendPostJson(routerBaseUrl, "api/cdn/admin/edges/start", toJson(payload));
    }

    /**
     * Stoppt eine verwaltete Edge-Instanz über ihre technische Instanz-ID.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param instanceId Instanz-ID der verwalteten Edge
     * @param deregister {@code true}, wenn die Edge aus dem Routing entfernt werden soll
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult stopEdge(URI routerBaseUrl, String instanceId, boolean deregister) {
        String cleanInstanceId = normalizeInstanceId(instanceId);
        String path = "api/cdn/admin/edges/" + cleanInstanceId + "?deregister=" + deregister;
        URI url = base(routerBaseUrl).resolve(path);
        return send(TransportRequest.delete(url, requestTimeout, adminHeaders()));
    }

    /**
     * Stoppt alle verwalteten Edge-Instanzen einer Region.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param region Zielregion
     * @param deregister {@code true}, wenn die Edges aus dem Routing entfernt werden sollen
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult stopRegion(URI routerBaseUrl, String region, boolean deregister) {
        String cleanRegion = requireText(region, "region");
        String encodedRegion = UriUtils.urlEncode(cleanRegion);
        String path = "api/cdn/admin/edges/region/" + encodedRegion + "?deregister=" + deregister;
        URI url = base(routerBaseUrl).resolve(path);
        return send(TransportRequest.delete(url, requestTimeout, adminHeaders()));
    }

    /**
     * Listet alle vom Router verwalteten Edge-Instanzen auf.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult listManaged(URI routerBaseUrl) {
        URI url = base(routerBaseUrl).resolve("api/cdn/admin/edges/managed");
        return send(TransportRequest.get(url, requestTimeout, adminHeaders()));
    }

    /**
     * Startet mehrere verwaltete Edge-Instanzen mit automatischer Portvergabe.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param region Zielregion der Edges
     * @param count Anzahl zu startender Edges
     * @param originBaseUrl Basis-URL des Origin-Servers
     * @param autoRegister {@code true}, wenn der Router die Edges direkt registrieren soll
     * @param waitUntilReady {@code true}, wenn auf Bereitschaft gewartet werden soll
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult startEdgesAuto(
            URI routerBaseUrl,
            String region,
            int count,
            URI originBaseUrl,
            boolean autoRegister,
            boolean waitUntilReady) {

        String cleanRegion = requireText(region, "region");
        URI cleanOriginBaseUrl = Objects.requireNonNull(originBaseUrl, "originBaseUrl");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("region", cleanRegion);
        payload.put("count", count);
        payload.put("originBaseUrl", cleanOriginBaseUrl.toString());
        payload.put("autoRegister", autoRegister);
        payload.put("waitUntilReady", waitUntilReady);

        return sendPostJson(routerBaseUrl, "api/cdn/admin/edges/start/auto", toJson(payload));
    }

    /**
     * Führt einen POST-JSON-Request gegen einen Router-Admin-Endpunkt aus.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param path relativer Endpunktpfad
     * @param jsonBody JSON-Payload
     * @return normiertes HTTP-Ergebnis
     */
    private CallResult sendPostJson(URI routerBaseUrl, String path, String jsonBody) {
        URI url = base(routerBaseUrl).resolve(path);
        return send(TransportRequest.postJson(url, requestTimeout, adminJsonHeaders(), jsonBody));
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
     * Liefert die Admin-Header für nicht-JSON-Requests.
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
     * Normalisiert eine Router-Basis-URL auf eine konsistente Form.
     *
     * @param routerBaseUrl rohe Basis-URL
     * @return normalisierte Basis-URL mit Trailing Slash
     */
    private static URI base(URI routerBaseUrl) {
        return UriUtils.ensureTrailingSlash(Objects.requireNonNull(routerBaseUrl, "routerBaseUrl"));
    }

    /**
     * Validiert und normalisiert eine technische Instanz-ID.
     *
     * @param instanceId rohe Instanz-ID
     * @return getrimmte Instanz-ID
     */
    private static String normalizeInstanceId(String instanceId) {
        String trimmed = requireText(instanceId, "instanceId");
        if (!isSafeInstanceId(trimmed)) {
            throw new IllegalArgumentException("instanceId must match [A-Za-z0-9_-]+");
        }
        return trimmed;
    }

    /**
     * Serialisiert ein einfaches JSON-Objekt aus einer Map.
     *
     * @param values Schlüssel/Wert-Paare
     * @return JSON-String
     */
    private static String toJson(Map<String, Object> values) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;

            json.append('"')
                    .append(JsonUtils.escapeJson(entry.getKey()))
                    .append('"')
                    .append(':');

            Object value = entry.getValue();
            if (value instanceof String stringValue) {
                json.append('"').append(JsonUtils.escapeJson(stringValue)).append('"');
            } else {
                json.append(value);
            }
        }

        json.append('}');
        return json.toString();
    }

    /**
     * Prüft, ob ein Text gesetzt ist, und liefert die getrimmte Form zurück.
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
     * Prüft, ob eine Instanz-ID dem erlaubten technischen Format entspricht.
     *
     * @param value zu prüfender Wert
     * @return {@code true}, wenn die ID gültig ist
     */
    private static boolean isSafeInstanceId(String value) {
        return !value.isBlank() && value.matches("[A-Za-z0-9_-]+");
    }
}
