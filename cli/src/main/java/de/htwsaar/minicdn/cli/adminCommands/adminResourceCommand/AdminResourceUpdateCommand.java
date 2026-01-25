package de.htwsaar.minicdn.cli.adminCommands.adminResourceCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "update", description = "Update resource configuration")
public class AdminResourceUpdateCommand implements Runnable {

    @Option(names = "--id", required = true, description = "Resource ID")
    long id;

    @Option(names = "--path", description = "New path (optional)")
    String path;

    @Option(names = "--origin", description = "New origin URL (optional)")
    String origin;

    @Option(names = "--cache-ttl", description = "New cache TTL in seconds (optional)")
    Integer cacheTtl;

    @Override
    public void run() {
        // TODO: ResourceService.update(...)
        System.out.printf("[ADMIN] Update resource %d (path=%s, origin=%s, ttl=%s)%n",
                id, path, origin, cacheTtl);
    }
}