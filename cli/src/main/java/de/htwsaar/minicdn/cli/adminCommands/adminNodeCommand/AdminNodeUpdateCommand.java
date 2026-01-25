package de.htwsaar.minicdn.cli.adminCommands.adminNodeCommand;// AdminNodeUpdateCommand.java

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "update",
        description = "Update edge node properties"
)
public class AdminNodeUpdateCommand implements Runnable {

    @Option(
            names = "--id",
            required = true,
            description = "Node ID"
    )
    String id;

    @Option(
            names = "--name",
            description = "New node name (optional)"
    )
    String name;

    @Option(
            names = "--ip",
            description = "New IP address (optional)"
    )
    String ip;

    @Option(
            names = "--region",
            description = "New region identifier (optional)"
    )
    String region;

    @Option(
            names = "--status",
            description = "New status, e.g. ACTIVE, DRAINING, MAINTENANCE (optional)"
    )
    String status;

    @Override
    public void run() {
        // TODO: NodeService.update(id, name, ip, region, status)
        System.out.printf(
                "[ADMIN] Update node %s (name=%s, ip=%s, region=%s, status=%s)%n",
                id, name, ip, region, status
        );
    }
}