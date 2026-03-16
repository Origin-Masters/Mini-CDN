package de.htwsaar.minicdn.cli.application.user;

import de.htwsaar.minicdn.cli.domain.model.LoginResult;
import de.htwsaar.minicdn.cli.domain.port.UserOperations;
import java.net.URI;
import java.util.Objects;

/**
 * Fachlicher Service für den User-Login über den Router.
 *
 * <p>Der Service delegiert den Remote-Aufruf vollständig an einen Port und hält
 * selbst nur die fachliche Sicht auf den Use-Case.</p>
 */
public final class UserAuthService {

    private final UserOperations userOperations;
    private final URI routerBaseUrl;

    /**
     * Erzeugt den Login-Service.
     *
     * @param userOperations fachlicher Port für benutzerbezogene Remote-Aufrufe
     * @param routerBaseUrl Basis-URL des Routers
     */
    public UserAuthService(UserOperations userOperations, URI routerBaseUrl) {
        this.userOperations = Objects.requireNonNull(userOperations, "userOperations");
        this.routerBaseUrl = Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
    }

    /**
     * Führt einen Login über den Router aus.
     *
     * @param username Benutzername
     * @return normiertes Login-Ergebnis
     */
    public LoginResult login(String username) {
        return userOperations.login(routerBaseUrl, username);
    }
}
