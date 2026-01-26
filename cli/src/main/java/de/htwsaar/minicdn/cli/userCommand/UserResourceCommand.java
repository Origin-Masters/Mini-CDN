package de.htwsaar.minicdn.cli.userCommand;

import de.htwsaar.minicdn.cli.userCommand.userResourceCommand.UserResourceListCommand;
import de.htwsaar.minicdn.cli.userCommand.userResourceCommand.UserResourceShowCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine;

@Command(
        name = "resource",
        description = "View resources owned by the current user",
        subcommands = {
                UserResourceListCommand.class,
                UserResourceShowCommand.class
        }
)
public class UserResourceCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}