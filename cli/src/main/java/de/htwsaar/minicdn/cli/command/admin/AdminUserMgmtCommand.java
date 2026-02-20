package de.htwsaar.minicdn.cli.command.admin;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.service.admin.AdminUserService;
import de.htwsaar.minicdn.cli.util.DatabaseUtils;
import java.sql.SQLException;
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

        private static final Map<String, Integer> ROLE_MAP = Map.of("ADMIN", 1, "USER", 2);

        @ParentCommand
        private AdminUserMgmtCommand parent;

        @Option(names = "--name", required = true, paramLabel = "NAME", description = "User name")
        private String name;

        @Option(names = "--role", required = true, paramLabel = "ROLE", description = "Role, e.g. ADMIN or USER")
        private String role;

        @Override
        public void run() {
            String jdbcUrl = DatabaseUtils.resolveJdbcUrl();
            int roleId = parseRole(role);

            try (AdminUserService svc = new AdminUserService(jdbcUrl)) {
                int id = svc.addUser(name, roleId);
                if (id > 0) {
                    parent.ctx.out().printf("[ADMIN] User added: id=%d name=%s role=%d%n", id, name, roleId);
                    parent.ctx.out().flush();
                } else {
                    parent.ctx.err().println("[ADMIN] Failed to insert user");
                    parent.ctx.err().flush();
                }
            } catch (SQLException e) {
                parent.ctx.err().println("[ADMIN] Database error: " + e.getMessage());
                parent.ctx.err().flush();
            }
        }

        private int parseRole(String r) {
            if (r == null) {
                return ROLE_MAP.get("USER");
            }
            String up = r.trim().toUpperCase();
            Integer mapped = ROLE_MAP.get(up);
            if (mapped != null) {
                return mapped;
            }
            try {
                return Integer.parseInt(r.trim());
            } catch (NumberFormatException ex) {
                return ROLE_MAP.get("USER");
            }
        }
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
            // TODO: UserService.list(role, page, size)
            parent.ctx.out().printf("[ADMIN] List users role=%s page=%d size=%d%n", role, page, size);
            parent.ctx.out().flush();
        }
    }

    @Command(
            name = "remove",
            description = "Remove an existing user from the system",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  minicdn admin user remove --id 42 --force",
                "  minicdn admin user remove --name alice --force --reassign-owner 1"
            })
    public static final class AdminUserRemoveCommand implements Runnable {

        @ParentCommand
        private AdminUserMgmtCommand parent;

        @Option(names = "--id", description = "User ID to remove")
        private Long userId;

        @Option(names = "--name", description = "Username to remove")
        private String username;

        @Option(names = "--force", description = "Do not ask for confirmation")
        private boolean force;

        @Option(names = "--reassign-owner", description = "User ID to reassign this user's resources to (optional)")
        private Long reassignOwnerId;

        @Override
        public void run() {
            if (userId == null && (username == null || username.isBlank())) {
                parent.ctx.err().println("[ADMIN] Error: either --id or --name must be specified");
                parent.ctx.err().flush();
                return;
            }

            String target = (userId != null) ? ("id=" + userId) : ("name=" + username);

            // TODO:
            // 1. Look up the user by ID or name
            // 2. Optionally reassign resources to reassignOwnerId
            // 3. Remove or deactivate the user

            parent.ctx
                    .out()
                    .printf("[ADMIN] Remove user (%s), force=%s, reassignOwnerId=%s%n", target, force, reassignOwnerId);
            parent.ctx.out().flush();
        }
    }
}
