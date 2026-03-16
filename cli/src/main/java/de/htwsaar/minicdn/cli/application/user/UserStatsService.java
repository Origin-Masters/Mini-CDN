package de.htwsaar.minicdn.cli.application.user;

import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.port.UserOperations;
import java.net.URI;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Fachlicher Service für User-spezifische Statistiken vom Router.
 *
 * <p>Die fachliche Logik validiert Eingaben und delegiert den eigentlichen
 * Remote-Zugriff an den Port. Das Ergebnis bleibt bewusst transportneutral.</p>
 */
public final class UserStatsService {

    private final UserOperations userOperations;
    private final URI routerBaseUrl;
    private final LongSupplier loggedInUserIdSupplier;

    /**
     * Erzeugt den Service.
     *
     * @param userOperations fachlicher Port für benutzerbezogene Remote-Aufrufe
     * @param routerBaseUrl Basis-URL des Routers
     * @param loggedInUserIdSupplier liefert die technische ID des aktuell eingeloggten Users
     */
    public UserStatsService(UserOperations userOperations, URI routerBaseUrl, LongSupplier loggedInUserIdSupplier) {
        this.userOperations = Objects.requireNonNull(userOperations, "userOperations");
        this.routerBaseUrl = Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        this.loggedInUserIdSupplier = Objects.requireNonNull(loggedInUserIdSupplier, "loggedInUserIdSupplier");
    }

    /**
     * Liefert Dateistatistiken für eine konkrete Datei des aktuellen Users.
     *
     * @param fileId technische Datei-ID
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult fileStatsForCurrentUser(long fileId) {
        if (fileId <= 0) {
            return CallResult.clientError("fileId must be greater than 0");
        }
        return withUserId(userId -> userOperations.fileStats(routerBaseUrl, userId, fileId));
    }

    /**
     * Listet Dateistatistiken des aktuellen Users mit Limitierung.
     *
     * @param limit maximale Anzahl an Einträgen
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult listUserFilesStats(int limit) {
        if (limit < 1) {
            return CallResult.clientError("limit must be >= 1");
        }
        return withUserId(userId -> userOperations.listFileStats(routerBaseUrl, userId, limit));
    }

    /**
     * Liefert aggregierte Statistiken des aktuellen Users für ein Zeitfenster.
     *
     * @param windowSec Zeitfenster in Sekunden
     * @return normiertes Ergebnis des Use-Cases
     */
    public CallResult overallStatsForCurrentUser(int windowSec) {
        if (windowSec < 1) {
            return CallResult.clientError("windowSec must be >= 1");
        }
        return withUserId(userId -> userOperations.overallStats(routerBaseUrl, userId, windowSec));
    }

    private CallResult withUserId(java.util.function.LongFunction<CallResult> operation) {
        try {
            long loggedInUserId = loggedInUserIdSupplier.getAsLong();
            if (loggedInUserId <= 0) {
                return CallResult.transportError("login required: missing user id");
            }
            return operation.apply(loggedInUserId);
        } catch (Exception ex) {
            return CallResult.transportError(ex.getMessage());
        }
    }
}
