package de.htwsaar.minicdn.cli.adminCommands;

import de.htwsaar.minicdn.cli.adminCommands.adminResourceCommand.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "resource",
        description = "Manage CDN resources",
        subcommands = {
                AdminResourceAddCommand.class,
                AdminResourceUpdateCommand.class,
                AdminResourceDeleteCommand.class,
                AdminResourceListCommand.class,
                AdminResourceShowCommand.class
        })
public class AdminResourceCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
