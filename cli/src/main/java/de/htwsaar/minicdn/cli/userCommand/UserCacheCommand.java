package de.htwsaar.minicdn.cli.userCommand;

import de.htwsaar.minicdn.cli.userCommand.userCacheCommand.UserCachePurgeCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "cache",
        description = "Cache operations for the current user",
        subcommands = {UserCachePurgeCommand.class})
public class UserCacheCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
