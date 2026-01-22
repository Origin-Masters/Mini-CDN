package de.htwsaar.minicdn.cli.adminCommands.adminUserMgmtCommand; // AdminUserAddCommand.java

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "add", description = "Create a new user")
public class AdminUserAddCommand implements Runnable {

    @Option(names = "--name", required = true, description = "User name")
    String name;

    @Option(names = "--role", required = true, description = "Role, e.g. ADMIN or USER")
    String role;

    @Override
    public void run() {
        // TODO: UserService.add(...)
        System.out.printf("[ADMIN] Add user %s role=%s%n", name, role);
    }
}
