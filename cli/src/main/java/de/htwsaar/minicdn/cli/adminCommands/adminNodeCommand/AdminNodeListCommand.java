package de.htwsaar.minicdn.cli.adminCommands.adminNodeCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "list", description = "List edge nodes")
public class AdminNodeListCommand implements Runnable {

    @Option(names = "--region", description = "Filter by region")
    String region;

    @Override
    public void run() {
        // TODO: NodeService.list(region)
        System.out.printf("[ADMIN] List nodes region=%s%n", region);
    }
}
