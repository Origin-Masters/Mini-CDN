package de.htwsaar.minicdn.cli.adminCommands;

import de.htwsaar.minicdn.cli.adminCommands.adminUserMgmtCommand.AdminUserAddCommand;
import de.htwsaar.minicdn.cli.adminCommands.adminUserMgmtCommand.AdminUserListCommand;
import de.htwsaar.minicdn.cli.adminCommands.adminUserMgmtCommand.AdminUserRemoveCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "user",
        description = "Manage system users",
        subcommands = {AdminUserAddCommand.class, AdminUserRemoveCommand.class, AdminUserListCommand.class})
public class AdminUserMgmtCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
