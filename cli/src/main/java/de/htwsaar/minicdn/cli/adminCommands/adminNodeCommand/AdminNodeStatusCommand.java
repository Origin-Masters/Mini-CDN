package de.htwsaar.minicdn.cli.adminCommands.adminNodeCommand;

import picocli.CommandLine;
import picocli.CommandLine.Option;

@CommandLine.Command(name = "status", description = "Show node status")
public class AdminNodeStatusCommand implements Runnable {

    @Option(names = "--id", required = true, description = "Node ID")
    String id;

    @Override
    public void run() {
        // TODO: NodeService.status(id)
        System.out.printf("[ADMIN] Node status %s%n", id);
    }
}
