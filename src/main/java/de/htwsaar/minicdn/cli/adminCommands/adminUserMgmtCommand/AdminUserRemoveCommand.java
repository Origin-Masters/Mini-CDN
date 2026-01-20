package de.htwsaar.minicdn.cli.adminCommands.adminUserMgmtCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "remove",
        description = "Remove an existing user from the system"
)
public class AdminUserRemoveCommand implements Runnable {

    @Option(
            names = "--id",
            description = "User ID to remove"
    )
    Long userId;

    @Option(
            names = "--name",
            description = "Username to remove"
    )
    String username;

    @Option(
            names = "--force",
            description = "Do not ask for confirmation"
    )
    boolean force;

    @Option(
            names = "--reassign-owner",
            description = "User ID to reassign this user's resources to (optional)"
    )
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
                "[ADMIN] Remove user (%s), force=%s, reassignOwnerId=%s%n",
                target, force, reassignOwnerId
        );
    }
}