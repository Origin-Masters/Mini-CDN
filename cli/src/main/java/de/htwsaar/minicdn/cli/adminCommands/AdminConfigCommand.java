package de.htwsaar.minicdn.cli.adminCommands;

import de.htwsaar.minicdn.cli.adminCommands.adminConfigCommand.AdminConfigSetCommand;
import de.htwsaar.minicdn.cli.adminCommands.adminConfigCommand.AdminConfigShowCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "config",
        description = "Manage global configuration",
        subcommands = {AdminConfigShowCommand.class, AdminConfigSetCommand.class})
public class AdminConfigCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
