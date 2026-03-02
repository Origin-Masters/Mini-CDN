package de.htwsaar.minicdn.cli.command.admin;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.HttpCallResult;
import de.htwsaar.minicdn.cli.service.admin.AdminUserService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Admin-Command zur Verwaltung von System-Usern über die Admin-API.
 *
 * <p>Hinweis: Dieser Command selbst hat keine Default-Aktion und zeigt nur Usage an.
 * Für die eigentliche Ausführung {@code user add}, {@code user list} oder {@code user delete} verwenden.</p>
 */
@Command(
        name = "user",
        description = "Manage system users",
        mixinStandardHelpOptions = true,
        footerHeading = "%nBeispiele:%n",
        footer = {
            "  admin user add --name alice --role 1",
            "  admin user list --role USER --page 1 --size 20",
            "  admin user remove --id 42 --force"
        },
        subcommands = {
            AdminUserMgmtCommand.AddCommand.class,
            AdminUserMgmtCommand.ListCommand.class,
            AdminUserMgmtCommand.DeleteCommand.class
        })
public class AdminUserMgmtCommand implements Runnable {

    final CliContext ctx;
    final AdminUserService service;

    @Spec
    CommandSpec spec;

    private static final Map<Integer, String> VALID_ROLES = Map.of(0, "USER", 1, "ADMIN");

    public AdminUserMgmtCommand(CliContext ctx) {
        this.ctx = ctx;
        this.service = new AdminUserService(
                ctx.transportClient(), ctx.defaultRequestTimeout(), ctx.routerBaseUrl(), ctx.adminToken());
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    /**
     * Command zum Anlegen eines neuen Users. Führt einen POST-Request gegen die Admin-API aus.
     *
     * <p>Exit-Codes:
     * - 0: OK
     * - 2: HTTP-Fehlerstatus (non-2xx) oder ungültige Eingabe
     * - 1: Exception/Netzwerkfehler</p>
     */
    @Command(
            name = "add",
            description = "Create a new user",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  admin user add --name alice --role 1", "  admin user add --name bob --role 0"})
    public static class AddCommand implements Runnable {

        @ParentCommand
        private AdminUserMgmtCommand parent;

        @Option(names = "--name", required = true, description = "User name")
        String name;

        @Option(names = "--role", required = true, description = "User role id (e.g. 0=USER, 1=ADMIN)")
        int role;

        @Override
        public void run() {
            if (!parent.VALID_ROLES.containsKey(role)) {
                ConsoleUtils.error(
                        parent.ctx.err(),
                        "[ADMIN] Invalid role: %d. Valid roles: %s",
                        role,
                        parent.VALID_ROLES.values());
                return;
            }

            HttpCallResult res = parent.service.addUser(name, role);
            if (res.error() != null) {
                ConsoleUtils.error(parent.ctx.err(), "[ADMIN] User add failed: %s", res.error());
                return;
            }
            if (!res.is2xx()) {
                ConsoleUtils.error(
                        parent.ctx.err(), "[ADMIN] User add failed HTTP %d body=%s", res.statusCode(), res.body());
                return;
            }
            ConsoleUtils.info(parent.ctx.out(), "[ADMIN] User added: %s", res.body());
        }
    }

    /**
     * Command zum Auflisten aller User. Führt einen GET-Request gegen die Admin-API aus und gibt die Daten formatiert aus.
     *
     * <p>Exit-Codes:
     * - 0: OK
     * - 2: HTTP-Fehlerstatus (non-2xx)
     * - 1: Exception/Netzwerkfehler</p>
     */
    @Command(
            name = "list",
            description = "List users in the system",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  admin user list", "  admin user list --role 1 --page 1 --size 50"})
    public static class ListCommand implements Runnable {

        @ParentCommand
        private AdminUserMgmtCommand parent;

        @Override
        public void run() {
            HttpCallResult res = parent.service.listUsersRaw();
            if (res.error() != null) {
                ConsoleUtils.error(parent.ctx.err(), "[ADMIN] User list failed: %s", res.error());
                return;
            }
            if (!res.is2xx()) {
                ConsoleUtils.error(
                        parent.ctx.err(), "[ADMIN] User list failed HTTP %d body=%s", res.statusCode(), res.body());
                return;
            }

            // Erwartet wird ein JSON-Array von UserResult-Objekten. Beispiel: [{"id":1,"name":"alice","role":1}, ...]
            String body = res.body();
            try {
                var users = parent.service.parseUsers(body);
                if (users.isEmpty()) {
                    parent.ctx.out().println("[ADMIN] Users: (none)");
                } else {
                    parent.ctx.out().println("[ADMIN] Users:");
                    for (var u : users) {
                        parent.ctx.out().printf("  - id=%d name=%s role=%d%n", u.id(), u.name(), u.role());
                    }
                }
                parent.ctx.out().flush();
            } catch (Exception e) {
                ConsoleUtils.error(
                        parent.ctx.err(),
                        "[ADMIN] User list: failed to parse JSON (%s), raw body:%n%s",
                        e.getMessage(),
                        body);
            }
        }
    }

    /**
     * Command zum Löschen eines Users anhand der ID. Führt einen DELETE-Request gegen die Admin-API aus.
     *
     * <p>Exit-Codes:
     * - 0: OK
     * - 2: HTTP-Fehlerstatus (non-2xx) oder ungültige Eingabe
     * - 1: Exception/Netzwerkfehler</p>
     */
    @Command(
            name = "delete",
            description = "delete a user by id",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  admin user delete --id 42 --force"})
    public static class DeleteCommand implements Runnable {

        @ParentCommand
        private AdminUserMgmtCommand parent;

        @Option(names = "--id", required = true, description = "User id")
        long id;

        @Override
        public void run() {
            HttpCallResult res = parent.service.deleteUser(id);
            if (res.error() != null) {
                ConsoleUtils.error(parent.ctx.err(), "[ADMIN] User delete failed: %s", res.error());
                return;
            }
            Integer sc = res.statusCode();
            if (sc != null && sc == 204) {
                ConsoleUtils.info(parent.ctx.out(), "[ADMIN] User %d deleted", id);
            } else if (sc != null && sc == 404) {
                ConsoleUtils.error(parent.ctx.err(), "[ADMIN] User %d not found", id);
            } else {
                ConsoleUtils.error(parent.ctx.err(), "[ADMIN] User delete HTTP %d body=%s", sc, res.body());
            }
        }
    }
}
