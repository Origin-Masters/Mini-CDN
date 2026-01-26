package de.htwsaar.minicdn.cli.userCommand.userStatsCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "resource", description = "Show stats for one of my resources")
public class UserStatsResourceCommand implements Runnable {

    @Option(names = "--resource-id", required = true, description = "Resource ID")
    long resourceId;

    @Override
    public void run() {
        // TODO: StatsService.resourceStatsForCurrentUser(resourceId)
        System.out.printf("[USER] Stats for my resource %d%n", resourceId);
    }
}