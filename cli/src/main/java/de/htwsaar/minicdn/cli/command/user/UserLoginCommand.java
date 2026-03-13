package de.htwsaar.minicdn.cli.command.user;

import static de.htwsaar.minicdn.common.util.ExitCodes.REJECTED;
import static de.htwsaar.minicdn.common.util.ExitCodes.REQUEST_FAILED;
import static de.htwsaar.minicdn.common.util.ExitCodes.SUCCESS;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.UserResult;
import de.htwsaar.minicdn.cli.service.user.UserAuthService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Führt einen Login aus und speichert den User in der aktuellen CLI-Session.
 *
 * <p>Die Klasse bildet nur die CLI-Adapterlogik ab: Eingaben validieren,
 * den Auth-Service aufrufen, das Ergebnis auf Exit-Codes mappen und den
 * Session-Zustand aktualisieren.</p>
 */
@Command(name = "login", description = "Login as existing user", mixinStandardHelpOptions = true)
public final class UserLoginCommand implements Callable<Integer> {

    private final CliContext ctx;
    private final UserAuthService authService;

    @Option(names = "--name", required = true, paramLabel = "USERNAME", description = "Username")
    private String username;

    /**
     * Erzeugt den Login-Command mit gemeinsamem CLI-Kontext.
     *
     * @param ctx gemeinsamer CLI-Kontext
     */
    public UserLoginCommand(CliContext ctx) {
        this(
                ctx,
                new UserAuthService(
                        Objects.requireNonNull(ctx, "ctx").transportClient(),
                        ctx.defaultRequestTimeout(),
                        ctx.routerBaseUrl()));
    }

    /**
     * Interner Konstruktor für Tests und explizite Dependency Injection.
     *
     * @param ctx gemeinsamer CLI-Kontext
     * @param authService fachlicher Auth-Service
     */
    UserLoginCommand(CliContext ctx, UserAuthService authService) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.authService = Objects.requireNonNull(authService, "authService");
    }

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
     * Normalisiert und validiert den Usernamen.
     *
     * @param rawUsername roher Benutzername aus der CLI
     * @return getrimmter Benutzername
     * @throws IllegalArgumentException falls der Benutzername leer ist
     */
    String normalizeUsername(String rawUsername) {
        String value = Objects.toString(rawUsername, "").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Username darf nicht leer sein");
        }
        return value;
    }

    /**
     * Speichert den eingeloggten User im Session-State.
     *
     * @param user erfolgreich eingeloggter User
     */
    void rememberLoggedInUser(UserResult user) {
        Objects.requireNonNull(user, "user");
        ctx.sessionState().rememberLoggedInUser(user.id(), user.name(), user.role());
    }

    /**
     * Gibt die Erfolgsmeldung für den Login aus.
     *
     * @param user erfolgreich eingeloggter User
     */
    void printSuccess(UserResult user) {
        ConsoleUtils.info(
                ctx.out(), "[AUTH] Eingeloggt als %s (id=%d, role=%s)", user.name(), user.id(), roleName(user.role()));
    }

    /**
     * Mappt die Rollen-ID auf einen lesbaren Rollennamen.
     *
     * @param role Rollen-ID
     * @return lesbarer Rollenname
     */
    String roleName(int role) {
        return role == 1 ? "ADMIN" : "USER";
    }

    /**
     * Gibt einen technischen Fehler einheitlich aus.
     *
     * @param message Fehlermeldung
     * @return Exit-Code für technische Fehler
     */
    int requestFailed(String message) {
        ConsoleUtils.error(ctx.err(), "[AUTH] %s", Objects.toString(message, "technischer Fehler"));
        return REQUEST_FAILED.code();
    }

    /**
     * Gibt eine fachliche Ablehnung oder einen Validierungsfehler einheitlich aus.
     *
     * @param message Fehlermeldung
     * @return Exit-Code für fachliche Ablehnung
     */
    int rejected(String message) {
        ConsoleUtils.error(ctx.err(), "[AUTH] %s", Objects.toString(message, "Login abgelehnt"));
        return REJECTED.code();
    }
}
