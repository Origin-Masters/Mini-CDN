package de.htwsaar.minicdn.cli.command.admin;

import static de.htwsaar.minicdn.common.util.ExitCodes.REJECTED;
import static de.htwsaar.minicdn.common.util.ExitCodes.REQUEST_FAILED;
import static de.htwsaar.minicdn.common.util.ExitCodes.SUCCESS;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.service.admin.AdminUserService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Stellt Admin-Befehle zur Verwaltung von System-Usern über die Router-Admin-API bereit.
 *
 * <p>Die Klasse bildet die CLI-Adapter-Schicht für Benutzerverwaltung. Sie validiert
 * Eingaben, delegiert die eigentlichen Remote-Aufrufe an den {@link AdminUserService}
 * und übernimmt ausschließlich Konsolenausgabe sowie Exit-Code-Mapping.</p>
 */
@Command(
        name = "user",
        description = "Manage system users",
        mixinStandardHelpOptions = true,
        footerHeading = "%nBeispiele:%n",
        footer = {"  admin user add --name alice --role 1", "  admin user list", "  admin user delete --id 42"},
        subcommands = {
            AdminUserMgmtCommand.AddCommand.class,
            AdminUserMgmtCommand.ListCommand.class,
            AdminUserMgmtCommand.DeleteCommand.class
        })
public final class AdminUserMgmtCommand implements Runnable {

    /**
     * Technischer Fallback für nicht vorhandene Login-Informationen.
     */
    private static final long FALLBACK_USER_ID = -1L;

    /**
     * Zulässige Rollen für Benutzerverwaltung.
     */
    private static final Map<Integer, String> VALID_ROLES = Map.of(
            0, "USER",
            1, "ADMIN");

    /**
     * Gemeinsamer CLI-Kontext.
     */
    private final CliContext ctx;

    /**
     * Fachlicher Service für User-Administration.
     */
    private final AdminUserService service;

    @Spec
    private CommandSpec spec;

    /**
     * Erzeugt den Command mit gemeinsamem CLI-Kontext.
     *
     * @param ctx gemeinsamer CLI-Kontext
     */
    public AdminUserMgmtCommand(CliContext ctx) {
        this(
                ctx,
                new AdminUserService(
                        Objects.requireNonNull(ctx, "ctx").transportClient(),
                        ctx.defaultRequestTimeout(),
                        ctx.routerBaseUrl(),
                        ctx.adminToken(),
                        resolveLoggedInUserId(ctx)));
    }

    /**
     * Interner Konstruktor für Tests und explizite Dependency Injection.
     *
     * @param ctx gemeinsamer CLI-Kontext
     * @param service fachlicher Service für User-Administration
     */
    AdminUserMgmtCommand(CliContext ctx, AdminUserService service) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    /**
     * Liefert den gemeinsamen CLI-Kontext.
     *
     * @return CLI-Kontext
     */
    CliContext ctx() {
        return ctx;
    }

    /**
     * Liefert den Service für User-Administration.
     *
     * @return User-Service
     */
    AdminUserService service() {
        return service;
    }

    /**
     * Prüft, ob die angegebene Rollen-ID gültig ist.
     *
     * @param role Rollen-ID
     * @return {@code true}, wenn die Rolle zulässig ist
     */
    boolean isValidRole(int role) {
        return VALID_ROLES.containsKey(role);
    }

    /**
     * Liefert die lesbaren Rollennamen für Fehlermeldungen.
     *
     * @return Liste gültiger Rollennamen
     */
    List<String> validRoleNames() {
        return VALID_ROLES.values().stream().toList();
    }

    /**
     * Gibt einen technischen Fehler einheitlich aus.
     *
     * @param message Fehlermeldung
     * @return Exit-Code für technische Fehler
     */
    int requestFailed(String message) {
        ConsoleUtils.error(ctx.err(), "[ADMIN] %s", Objects.toString(message, "unknown error"));
        return REQUEST_FAILED.code();
    }

    /**
     * Gibt eine fachliche Ablehnung oder Validierungsfehlermeldung einheitlich aus.
     *
     * @param message Fehlermeldung
     * @return Exit-Code für fachliche Ablehnung
     */
    int rejected(String message) {
        ConsoleUtils.error(ctx.err(), "[ADMIN] %s", Objects.toString(message, "request rejected"));
        return REJECTED.code();
    }

    /**
     * Bewertet ein HTTP-Ergebnis zentral und normiert die Fehlerbehandlung.
     *
     * @param operation Name der Operation
     * @param result Ergebnis des Service-Aufrufs
     * @return normiertes Ergebnisobjekt
     */
    CallOutcome evaluate(String operation, CallResult result) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(result, "result");

        if (result.error() != null) {
            return CallOutcome.failure(requestFailed(String.format("%s failed: %s", operation, result.error())));
        }

        Integer statusCode = result.statusCode();
        if (statusCode == null) {
            return CallOutcome.failure(requestFailed(String.format("%s failed: missing HTTP status code", operation)));
        }

        if (!result.is2xx()) {
            return CallOutcome.failure(rejected(String.format(
                    "%s failed HTTP %d body=%s", operation, statusCode, Objects.toString(result.body(), ""))));
        }

        return CallOutcome.success(statusCode, result.body());
    }

    /**
     * Ermittelt die aktuell eingeloggte User-ID oder einen technischen Fallback.
     *
     * @param ctx CLI-Kontext
     * @return User-ID oder {@code -1L}, wenn keine Session-User-ID gesetzt ist
     */
    private static long resolveLoggedInUserId(CliContext ctx) {
        Long loggedInUserId = Objects.requireNonNull(ctx, "ctx").sessionState().loggedInUserId();
        return loggedInUserId == null ? FALLBACK_USER_ID : loggedInUserId;
    }

    /**
     * Normiertes Ergebnis eines bereits ausgewerteten HTTP-Aufrufs.
     *
     * @param successful Kennzeichen für Erfolg
     * @param exitCode zurückzugebender Exit-Code
     * @param statusCode HTTP-Statuscode bei Erfolg
     * @param body HTTP-Body bei Erfolg
     */
    record CallOutcome(boolean successful, int exitCode, int statusCode, String body) {

        /**
         * Erzeugt ein Erfolgsresultat.
         *
         * @param statusCode HTTP-Statuscode
         * @param body HTTP-Body
         * @return Erfolgsobjekt
         */
        static CallOutcome success(int statusCode, String body) {
            return new CallOutcome(true, SUCCESS.code(), statusCode, body);
        }

        /**
         * Erzeugt ein Fehlerresultat.
         *
         * @param exitCode Exit-Code
         * @return Fehlerobjekt
         */
        static CallOutcome failure(int exitCode) {
            return new CallOutcome(false, exitCode, -1, null);
        }
    }

    /**
     * Command zum Anlegen eines neuen Users.
     *
     * <p>Der Command validiert die Rollen-ID und delegiert den eigentlichen POST-Aufruf
     * an den {@link AdminUserService}.</p>
     */
    @Command(
            name = "add",
            description = "Create a new user",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  admin user add --name alice --role 1", "  admin user add --name bob --role 0"})
    public static final class AddCommand implements Callable<Integer> {

        @ParentCommand
        private AdminUserMgmtCommand parent;

        /**
         * Anzeigename des anzulegenden Users.
         */
        @Option(names = "--name", required = true, paramLabel = "NAME", description = "User name")
        private String name;

        /**
         * Rollen-ID des neuen Users.
         */
        @Option(names = "--role", required = true, paramLabel = "ROLE", description = "User role id (0=USER, 1=ADMIN)")
        private int role;

        @Override
        public Integer call() {
            String cleanName = Objects.toString(name, "").trim();
            if (cleanName.isBlank()) {
                return parent.rejected("user name must not be blank");
            }

            if (!parent.isValidRole(role)) {
                return parent.rejected(
                        String.format("invalid role: %d. Valid roles: %s", role, parent.validRoleNames()));
            }

            CallOutcome outcome = parent.evaluate("User add", parent.service().addUser(cleanName, role));
            if (!outcome.successful()) {
                return outcome.exitCode();
            }

            ConsoleUtils.info(
                    parent.ctx().out(),
                    "[ADMIN] User added HTTP %d body=%s",
                    outcome.statusCode(),
                    Objects.toString(outcome.body(), ""));
            return SUCCESS.code();
        }
    }

    /**
     * Command zum Auflisten aller User.
     *
     * <p>Der Command ruft die User-Liste als JSON ab und formatiert die geparsten
     * {@code UserResult}-Einträge menschenlesbar für die CLI.</p>
     */
    @Command(
            name = "list",
            description = "List users in the system",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  admin user list"})
    public static final class ListCommand implements Callable<Integer> {

        @ParentCommand
        private AdminUserMgmtCommand parent;

        @Override
        public Integer call() {
            CallOutcome outcome = parent.evaluate("User list", parent.service().listUsersRaw());
            if (!outcome.successful()) {
                return outcome.exitCode();
            }

            try {
                var users = parent.service().parseUsers(outcome.body());

                if (users.isEmpty()) {
                    parent.ctx().out().println("[ADMIN] Users: (none)");
                    parent.ctx().out().flush();
                    return SUCCESS.code();
                }

                parent.ctx().out().println("[ADMIN] Users:");
                for (var user : users) {
                    parent.ctx().out().printf("  - id=%d name=%s role=%d%n", user.id(), user.name(), user.role());
                }
                parent.ctx().out().flush();
                return SUCCESS.code();

            } catch (Exception ex) {
                return parent.requestFailed(String.format(
                        "User list: failed to parse JSON (%s), raw body:%n%s",
                        ex.getMessage(), Objects.toString(outcome.body(), "")));
            }
        }
    }

    /**
     * Command zum Löschen eines Users anhand der ID.
     *
     * <p>Der Command führt einen DELETE-Request gegen die Router-Admin-API aus
     * und behandelt fachliche Sonderfälle wie HTTP 404 explizit.</p>
     */
    @Command(
            name = "delete",
            description = "Delete a user by id",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  admin user delete --id 42"})
    public static final class DeleteCommand implements Callable<Integer> {

        @ParentCommand
        private AdminUserMgmtCommand parent;

        /**
         * Technische Benutzer-ID.
         */
        @Option(names = "--id", required = true, paramLabel = "ID", description = "User id")
        private long id;

        @Override
        public Integer call() {
            if (id <= 0) {
                return parent.rejected("user id must be greater than 0");
            }

            CallResult result = parent.service().deleteUser(id);

            if (result.error() != null) {
                return parent.requestFailed(String.format("User delete failed: %s", result.error()));
            }

            Integer statusCode = result.statusCode();
            if (Integer.valueOf(204).equals(statusCode)) {
                ConsoleUtils.info(parent.ctx().out(), "[ADMIN] User %d deleted", id);
                return SUCCESS.code();
            }

            if (Integer.valueOf(404).equals(statusCode)) {
                return parent.rejected(String.format("User %d not found", id));
            }

            return parent.rejected(String.format(
                    "User delete failed HTTP %s body=%s", statusCode, Objects.toString(result.body(), "")));
        }
    }
}
