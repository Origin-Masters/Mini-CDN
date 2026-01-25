package de.htwsaar.minicdn.cli.adminCommands.adminResourceCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "list", description = "List resources")
public class AdminResourceListCommand implements Runnable {

    @Option(names = "--page", description = "Page number", defaultValue = "1")
    int page;

    @Option(names = "--size", description = "Page size", defaultValue = "20")
    int size;

    @Override
    public void run() {
        // TODO: ResourceService.list(page, size)
        System.out.printf("[ADMIN] List resources page=%d size=%d%n", page, size);
    }
}