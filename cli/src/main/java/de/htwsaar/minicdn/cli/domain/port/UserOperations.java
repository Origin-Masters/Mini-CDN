package de.htwsaar.minicdn.cli.domain.port;

import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.model.LoginResult;
import java.net.URI;

/**
 * Fachlicher Port für benutzerbezogene Remote-Operationen der CLI.
 *
 * <p>Die Signaturen beschreiben nur fachliche Anwendungsfälle. Details der
 * konkreten Transportbindung bleiben in den Outbound-Adaptern. Die Rückgabe
 * erfolgt als normiertes fachliches Aufrufergebnis.</p>
 */
public interface UserOperations {

    /**
     * Führt einen Login über den Router aus.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param username Benutzername
     * @return normiertes Login-Ergebnis
     */
    LoginResult login(URI routerBaseUrl, String username);

    /**
     * Lädt Dateistatistiken eines Benutzers für eine konkrete Datei.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param loggedInUserId technische Benutzer-ID
     * @param fileId technische Datei-ID
     * @return normiertes Ergebnis des Use-Cases
     */
    CallResult fileStats(URI routerBaseUrl, long loggedInUserId, long fileId);

    /**
     * Lädt eine begrenzte Liste von Dateistatistiken eines Benutzers.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param loggedInUserId technische Benutzer-ID
     * @param limit maximale Anzahl an Einträgen
     * @return normiertes Ergebnis des Use-Cases
     */
    CallResult listFileStats(URI routerBaseUrl, long loggedInUserId, int limit);

    /**
     * Lädt aggregierte Benutzerstatistiken für ein Zeitfenster.
     *
     * @param routerBaseUrl Basis-URL des Routers
     * @param loggedInUserId technische Benutzer-ID
     * @param windowSec Zeitfenster in Sekunden
     * @return normiertes Ergebnis des Use-Cases
     */
    CallResult overallStats(URI routerBaseUrl, long loggedInUserId, int windowSec);
}
