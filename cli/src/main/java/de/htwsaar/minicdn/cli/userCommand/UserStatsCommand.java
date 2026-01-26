package de.htwsaar.minicdn.cli.userCommand;

import de.htwsaar.minicdn.cli.userCommand.userStatsCommand.UserStatsResourceCommand;
import picocli.CommandLine.Command;
import picocli.CommandLine;

@Command(
        name = "stats",
        description = "Statistics for the current user",
        subcommands = {
                UserStatsResourceCommand.class
        }
)
public class UserStatsCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}