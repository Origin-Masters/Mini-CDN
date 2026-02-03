package de.htwsaar.minicdn.cli.adminCommands;

import de.htwsaar.minicdn.cli.service.AdminUserService;
import java.sql.SQLException;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "user",
        description = "Manage system users",
        subcommands = {
            AdminUserMgmtCommand.AdminUserAddCommand.class,
            AdminUserMgmtCommand.AdminUserRemoveCommand.class,
            AdminUserMgmtCommand.AdminUserListCommand.class
        })
public class AdminUserMgmtCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "add", description = "Create a new user")
    public static class AdminUserAddCommand implements Runnable {

        @Option(names = "--name", required = true, description = "User name")
        String name;

        @Option(names = "--role", required = true, description = "Role, e.g. ADMIN or USER")
        String role;

        // simple role mapping
        private static final Map<String, Integer> ROLE_MAP = Map.of("ADMIN", 1, "USER", 2);

        @Override
        public void run() {
            String jdbcUrl = System.getenv("MINICDN_JDBC_URL");
            if (jdbcUrl == null || jdbcUrl.isBlank()) jdbcUrl = "jdbc:sqlite:./minicdn.db";

            int roleId = parseRole(role);

            try (AdminUserService svc = new AdminUserService(jdbcUrl)) {
                int id = svc.addUser(name, roleId);
                if (id > 0) {
                    System.out.printf("[ADMIN] User added: id=%d name=%s role=%d%n", id, name, roleId);
                } else {
                    System.err.println("[ADMIN] Failed to insert user");
                }
            } catch (SQLException e) {
                System.err.println("[ADMIN] Database error: " + e.getMessage());
            }
        }

        private int parseRole(String r) {
            if (r == null) return ROLE_MAP.get("USER");
            String up = r.trim().toUpperCase();
            if (ROLE_MAP.containsKey(up)) return ROLE_MAP.get(up);
            try {
                return Integer.parseInt(r.trim());
            } catch (NumberFormatException ex) {
                return ROLE_MAP.get("USER");
            }
        }
    }

    @Command(name = "list", description = "List users in the system")
    public static class AdminUserListCommand implements Runnable {

        @Option(names = "--role", description = "Filter by role, e.g. ADMIN or USER")
        String role;

        @Option(names = "--page", description = "Page number", defaultValue = "1")
        int page;

        @Option(names = "--size", description = "Page size", defaultValue = "20")
        int size;

        @Override
        public void run() {
            // TODO: UserService.list(role, page, size)
            System.out.printf("[ADMIN] List users role=%s page=%d size=%d%n", role, page, size);
        }
    }

    @Command(name = "remove", description = "Remove an existing user from the system")
    public static class AdminUserRemoveCommand implements Runnable {

        @Option(names = "--id", description = "User ID to remove")
        Long userId;

        @Option(names = "--name", description = "Username to remove")
        String username;

        @Option(names = "--force", description = "Do not ask for confirmation")
        boolean force;

        @Option(names = "--reassign-owner", description = "User ID to reassign this user's resources to (optional)")
        Long reassignOwnerId;

        @Override
        public void run() {
            // Basic validation: require either id or name
            if (userId == null && (username == null || username.isBlank())) {
                System.err.println("Error: either --id or --name must be specified");
                return;
            }

            String target = userId != null ? ("id=" + userId) : ("name=" + username);

            // TODO:
            // 1. Look up the user by ID or name
            // 2. Optionally reassign resources to reassignOwnerId
            // 3. Remove or deactivate the user

            System.out.printf(
                    "[ADMIN] Remove user (%s), force=%s, reassignOwnerId=%s%n", target, force, reassignOwnerId);
        }
    }
}
