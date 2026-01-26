package de.htwsaar.minicdn.cli.userCommand.userCacheCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "purge", description = "Purge cache for one of my resources")
public class UserCachePurgeCommand implements Runnable {

    @Option(names = "--resource-id", required = true, description = "Resource ID")
    long resourceId;

    @Override
    public void run() {
        // TODO: CacheService.purgeForCurrentUser(resourceId)
        System.out.printf("[USER] Purge cache for my resource %d%n", resourceId);
    }
}