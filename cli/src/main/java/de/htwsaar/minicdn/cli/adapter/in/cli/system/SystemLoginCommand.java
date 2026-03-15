package de.htwsaar.minicdn.cli.adapter.in.cli.system;

import static de.htwsaar.minicdn.common.util.ExitCodes.REJECTED;
import static de.htwsaar.minicdn.common.util.ExitCodes.REQUEST_FAILED;
import static de.htwsaar.minicdn.common.util.ExitCodes.SUCCESS;

import de.htwsaar.minicdn.cli.adapter.in.cli.support.ConsoleUtils;
import de.htwsaar.minicdn.cli.application.context.CliContext;
import de.htwsaar.minicdn.cli.application.user.UserAuthService;
import de.htwsaar.minicdn.cli.domain.model.UserResult;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Führt einen Login über die Router-Auth-API aus und speichert den Benutzer in der aktuellen CLI-Session.
 *
 * <p>Die Klasse übernimmt ausschließlich die CLI-Adapterlogik: Eingaben validieren,
 * den fachlichen Login-Service aufrufen, das Ergebnis auf Exit-Codes abbilden und
 * den Session-Zustand aktualisieren.</p>
 */
@Command(name = "login", description = "Login as existing user", mixinStandardHelpOptions = true)
public final class SystemLoginCommand implements Callable<Integer> {

    private final CliContext ctx;
    private final UserAuthService authService;

    @Option(names = "--name", required = true, paramLabel = "USERNAME", description = "Username")
    private String username;

    /**
     * Erzeugt das Login-Kommando mit Standardverdrahtung aus dem CLI-Kontext.
     *
     * @param ctx gemeinsamer CLI-Kontext
     */
    public SystemLoginCommand(CliContext ctx) {
        this(
                ctx,
                new UserAuthService(
                        Objects.requireNonNull(ctx, "ctx").transportClient(),
                        ctx.defaultRequestTimeout(),
                        ctx.routerBaseUrl()));
    }

    /**
     * Interner Konstruktor für Tests und explizite Verdrahtung.
     *
     * @param ctx gemeinsamer CLI-Kontext
     * @param authService fachlicher Login-Service
     */
    SystemLoginCommand(CliContext ctx, UserAuthService authService) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.authService = Objects.requireNonNull(authService, "authService");
    }

    /**
     * Führt den Login aus, speichert bei Erfolg den Benutzer in der Session und gibt den passenden Exit-Code zurück.
     *
     * @return Exit-Code gemäß Login-Ergebnis
     */
    @Override
    public Integer call() {
        try {
            String normalizedUsername = normalizeUsername(username);
            UserAuthService.LoginResult result = authService.login(normalizedUsername);

            if (result.error() != null) {
                return requestFailed("Login fehlgeschlagen (IO): " + result.error());
            }

            Integer statusCode = result.statusCode();
            if (!result.hasSuccessfulStatus()) {
                return rejected("Login fehlgeschlagen: HTTP " + statusCode);
            }

            UserResult user = Objects.requireNonNull(result.user(), "user");
            rememberLoggedInUser(user);
            printSuccess(user);
            return SUCCESS.code();

        } catch (IllegalArgumentException ex) {
            return rejected(ex.getMessage());
        } catch (Exception ex) {
            return requestFailed("Login fehlgeschlagen: " + ex.getMessage());
        }
    }

    /**
     * Validiert und normalisiert den übergebenen Benutzernamen.
     *
     * @param rawUsername roher CLI-Wert
     * @return getrimmter Benutzername
     */
    String normalizeUsername(String rawUsername) {
        String value = Objects.toString(rawUsername, "").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Username darf nicht leer sein");
        }
        return value;
    }

    /**
     * Schreibt den eingeloggten Benutzer in den Session-State.
     *
     * @param user erfolgreich eingeloggter Benutzer
     */
    void rememberLoggedInUser(UserResult user) {
        Objects.requireNonNull(user, "user");
        ctx.sessionState().rememberLoggedInUser(user.id(), user.name(), user.role());
    }

    /**
     * Gibt die Erfolgsmeldung für einen erfolgreichen Login aus.
     *
     * @param user erfolgreich eingeloggter Benutzer
     */
    void printSuccess(UserResult user) {
        ConsoleUtils.info(
                ctx.out(), "[AUTH] Eingeloggt als %s (id=%d, role=%s)", user.name(), user.id(), roleName(user.role()));
    }

    /**
     * Übersetzt die numerische Rolle in eine kurze Ausgabebezeichnung.
     *
     * @param role numerische Rollen-ID
     * @return Rollenname für die CLI-Ausgabe
     */
    String roleName(int role) {
        return role == 1 ? "ADMIN" : "USER";
    }

    /**
     * Gibt einen technischen Fehler aus und liefert den passenden Exit-Code.
     *
     * @param message Fehlermeldung
     * @return Exit-Code für technische Fehler
     */
    int requestFailed(String message) {
        ConsoleUtils.error(ctx.err(), "[AUTH] %s", Objects.toString(message, "technischer Fehler"));
        return REQUEST_FAILED.code();
    }

    /**
     * Gibt eine fachlich abgelehnte Login-Meldung aus und liefert den passenden Exit-Code.
     *
     * @param message Fehlermeldung
     * @return Exit-Code für abgelehnte Anfragen
     */
    int rejected(String message) {
        ConsoleUtils.error(ctx.err(), "[AUTH] %s", Objects.toString(message, "Login abgelehnt"));
        return REJECTED.code();
    }
}
