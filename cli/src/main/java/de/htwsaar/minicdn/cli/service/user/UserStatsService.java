package de.htwsaar.minicdn.cli.service.user;

import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.transport.TransportCallAdapter;
import de.htwsaar.minicdn.cli.transport.TransportClient;
import de.htwsaar.minicdn.cli.transport.TransportRequest;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Fachlicher Service für User-spezifische Statistiken vom Router.
 *
 * <p>Die Klasse kapselt ausschließlich den technischen Zugriff auf die
 * Statistik-Endpunkte des Routers. Sie enthält keine CLI-Ausgabe,
 * keine Exit-Code-Logik und keine Interpretation der JSON-Antworten.</p>
 */
public final class UserStatsService {

    /**
     * Header-Name für das Zugriffstoken.
     *
     * <p>Aktuell verwendet der Router dafür den Header {@code X-Admin-Token}.</p>
     */
    private static final String AUTH_TOKEN_HEADER = "X-Admin-Token";

    private final TransportClient transportClient;
    private final Duration requestTimeout;
    private final URI routerBaseUrl;
    private final String authToken;

    /**
     * Erzeugt den Service mit allen benötigten technischen Abhängigkeiten.
     *
     * @param transportClient Transport-Abstraktion für HTTP-Aufrufe
     * @param requestTimeout Standard-Timeout für Requests
     * @param routerBaseUrl Basis-URL des Routers
     * @param authToken Zugriffstoken für geschützte Statistik-Endpunkte
     */
    public UserStatsService(
            TransportClient transportClient, Duration requestTimeout, URI routerBaseUrl, String authToken) {

        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.routerBaseUrl = Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        this.authToken = requireText(authToken, "authToken");
    }

    /**
     * Liefert Dateistatistiken für eine konkrete Datei des aktuellen Users.
     *
     * @param fileId technische Datei-ID
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult fileStatsForCurrentUser(long fileId) {
        if (fileId <= 0) {
            return CallResult.clientError("fileId must be greater than 0");
        }
        return sendGet("api/cdn/stats/file/" + fileId);
    }

    /**
     * Listet Dateistatistiken des aktuellen Users mit Limitierung.
     *
     * @param limit maximale Anzahl an Einträgen
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult listUserFilesStats(int limit) {
        if (limit < 1) {
            return CallResult.clientError("limit must be >= 1");
        }
        return sendGet("api/cdn/stats/files?limit=" + limit);
    }

    /**
     * Liefert aggregierte Statistiken des aktuellen Users für ein Zeitfenster.
     *
     * @param windowSec Zeitfenster in Sekunden
     * @return normiertes HTTP-Ergebnis
     */
    public CallResult overallStatsForCurrentUser(int windowSec) {
        if (windowSec < 1) {
            return CallResult.clientError("windowSec must be >= 1");
        }
        return sendGet("api/cdn/stats?windowSec=" + windowSec);
    }

    /**
     * Führt einen GET-Request gegen einen Router-Statistik-Endpunkt aus.
     *
     * @param relativePath relativer Endpunktpfad
     * @return normiertes HTTP-Ergebnis
     */
    private CallResult sendGet(String relativePath) {
        try {
            URI url = base().resolve(relativePath);
            TransportRequest request = TransportRequest.get(url, requestTimeout, authHeaders());
            return TransportCallAdapter.execute(transportClient, request);
        } catch (Exception ex) {
            return CallResult.transportError(ex.getMessage());
        }
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
     * Liefert die Header für geschützte Statistik-Requests.
     *
     * @return Header-Map mit Zugriffstoken
     */
    private Map<String, String> authHeaders() {
        return Map.of(AUTH_TOKEN_HEADER, authToken);
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
