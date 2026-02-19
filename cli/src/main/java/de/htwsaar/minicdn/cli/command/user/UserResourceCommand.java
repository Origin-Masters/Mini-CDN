package de.htwsaar.minicdn.cli.command.user;

import de.htwsaar.minicdn.cli.di.CliContext;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * User-Commands zur Anzeige der Ressourcen des aktuellen Nutzers.
 *
 * <p>Ohne Subcommand wird die Usage angezeigt.
 */
@Command(
        name = "resource",
        description = "View resources owned by the current user",
        subcommands = {
            UserResourceCommand.UserResourceListCommand.class,
            UserResourceCommand.UserResourceShowCommand.class
        })
public final class UserResourceCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    /**
     * Konstruktor für Constructor Injection via {@code ContextFactory}.
     *
     * @param ctx CLI-Kontext (Output, HTTP-Client, Timeouts, ...)
     */
    public UserResourceCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    @Command(name = "list", description = "List resources owned by the current user")
    public static final class UserResourceListCommand implements Runnable {

        @ParentCommand
        private UserResourceCommand parent;

        @Override
        public void run() {
            // TODO: ResourceService.listByCurrentUser()
            parent.ctx.out().println("[USER] List my resources");
            parent.ctx.out().flush();
        }
    }

    @Command(name = "show", description = "Show details for one of my resources")
    public static final class UserResourceShowCommand implements Runnable {

        @ParentCommand
        private UserResourceCommand parent;

        @Option(names = "--id", required = true, description = "Resource ID")
        private long id;

        @Override
        public void run() {
            // TODO: ResourceService.showForCurrentUser(id)
            parent.ctx.out().printf("[USER] Show my resource %d%n", id);
            parent.ctx.out().flush();
        }
    }
}
