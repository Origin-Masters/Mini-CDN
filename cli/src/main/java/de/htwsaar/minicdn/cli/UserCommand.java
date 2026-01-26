package de.htwsaar.minicdn.cli;

import de.htwsaar.minicdn.cli.userCommand.UserCacheCommand;
import de.htwsaar.minicdn.cli.userCommand.UserResourceCommand;
import de.htwsaar.minicdn.cli.userCommand.UserStatsCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "user",
        description = "User operations",
        subcommands = {UserResourceCommand.class, UserCacheCommand.class, UserStatsCommand.class})
public class UserCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
