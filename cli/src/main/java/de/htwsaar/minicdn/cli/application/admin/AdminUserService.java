package de.htwsaar.minicdn.cli.application.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.model.UserResult;
import de.htwsaar.minicdn.cli.domain.port.AdminOperations;
import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * Fachlicher Service für User-Administration über den Router.
 *
 * <p>Der Service enthält nur noch fachliche Validierung sowie JSON-Parsing für
 * die Kommandoausgabe. Die Transportschicht bleibt im Adapter.</p>
 */
public final class AdminUserService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<UserResult>> USER_LIST_TYPE = new TypeReference<>() {};

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
    public AdminUserService(
            AdminOperations adminOperations, URI routerBaseUrl, String adminToken, long loggedInUserId) {
        this.adminOperations = Objects.requireNonNull(adminOperations, "adminOperations");
        this.routerBaseUrl = Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        this.adminToken = requireText(adminToken, "adminToken");
        this.loggedInUserId = loggedInUserId;
    }

    /**
     * Legt einen neuen Benutzer an.
     *
     * @param name Benutzername
     * @param role Rollen-ID
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult addUser(String name, int role) {
        return adminOperations.addUser(routerBaseUrl, adminToken, loggedInUserId, requireText(name, "name"), role);
    }

    /**
     * Listet alle Benutzer roh auf.
     *
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult listUsersRaw() {
        return adminOperations.listUsers(routerBaseUrl, adminToken, loggedInUserId);
    }

    /**
     * Parst einen JSON-Body in eine Liste von User-Ergebnissen.
     *
     * @param body JSON-Body
     * @return geparste Benutzerliste, bei leerem Body eine leere Liste
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
     * Löscht einen Benutzer.
     *
     * @param id technische User-ID
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult deleteUser(long id) {
        if (id <= 0) {
            return CallResult.clientError("id must be greater than 0");
        }
        return adminOperations.deleteUser(routerBaseUrl, adminToken, loggedInUserId, id);
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
