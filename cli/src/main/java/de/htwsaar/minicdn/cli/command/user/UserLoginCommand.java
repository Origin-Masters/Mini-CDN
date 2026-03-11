package de.htwsaar.minicdn.cli.command.user;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.service.user.UserAuthService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Führt einen Login aus und speichert den User in der aktuellen CLI-Session.
 */
@Command(name = "login", description = "Login as existing user", mixinStandardHelpOptions = true)
public final class UserLoginCommand implements Runnable {

    private final CliContext ctx;
    private final UserAuthService authService;

    @Option(names = "--name", required = true, description = "Username")
    private String username;

    public UserLoginCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.authService = new UserAuthService(ctx.transportClient(), ctx.defaultRequestTimeout(), ctx.routerBaseUrl());
    }

    @Override
    public void run() {
        var result = authService.login(username);

        if (result.error() != null) {
            ConsoleUtils.error(ctx.err(), "[AUTH] Login fehlgeschlagen (IO): %s", result.error());
            return;
        }

        if (result.statusCode() == null || result.statusCode() >= 400) {
            ConsoleUtils.error(ctx.err(), "[AUTH] Login fehlgeschlagen: HTTP %s", result.statusCode());
            return;
        }

        var user = result.user();
        ctx.sessionState().rememberLoggedInUser(user.id(), user.name(), user.role());

        String roleName = user.role() == 1 ? "ADMIN" : "USER";
        ConsoleUtils.info(ctx.out(), "[AUTH] Eingeloggt als %s (id=%d, role=%s)", user.name(), user.id(), roleName);
    }
}
