package de.htwsaar.minicdn.cli.command.system;

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
 */
@Command(name = "login", description = "Login as existing user", mixinStandardHelpOptions = true)
public final class SystemLoginCommand implements Callable<Integer> {

    private final CliContext ctx;
    private final UserAuthService authService;

    @Option(names = "--name", required = true, paramLabel = "USERNAME", description = "Username")
    private String username;

    public SystemLoginCommand(CliContext ctx) {
        this(
                ctx,
                new UserAuthService(
                        Objects.requireNonNull(ctx, "ctx").transportClient(),
                        ctx.defaultRequestTimeout(),
                        ctx.routerBaseUrl()));
    }

    SystemLoginCommand(CliContext ctx, UserAuthService authService) {
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

    String normalizeUsername(String rawUsername) {
        String value = Objects.toString(rawUsername, "").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Username darf nicht leer sein");
        }
        return value;
    }

    void rememberLoggedInUser(UserResult user) {
        Objects.requireNonNull(user, "user");
        ctx.sessionState().rememberLoggedInUser(user.id(), user.name(), user.role());
    }

    void printSuccess(UserResult user) {
        ConsoleUtils.info(
                ctx.out(), "[AUTH] Eingeloggt als %s (id=%d, role=%s)", user.name(), user.id(), roleName(user.role()));
    }

    String roleName(int role) {
        return role == 1 ? "ADMIN" : "USER";
    }

    int requestFailed(String message) {
        ConsoleUtils.error(ctx.err(), "[AUTH] %s", Objects.toString(message, "technischer Fehler"));
        return REQUEST_FAILED.code();
    }

    int rejected(String message) {
        ConsoleUtils.error(ctx.err(), "[AUTH] %s", Objects.toString(message, "Login abgelehnt"));
        return REJECTED.code();
    }
}
