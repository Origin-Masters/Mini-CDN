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
import java.util.function.LongSupplier;

/**
 * Fachlicher Service für User-spezifische Statistiken vom Router.
 *
 * <p>Die Klasse kapselt ausschließlich den technischen Zugriff auf die
 * User-Statistik-Endpunkte des Routers unter {@code /api/cdn/stats}. Sie enthält keine CLI-Ausgabe,
 * keine Exit-Code-Logik und keine Interpretation der JSON-Antworten.</p>
 */
public final class UserStatsService {

    /** Header-Name der technischen User-ID. */
    private static final String USER_ID_HEADER = "X-User-Id";

    private final TransportClient transportClient;
    private final Duration requestTimeout;
    private final URI routerBaseUrl;
    private final LongSupplier loggedInUserIdSupplier;

    /**
     * Erzeugt den Service mit allen benötigten technischen Abhängigkeiten.
     *
     * @param transportClient Transport-Abstraktion für HTTP-Aufrufe
     * @param requestTimeout Standard-Timeout für Requests
     * @param routerBaseUrl Basis-URL des Routers
     * @param loggedInUserIdSupplier liefert die technische ID des aktuell eingeloggten Users
     */
    public UserStatsService(
            TransportClient transportClient,
            Duration requestTimeout,
            URI routerBaseUrl,
            LongSupplier loggedInUserIdSupplier) {

        this.transportClient = Objects.requireNonNull(transportClient, "transportClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.routerBaseUrl = Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        this.loggedInUserIdSupplier = Objects.requireNonNull(loggedInUserIdSupplier, "loggedInUserIdSupplier");
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
            long loggedInUserId = loggedInUserIdSupplier.getAsLong();
            if (loggedInUserId <= 0) {
                return CallResult.transportError("login required: missing user id");
            }
            URI url = base().resolve(relativePath);
            TransportRequest request = TransportRequest.get(url, requestTimeout, authHeaders(loggedInUserId));
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
     * Liefert die Header für userbezogene Statistik-Requests.
     *
     * @param loggedInUserId technische ID des aktuellen Users
     * @return Header-Map mit technischer User-ID
     */
    private Map<String, String> authHeaders(long loggedInUserId) {
        return Map.of(USER_ID_HEADER, String.valueOf(loggedInUserId));
    }
}
