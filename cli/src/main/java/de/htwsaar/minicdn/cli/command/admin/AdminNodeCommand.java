package de.htwsaar.minicdn.cli.command.admin;

import de.htwsaar.minicdn.cli.di.CliContext;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Verwaltung von Edge-Nodes (Admin).
 *
 * <p>Hinweis: Aktuell sind die Operationen Stubs (TODO). Ohne Subcommand wird die Usage angezeigt.
 */
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
public final class AdminNodeCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    /**
     * Konstruktor für Constructor Injection via {@code ContextFactory}.
     *
     * @param ctx CLI-Kontext (Output, HTTP-Client, Timeouts, ...)
     */
    public AdminNodeCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    @Command(name = "add", description = "Add an edge node")
    public static final class AdminNodeAddCommand implements Runnable {

        @ParentCommand
        private AdminNodeCommand parent;

        @Option(names = "--name", required = true, description = "Node name")
        private String name;

        @Option(names = "--ip", required = true, description = "Node IP address")
        private String ip;

        @Option(names = "--region", required = true, description = "Region identifier")
        private String region;

        @Override
        public void run() {
            // TODO: NodeService.add(...)
            parent.ctx.out().printf("[ADMIN] Add node %s (%s, %s)%n", name, ip, region);
            parent.ctx.out().flush();
        }
    }

    @Command(name = "update", description = "Update edge node properties")
    public static final class AdminNodeUpdateCommand implements Runnable {

        @ParentCommand
        private AdminNodeCommand parent;

        @Option(names = "--id", required = true, description = "Node ID")
        private String id;

        @Option(names = "--name", description = "New node name (optional)")
        private String name;

        @Option(names = "--ip", description = "New IP address (optional)")
        private String ip;

        @Option(names = "--region", description = "New region identifier (optional)")
        private String region;

        @Option(names = "--status", description = "New status, e.g. ACTIVE, DRAINING, MAINTENANCE (optional)")
        private String status;

        @Override
        public void run() {
            // TODO: NodeService.update(id, name, ip, region, status)
            parent.ctx
                    .out()
                    .printf(
                            "[ADMIN] Update node %s (name=%s, ip=%s, region=%s, status=%s)%n",
                            id, name, ip, region, status);
            parent.ctx.out().flush();
        }
    }

    @Command(name = "delete", description = "Delete an edge node")
    public static final class AdminNodeDeleteCommand implements Runnable {

        @ParentCommand
        private AdminNodeCommand parent;

        @Option(names = "--id", required = true, description = "Node ID")
        private String id;

        @Option(names = "--force", description = "Do not ask for confirmation")
        private boolean force;

        @Override
        public void run() {
            // TODO:
            // 1. Check if the node can be safely removed (no traffic, or drained)
            // 2. Remove the node from the cluster
            parent.ctx.out().printf("[ADMIN] Delete node %s, force=%s%n", id, force);
            parent.ctx.out().flush();
        }
    }

    @Command(name = "list", description = "List edge nodes")
    public static final class AdminNodeListCommand implements Runnable {

        @ParentCommand
        private AdminNodeCommand parent;

        @Option(names = "--region", description = "Filter by region")
        private String region;

        @Override
        public void run() {
            // TODO: NodeService.list(region)
            parent.ctx.out().printf("[ADMIN] List nodes region=%s%n", region);
            parent.ctx.out().flush();
        }
    }

    @Command(name = "status", description = "Show node status")
    public static final class AdminNodeStatusCommand implements Runnable {

        @ParentCommand
        private AdminNodeCommand parent;

        @Option(names = "--id", required = true, description = "Node ID")
        private String id;

        @Override
        public void run() {
            // TODO: NodeService.status(id)
            parent.ctx.out().printf("[ADMIN] Node status %s%n", id);
            parent.ctx.out().flush();
        }
    }
}
