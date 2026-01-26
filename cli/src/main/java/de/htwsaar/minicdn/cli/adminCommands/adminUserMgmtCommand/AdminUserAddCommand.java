package de.htwsaar.minicdn.cli.adminCommands.adminUserMgmtCommand;

import de.htwsaar.minicdn.cli.service.UserService;
import java.sql.SQLException;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "add", description = "Create a new user")
public class AdminUserAddCommand implements Runnable {

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

        try (UserService svc = new UserService(jdbcUrl)) {
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
