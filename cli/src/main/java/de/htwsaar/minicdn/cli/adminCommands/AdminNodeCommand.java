package de.htwsaar.minicdn.cli.adminCommands;

import de.htwsaar.minicdn.cli.adminCommands.adminNodeCommand.AdminNodeAddCommand;
import de.htwsaar.minicdn.cli.adminCommands.adminNodeCommand.AdminNodeDeleteCommand;
import de.htwsaar.minicdn.cli.adminCommands.adminNodeCommand.AdminNodeListCommand;
import de.htwsaar.minicdn.cli.adminCommands.adminNodeCommand.AdminNodeStatusCommand;
import de.htwsaar.minicdn.cli.adminCommands.adminNodeCommand.AdminNodeUpdateCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "node",
        description = "Manage edge nodes",
        subcommands = {
            AdminNodeAddCommand.class,
            AdminNodeUpdateCommand.class,
            AdminNodeDeleteCommand.class,
            AdminNodeListCommand.class,
            AdminNodeStatusCommand.class
        })
public class AdminNodeCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
