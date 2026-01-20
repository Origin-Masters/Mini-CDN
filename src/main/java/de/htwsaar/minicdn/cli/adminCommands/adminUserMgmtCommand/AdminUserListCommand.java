package de.htwsaar.minicdn.cli.adminCommands.adminUserMgmtCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "list",
        description = "List users in the system"
)
public class AdminUserListCommand implements Runnable {

    @Option(
            names = "--role",
            description = "Filter by role, e.g. ADMIN or USER"
    )
    String role;

    @Option(
            names = "--page",
            description = "Page number",
            defaultValue = "1"
    )
    int page;

    @Option(
            names = "--size",
            description = "Page size",
            defaultValue = "20"
    )
    int size;

    @Override
    public void run() {
        // TODO: UserService.list(role, page, size)
        System.out.printf(
                "[ADMIN] List users role=%s page=%d size=%d%n",
                role, page, size
        );
    }
}