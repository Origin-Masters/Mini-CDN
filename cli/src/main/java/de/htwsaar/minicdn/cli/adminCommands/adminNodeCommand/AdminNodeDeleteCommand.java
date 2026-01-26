package de.htwsaar.minicdn.cli.adminCommands.adminNodeCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "delete", description = "Delete an edge node")
public class AdminNodeDeleteCommand implements Runnable {

    @Option(names = "--id", required = true, description = "Node ID")
    String id;

    @Option(names = "--force", description = "Do not ask for confirmation")
    boolean force;

    @Override
    public void run() {
        // TODO:
        // 1. Check if the node can be safely removed (no traffic, or drained)
        // 2. Remove the node from the cluster

        System.out.printf("[ADMIN] Delete node %s, force=%s%n", id, force);
    }
}
