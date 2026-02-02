package de.htwsaar.minicdn.cli.adminCommands;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "node",
        description = "Manage edge nodes",
        subcommands = {
            AdminNodeCommand.AdminNodeAddCommand.class,
            AdminNodeCommand.AdminNodeUpdateCommand.class,
            AdminNodeCommand.AdminNodeDeleteCommand.class,
            AdminNodeCommand.AdminNodeListCommand.class,
            AdminNodeCommand.AdminNodeStatusCommand.class
        })
public class AdminNodeCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "add", description = "Add an edge node")
    public static class AdminNodeAddCommand implements Runnable {

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

    @Command(name = "update", description = "Update edge node properties")
    public static class AdminNodeUpdateCommand implements Runnable {

        @Option(names = "--id", required = true, description = "Node ID")
        String id;

        @Option(names = "--name", description = "New node name (optional)")
        String name;

        @Option(names = "--ip", description = "New IP address (optional)")
        String ip;

        @Option(names = "--region", description = "New region identifier (optional)")
        String region;

        @Option(names = "--status", description = "New status, e.g. ACTIVE, DRAINING, MAINTENANCE (optional)")
        String status;

        @Override
        public void run() {
            // TODO: NodeService.update(id, name, ip, region, status)
            System.out.printf(
                    "[ADMIN] Update node %s (name=%s, ip=%s, region=%s, status=%s)%n", id, name, ip, region, status);
        }
    }

    @Command(name = "delete", description = "Delete an edge node")
    public static class AdminNodeDeleteCommand implements Runnable {

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

    @Command(name = "list", description = "List edge nodes")
    public static class AdminNodeListCommand implements Runnable {

        @Option(names = "--region", description = "Filter by region")
        String region;

        @Override
        public void run() {
            // TODO: NodeService.list(region)
            System.out.printf("[ADMIN] List nodes region=%s%n", region);
        }
    }

    @Command(name = "status", description = "Show node status")
    public static class AdminNodeStatusCommand implements Runnable {

        @Option(names = "--id", required = true, description = "Node ID")
        String id;

        @Override
        public void run() {
            // TODO: NodeService.status(id)
            System.out.printf("[ADMIN] Node status %s%n", id);
        }
    }
}
