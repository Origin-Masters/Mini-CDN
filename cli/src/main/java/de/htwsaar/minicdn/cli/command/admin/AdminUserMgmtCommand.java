package de.htwsaar.minicdn.cli.command.admin;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.service.admin.AdminUserService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import de.htwsaar.minicdn.cli.util.DatabaseUtils;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Admin-Command zur Benutzerverwaltung.
 *
 * <p>Ohne Subcommand wird die Usage angezeigt.
 */
@Command(
        name = "user",
        description = "Manage system users",
        mixinStandardHelpOptions = true,
        footerHeading = "%nBeispiele:%n",
        footer = {
                "  minicdn admin user add --name alice --role ADMIN",
                "  minicdn admin user list --role USER --page 1 --size 20",
                "  minicdn admin user remove --id 42 --force"
        },
        subcommands = {
                AdminUserMgmtCommand.AdminUserAddCommand.class,
                AdminUserMgmtCommand.AdminUserRemoveCommand.class,
                AdminUserMgmtCommand.AdminUserListCommand.class
        })
public final class AdminUserMgmtCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    /**
     * Konstruktor für Constructor Injection via {@code ContextFactory}.
     *
     * @param ctx CLI-Kontext (Output, HTTP-Client, Timeouts, ...)
     */
    public AdminUserMgmtCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    @Command(
            name = "add",
            description = "Create a new user",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                    "  minicdn admin user add --name alice --role ADMIN",
                    "  minicdn admin user add --name bob --role USER"
            })
    public static final class AdminUserAddCommand implements Runnable {

        public static final Map<String, Integer> ROLE_MAP = Map.of("ADMIN", 1, "USER", 2);

        @ParentCommand
        private AdminUserMgmtCommand parent;

        @Option(names = "--name", required = true, paramLabel = "NAME", description = "User name")
        private String name;

        @Option(names = "--role", required = true, paramLabel = "ROLE", description = "Role, e.g. ADMIN or USER")
        private String role;

        @Override
        public void run() {
            String jdbcUrl = DatabaseUtils.resolveJdbcUrl();
            int roleId = AdminUserService.parseRole(role);

            try (AdminUserService svc = new AdminUserService(jdbcUrl)) {
                int id = svc.addUser(name, roleId);
                if (id > 0) {
                    ConsoleUtils.info(
                            parent.ctx.out(), "[ADMIN] User added successfully: id=%d name=%s role=%d", id, name, roleId);
                } else {
                    ConsoleUtils.error(parent.ctx.err(), "[ADMIN] Failed to add user: name=%s role=%d", name, roleId);
                }
            } catch (SQLException e) {
                ConsoleUtils.error(parent.ctx.err(), "[ADMIN] Database error: %s", e.getMessage());
            }
        }

        // Hilfsfunktion zum Parsen der Rolle aus String, unterstützt sowohl benannte Rollen als auch numerische IDs

    }

    @Command(
            name = "list",
            description = "List users in the system",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  minicdn admin user list", "  minicdn admin user list --role ADMIN --page 1 --size 50"})
    public static final class AdminUserListCommand implements Runnable {

        @ParentCommand
        private AdminUserMgmtCommand parent;

        @Option(names = "--role", paramLabel = "ROLE", description = "Filter by role, e.g. ADMIN or USER")
        private String role;

        @Option(names = "--page", description = "Page number", defaultValue = "1")
        private int page;

        @Option(names = "--size", description = "Page size", defaultValue = "20")
        private int size;

        @Override
        public void run() {
            if (page <= 0 || size <= 0) {
                ConsoleUtils.error(parent.ctx.err(), "[ADMIN] Page or Size must be greater than 0");
                return;
            }
            String jdbcUrl = DatabaseUtils.resolveJdbcUrl();

            try (AdminUserService svc = new AdminUserService(jdbcUrl)) {
                Object data = svc.listUsers(role, page, size);

                if (data instanceof Collection<?> collection) {
                    if (collection.isEmpty()) {
                        ConsoleUtils.info(parent.ctx.out(),
                                "[ADMIN] No users found (role=%s, page=%d, size=%d)", role == null ? "*" : role, page, size);
                        return;
                    }

                    ConsoleUtils.info(parent.ctx.out(),
                            "[ADMIN] Users (role=%s, page=%d, size=%d)", role == null ? "*" : role, page, size);
                    int index = 1;
                    for (Object entry : collection) {
                        parent.ctx.out().printf("%d) %s%n", index++, entry);
                    }
                    parent.ctx.out().flush();
                } else {
                    ConsoleUtils.info(parent.ctx.out(), "[ADMIN] %s", data);
                }
            } catch (SQLException ex) {
                ConsoleUtils.error(parent.ctx.err(), "[ADMIN] Database error: %s", ex.getMessage());
            }
        }
    }


    @Command(
            name = "remove",
            description = "Remove an existing user from the system",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                    "  minicdn admin user remove --id 42 --force"
            })
    public static final class AdminUserRemoveCommand implements Runnable {

        @ParentCommand
        private AdminUserMgmtCommand parent;

        @Option(names = "--id", description = "User ID to remove")
        private Long userId;


        @Option(names = "--force", description = "Do not ask for confirmation")
        private boolean force;


        @Override
        public void run() {
            String target = "id=" + userId;
            String jdbcUrl = DatabaseUtils.resolveJdbcUrl();

            try (AdminUserService svc = new AdminUserService(jdbcUrl)) {
                Map<String, Object> existing = svc.findUser(userId);
                if (existing == null) {
                    ConsoleUtils.error(parent.ctx.err(), "[ADMIN] User not found: %s", target);
                    return;
                }

                if (!force && !confirmRemoval(target, existing)) {
                    ConsoleUtils.info(parent.ctx.out(), "[ADMIN] Aborted removal of %s", target);
                    parent.ctx.out().flush();
                    return;
                }

                if (svc.removeUser(userId)) {
                    ConsoleUtils.info(parent.ctx.out(), "[ADMIN] Removed user: id=%s name=%s role=%s",
                            existing.get("id"), existing.get("name"), existing.get("role"));
                } else {
                    ConsoleUtils.error(parent.ctx.err(), "[ADMIN] Failed to remove user: %s", target);
                }
            } catch (SQLException ex) {
                ConsoleUtils.error(parent.ctx.err(), "[ADMIN] Database error: %s", ex.getMessage());
            }
        }

        private boolean confirmRemoval(String target, Map<String, Object> user) {
            parent.ctx.out().printf("[ADMIN] Removing user: %s (id=%s name=%s role=%s)%n",
                    target, user.get("id"), user.get("name"), user.get("role"));
            parent.ctx.out().print("[ADMIN] Are you sure? (y/N): ");
            parent.ctx.out().flush();

            String response = parent.ctx.in().nextLine().trim().toLowerCase();
            return response.equals("y") || response.equals("yes");
        }
    }
}
