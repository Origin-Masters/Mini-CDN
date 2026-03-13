package de.htwsaar.minicdn.cli.service.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.dto.UserResult;
import de.htwsaar.minicdn.cli.transport.TransportCallAdapter;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fachlicher Service für User-Administration über die Router-Admin-API.
 *
 * <p>Die Klasse kapselt ausschließlich den technischen Zugriff auf die
 * Admin-Endpunkte für Benutzerverwaltung. Sie enthält keine CLI-Ausgabe,
 * keine Exit-Code-Logik und keine Picocli-spezifische Verarbeitung.</p>
 */
public final class AdminUserService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<UserResult>> USER_LIST_TYPE = new TypeReference<>() {};

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
    public AdminUserService(
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
     * Legt einen neuen Benutzer über die Router-Admin-API an.
     *
     * @param name Benutzername
     * @param role Rollen-ID
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult addUser(String name, int role) {
        String cleanName = requireText(name, "name");

        Map<String, Object> payload = Map.of(
                "name", cleanName,
                "role", role);

        return send(TransportRequest.postJson(usersUrl(), requestTimeout, adminJsonHeaders(), toJson(payload)));
    }

    /**
     * Listet alle Benutzer roh über die Router-Admin-API auf.
     *
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult listUsersRaw() {
        return send(TransportRequest.get(usersUrl(), requestTimeout, adminHeaders()));
    }

    /**
     * Parst einen JSON-Body in eine Liste von User-Ergebnissen.
     *
     * @param body JSON-Body
     * @return geparste Benutzerliste, bei leerem Body eine leere Liste
     * @throws IllegalArgumentException falls der JSON-Body ungültig ist
     */
    public List<UserResult> parseUsers(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }

        try {
            return MAPPER.readValue(body, USER_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("failed to parse users JSON", ex);
        }
    }

    /**
     * Löscht einen Benutzer über die Router-Admin-API.
     *
     * @param id technische User-ID
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult deleteUser(long id) {
        if (id <= 0) {
            return CallResult.clientError("id must be greater than 0");
        }

        URI url = usersUrl().resolve(String.valueOf(id));
        return send(TransportRequest.delete(url, requestTimeout, adminHeaders()));
    }

    /**
     * Liefert die Basis-URL der User-Admin-API.
     *
     * @return URL des User-Endpunkts
     */
    private URI usersUrl() {
        return base().resolve("api/cdn/admin/users/");
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
     * Liefert Header für JSON-Requests.
     *
     * @return Header mit Admin-Token, User-ID und Content-Type
     */
    private Map<String, String> adminJsonHeaders() {
        Map<String, String> headers = new LinkedHashMap<>(adminHeaders());
        headers.put("Content-Type", "application/json");
        return headers;
    }

    /**
     * Serialisiert ein Objekt in JSON.
     *
     * @param payload zu serialisierendes Objekt
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
