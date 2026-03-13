package de.htwsaar.minicdn.cli.service.admin;

import com.fasterxml.jackson.databind.JsonNode;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
import de.htwsaar.minicdn.cli.util.JsonUtils;
import de.htwsaar.minicdn.cli.util.StatsFormatter;
import de.htwsaar.minicdn.common.serialization.JacksonCodec;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Fachlicher Service für Admin-Statistiken.
 *
 * <p>Die Klasse kapselt den Abruf von Router-Statistiken über den abstrahierten
 * {@link TransportClient}. Zusätzlich stellt sie formatierte Darstellungen
 * erfolgreicher Antworten bereit, damit Commands keine JSON-Struktur selbst
 * interpretieren und formatieren müssen.</p>
 *
 * <p>Die Klasse kennt keine konkrete HTTP-Implementierung und arbeitet ausschließlich
 * auf Basis der Transport-Abstraktion.</p>
 */
public final class AdminStatsService {

    /**
     * Header-Name für das Admin-Token.
     */
    private static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private final TransportClient transportClient;
    private final Duration requestTimeout;

    /**
     * Erzeugt einen neuen Service für Admin-Statistiken.
     *
     * @param transportClient transportneutrale Client-Abstraktion
     * @param requestTimeout Standard-Timeout für Requests
     */
    public AdminStatsService(TransportClient transportClient, Duration requestTimeout) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    /**
     * Ruft die Statistiken vom Router ab.
     *
     * @param host Basis-URL des Routers
     * @param windowSec Zeitfenster in Sekunden
     * @param aggregateEdge Kennzeichen, ob Edge-Metriken aggregiert werden sollen
     * @param token Admin-Token; optional, falls alternativ per Environment oder System-Property gesetzt
     * @return normierte Antwort inklusive HTTP-Status, Roh-Body, JSON-Baum und technischem Fehler
     */
    public StatsResponse fetchStats(URI host, int windowSec, boolean aggregateEdge, String token) {
        Objects.requireNonNull(host, "host");

        if (windowSec < 1) {
            return StatsResponse.clientError("windowSec must be >= 1");
        }

        final String effectiveToken;
        try {
            effectiveToken = resolveToken(token);
        } catch (IllegalArgumentException ex) {
            return StatsResponse.clientError(ex.getMessage());
        }

        URI base = UriUtils.ensureTrailingSlash(host);
        URI url = base.resolve("api/cdn/admin/stats?windowSec=" + windowSec + "&aggregateEdge=" + aggregateEdge);

        TransportResponse response = transportClient.send(
                TransportRequest.get(url, requestTimeout, Map.of(ADMIN_TOKEN_HEADER, effectiveToken)));

        if (response.error() != null) {
            return StatsResponse.transportError(response.error());
        }

        int statusCode = Objects.requireNonNull(response.statusCode(), "statusCode");
        String rawBody = Objects.toString(response.body(), "");

        if (!response.is2xx()) {
            return StatsResponse.httpError(statusCode, rawBody);
        }

        try {
            JsonNode jsonData = JacksonCodec.fromJson(rawBody, JsonNode.class);
            return StatsResponse.success(statusCode, rawBody, jsonData);
        } catch (RuntimeException ex) {
            return StatsResponse.parsingError(statusCode, rawBody, ex.getMessage());
        }
    }

    /**
     * Formatiert eine erfolgreiche Antwort als schön eingerücktes JSON.
     *
     * @param response erfolgreiche Statistikantwort
     * @return pretty-printed JSON
     * @throws IllegalArgumentException falls die Antwort nicht erfolgreich ist
     */
    public String formatPrettyJson(StatsResponse response) {
        StatsResponse successfulResponse = requireSuccessfulResponse(response);
        return JsonUtils.formatJson(successfulResponse.rawBody());
    }

    /**
     * Formatiert eine erfolgreiche Antwort menschenlesbar für die CLI.
     *
     * @param response erfolgreiche Statistikantwort
     * @param defaultWindowSec Fallback-Zeitfenster für die Ausgabe
     * @return formatierte Textausgabe
     * @throws IllegalArgumentException falls die Antwort nicht erfolgreich ist
     */
    public String formatHumanReadable(StatsResponse response, int defaultWindowSec) {
        StatsResponse successfulResponse = requireSuccessfulResponse(response);
        JsonNode root = Objects.requireNonNull(successfulResponse.jsonData(), "jsonData");

        StringWriter buffer = new StringWriter();
        PrintWriter out = new PrintWriter(buffer);

        JsonNode router = root.path("router");
        JsonNode cache = root.path("cache");
        JsonNode nodes = root.path("nodes");
        JsonNode downloads = root.path("downloads");

        out.println("[ADMIN] Mini-CDN Stats");
        out.printf("  timestamp         : %s%n", root.path("timestamp").asText("n/a"));
        out.printf("  windowSec         : %d%n", root.path("windowSec").asInt(defaultWindowSec));
        out.printf("  totalRequests     : %d%n", router.path("totalRequests").asLong());
        out.printf(
                "  requestsPerMinute : %d%n", router.path("requestsPerMinute").asLong());
        out.printf("  activeClients     : %d%n", router.path("activeClients").asLong());
        out.printf("  routingErrors     : %d%n", router.path("routingErrors").asLong());
        out.printf("  cacheHits         : %d%n", cache.path("hits").asLong());
        out.printf("  cacheMisses       : %d%n", cache.path("misses").asLong());
        out.printf("  cacheHitRatio     : %.4f%n", cache.path("hitRatio").asDouble());
        out.printf("  filesLoaded       : %d%n", cache.path("filesLoaded").asLong());
        out.printf("  nodesTotal        : %d%n", nodes.path("total").asLong());

        StatsFormatter.printDownloadTotals(out, downloads.path("byFileTotal"));
        StatsFormatter.printDownloadByEdge(out, downloads.path("byFileByEdge"));

        out.flush();
        return buffer.toString();
    }

    /**
     * Validiert, dass nur erfolgreiche Antworten formatiert werden.
     *
     * @param response zu prüfende Antwort
     * @return erfolgreiche Antwort
     * @throws IllegalArgumentException falls die Antwort nicht erfolgreich ist
     */
    private static StatsResponse requireSuccessfulResponse(StatsResponse response) {
        Objects.requireNonNull(response, "response");
        if (!response.isSuccess()) {
            throw new IllegalArgumentException("response must be successful before formatting");
        }
        return response;
    }

    /**
     * Ermittelt das effektive Admin-Token.
     *
     * <p>Priorität:
     * 1. explizit übergebenes Token,
     * 2. Environment-Variable {@code MINICDN_ADMIN_TOKEN},
     * 3. System-Property {@code minicdn.admin.token}.</p>
     *
     * @param token explizit gesetztes Token
     * @return effektives Admin-Token
     * @throws IllegalArgumentException falls kein gültiges Token verfügbar ist
     */
    private static String resolveToken(String token) {
        String directToken = Objects.toString(token, "").trim();
        if (!directToken.isBlank()) {
            return directToken;
        }

        String envToken = System.getenv("MINICDN_ADMIN_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            return envToken.trim();
        }

        String systemToken = System.getProperty("minicdn.admin.token");
        if (systemToken != null && !systemToken.isBlank()) {
            return systemToken.trim();
        }

        throw new IllegalArgumentException("admin token must not be blank");
    }

    /**
     * Normierte Antwort des Statistik-Service.
     *
     * @param statusCode HTTP-Statuscode; bei rein technischem Fehler {@code null}
     * @param rawBody roher Response-Body oder Fehlertext
     * @param jsonData geparste JSON-Daten bei erfolgreicher Antwort
     * @param error technischer oder Parsing-Fehler; sonst {@code null}
     */
    public record StatsResponse(Integer statusCode, String rawBody, JsonNode jsonData, String error) {

        /**
         * Erzeugt eine erfolgreiche Antwort.
         *
         * @param statusCode HTTP-Statuscode
         * @param rawBody roher JSON-Body
         * @param jsonData geparster JSON-Baum
         * @return erfolgreiche Antwort
         */
        public static StatsResponse success(int statusCode, String rawBody, JsonNode jsonData) {
            return new StatsResponse(statusCode, rawBody, jsonData, null);
        }

        /**
         * Erzeugt eine HTTP-Fehlerantwort.
         *
         * @param statusCode HTTP-Statuscode
         * @param rawBody roher Response-Body
         * @return HTTP-Fehlerantwort
         */
        public static StatsResponse httpError(int statusCode, String rawBody) {
            return new StatsResponse(statusCode, rawBody, null, null);
        }

        /**
         * Erzeugt eine Transport-Fehlerantwort.
         *
         * @param message Fehlermeldung
         * @return Fehlerantwort
         */
        public static StatsResponse transportError(String message) {
            return new StatsResponse(null, null, null, Objects.toString(message, "transport error"));
        }

        /**
         * Erzeugt eine Parsing-Fehlerantwort.
         *
         * @param statusCode HTTP-Statuscode
         * @param rawBody roher Response-Body
         * @param message Fehlermeldung
         * @return Fehlerantwort
         */
        public static StatsResponse parsingError(int statusCode, String rawBody, String message) {
            return new StatsResponse(statusCode, rawBody, null, Objects.toString(message, "json parsing error"));
        }

        /**
         * Erzeugt eine clientseitige Fehlerantwort.
         *
         * @param message Fehlermeldung
         * @return Fehlerantwort
         */
        public static StatsResponse clientError(String message) {
            return new StatsResponse(400, Objects.toString(message, "client error"), null, null);
        }

        /**
         * Prüft, ob die Antwort erfolgreich ist und geparste JSON-Daten enthält.
         *
         * @return {@code true}, wenn die Antwort erfolgreich ist
         */
        public boolean isSuccess() {
            return is2xx() && error == null && jsonData != null;
        }

        public boolean is2xx() {
            return statusCode != null && statusCode >= 200 && statusCode < 300;
        }

        public boolean isAuthError() {
            return Integer.valueOf(401).equals(statusCode)
                    || Integer.valueOf(403).equals(statusCode);
        }

        /**
         * Prüft, ob ein technischer oder Parsing-Fehler vorliegt.
         *
         * @return {@code true}, wenn ein Fehlertext vorhanden ist
         */
        public boolean hasError() {
            return error != null && !error.isBlank();
        }
    }
}
