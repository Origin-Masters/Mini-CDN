package de.htwsaar.minicdn.cli.userCommand;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "stats",
        description = "Statistics for the current user",
        subcommands = {UserStatsCommand.UserStatsResourceCommand.class})
public class UserStatsCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "resource", description = "Show stats for one of my resources")
    public static class UserStatsResourceCommand implements Runnable {

        @Option(names = "--resource-id", required = true, description = "Resource ID")
        long resourceId;

        @Override
        public void run() {
            // TODO: StatsService.resourceStatsForCurrentUser(resourceId)
            System.out.printf("[USER] Stats for my resource %d%n", resourceId);
        }
    }
}
