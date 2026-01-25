package de.htwsaar.minicdn.cli.adminCommands.adminResourceCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "add", description = "Add a new resource")
public class AdminResourceAddCommand implements Runnable {

    @Option(names = "--path", required = true, description = "Resource path, e.g. /img/logo.png")
    String path;

    @Option(names = "--origin", required = true, description = "Origin server URL")
    String origin;

    @Option(names = "--cache-ttl", required = true, description = "Cache time-to-live in seconds")
    int cacheTtl;

    @Override
    public void run() {
        // TODO: ResourceService.create(...)
        System.out.printf("[ADMIN] Add resource: path=%s, origin=%s, ttl=%d%n",
                path, origin, cacheTtl);
    }
}