package de.htwsaar.minicdn.cli.service.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.cli.dto.UserResult;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.cli.transport.TransportResponse;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Fachlicher Service für den User-Login über die Router-Auth-API.
 *
 * <p>Die Klasse kapselt ausschließlich den technischen Aufruf des Login-Endpunkts
 * und die Normierung des Ergebnisses. CLI-Ausgabe und Session-Verwaltung liegen
 * bewusst außerhalb dieses Services.</p>
 */
public final class UserAuthService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";

    private final TransportClient transportClient;
    private final Duration requestTimeout;
    private final URI routerBaseUrl;

    /**
     * Erzeugt den Login-Service.
     *
     * @param transportClient Transport-Abstraktion für HTTP-Aufrufe
     * @param requestTimeout Standard-Timeout für Requests
     * @param routerBaseUrl Basis-URL des Routers
     */
    public UserAuthService(TransportClient transportClient, Duration requestTimeout, URI routerBaseUrl) {
        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.routerBaseUrl = Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
    }

    /**
     * Führt einen Login über die Router-Auth-API aus.
     *
     * @param username Benutzername
     * @return normiertes Login-Ergebnis
     */
    public LoginResult login(String username) {
        String cleanUsername = requireText(username, "username");

        try {
            TransportResponse response = transportClient.send(TransportRequest.postJson(
                    loginUrl(), requestTimeout, jsonHeaders(), toLoginPayload(cleanUsername)));

            return toLoginResult(response);
        } catch (Exception ex) {
            return LoginResult.transportError(ex.getMessage());
        }
    }

    /**
     * Liefert die URL des Login-Endpunkts.
     *
     * @return vollständige Login-URL
     */
    private URI loginUrl() {
        return UriUtils.ensureTrailingSlash(routerBaseUrl).resolve("api/cdn/auth/login");
    }

    /**
     * Liefert die Header für JSON-Requests.
     *
     * @return Header-Map mit Content-Type JSON
     */
    private static Map<String, String> jsonHeaders() {
        return Map.of(CONTENT_TYPE_HEADER, APPLICATION_JSON);
    }

    /**
     * Serialisiert den Login-Payload.
     *
     * @param username validierter Benutzername
     * @return JSON-Payload
     */
    private static String toLoginPayload(String username) {
        try {
            return MAPPER.writeValueAsString(Map.of("name", username));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("failed to serialize login payload", ex);
        }
    }

    /**
     * Wandelt eine Transportantwort in ein normiertes Login-Ergebnis um.
     *
     * @param response Transportantwort
     * @return normiertes Login-Ergebnis
     */
    private static LoginResult toLoginResult(TransportResponse response) {
        if (response == null) {
            return LoginResult.transportError("response must not be null");
        }

        if (response.error() != null) {
            return LoginResult.transportError(response.error());
        }

        int statusCode = Objects.requireNonNull(response.statusCode(), "statusCode");
        String rawBody = Objects.toString(response.body(), "");

        if (!response.is2xx()) {
            return LoginResult.httpError(statusCode, rawBody);
        }

        try {
            UserResult user = MAPPER.readValue(rawBody, UserResult.class);
            return LoginResult.success(user, statusCode, rawBody);
        } catch (JsonProcessingException ex) {
            return LoginResult.parsingError(statusCode, rawBody, ex.getMessage());
        }
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
     * Normiertes Ergebnis eines Login-Aufrufs.
     *
     * @param user geparster Benutzer bei Erfolg
     * @param statusCode HTTP-Statuscode, sofern vorhanden
     * @param rawBody roher Response-Body
     * @param error technischer oder Parsing-Fehler
     */
    public record LoginResult(UserResult user, Integer statusCode, String rawBody, String error) {

        /**
         * Erzeugt ein Erfolgsresultat.
         *
         * @param user geparster Benutzer
         * @param statusCode HTTP-Statuscode
         * @param rawBody roher Response-Body
         * @return Erfolgsresultat
         */
        public static LoginResult success(UserResult user, int statusCode, String rawBody) {
            return new LoginResult(Objects.requireNonNull(user, "user"), statusCode, rawBody, null);
        }

        /**
         * Erzeugt ein HTTP-Fehlerresultat.
         *
         * @param statusCode HTTP-Statuscode
         * @param rawBody roher Response-Body
         * @return HTTP-Fehlerresultat
         */
        public static LoginResult httpError(int statusCode, String rawBody) {
            return new LoginResult(null, statusCode, rawBody, null);
        }

        /**
         * Erzeugt ein Transport-Fehlerresultat.
         *
         * @param error Fehlermeldung
         * @return Fehlerresultat
         */
        public static LoginResult transportError(String error) {
            return new LoginResult(null, null, null, Objects.toString(error, "transport error"));
        }

        /**
         * Erzeugt ein Parsing-Fehlerresultat.
         *
         * @param statusCode HTTP-Statuscode
         * @param rawBody roher Response-Body
         * @param error Fehlermeldung
         * @return Fehlerresultat
         */
        public static LoginResult parsingError(int statusCode, String rawBody, String error) {
            return new LoginResult(null, statusCode, rawBody, Objects.toString(error, "json parsing error"));
        }

        /**
         * Prüft, ob der Login erfolgreich war.
         *
         * @return {@code true}, wenn ein Benutzerobjekt vorhanden ist
         */
        public boolean isSuccess() {
            return user != null && hasSuccessfulStatus() && error == null;
        }

        public boolean hasSuccessfulStatus() {
            return statusCode != null && statusCode >= 200 && statusCode < 300;
        }

        public boolean isClientError() {
            return statusCode != null && statusCode >= 400 && statusCode < 500;
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
