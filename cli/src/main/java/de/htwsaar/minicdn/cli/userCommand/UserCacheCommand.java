package de.htwsaar.minicdn.cli.userCommand;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "cache",
        description = "Cache operations for the current user",
        subcommands = {UserCacheCommand.UserCachePurgeCommand.class})
public class UserCacheCommand implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "purge", description = "Purge cache for one of my resources")
    public static class UserCachePurgeCommand implements Runnable {

        @Option(names = "--resource-id", required = true, description = "Resource ID")
        long resourceId;

        @Override
        public void run() {
            // TODO: CacheService.purgeForCurrentUser(resourceId)
            System.out.printf("[USER] Purge cache for my resource %d%n", resourceId);
        }
    }
}
