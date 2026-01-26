package de.htwsaar.minicdn.cli.adminCommands.adminNodeCommand;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "add", description = "Add an edge node")
public class AdminNodeAddCommand implements Runnable {

    @Option(names = "--name", required = true, description = "Node name")
    String name;

    @Option(names = "--ip", required = true, description = "Node IP address")
    String ip;

    @Option(names = "--region", required = true, description = "Region identifier")
    String region;

    @Override
    public void run() {
        // TODO: NodeService.add(...)
        System.out.printf("[ADMIN] Add node %s (%s, %s)%n", name, ip, region);
    }
}
