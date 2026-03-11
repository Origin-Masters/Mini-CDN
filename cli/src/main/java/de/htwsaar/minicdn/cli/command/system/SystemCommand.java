package de.htwsaar.minicdn.cli.command.system;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.service.system.JavaJarServiceLauncher;
import de.htwsaar.minicdn.cli.service.system.SystemInitService;
import java.nio.file.Path;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Top-Level Command für lokale System-Bootstrap-Aufgaben.
 */
@Command(
        name = "system",
        description = "Lokale System-Kommandos (Bootstrap/Init)",
        subcommands = {SystemCommand.InitCommand.class})
public final class SystemCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    public SystemCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    @Command(
            name = "init",
            description = "Startet origin/edge/router lokal aus den gebauten Exec-JARs und registriert Edge am Router.")
    static final class InitCommand implements Runnable {

        @ParentCommand
        private SystemCommand parent;

        @Option(
                names = "--project-dir",
                defaultValue = ".",
                description = "Projektwurzel mit den Modulen origin/edge/router (default: ${DEFAULT-VALUE}).")
        private Path projectDir;

        @Option(
                names = "--start-edge",
                defaultValue = "true",
                description =
                        "Soll der Edge-Prozess automatisch gestartet und registriert werden? (default: ${DEFAULT-VALUE})")
        private boolean startEdge;

        @Override
        public void run() {
            CliContext ctx = parent.ctx;
            SystemInitService initService = new SystemInitService(
                    new JavaJarServiceLauncher(ctx.adminToken(), ctx.routerBaseUrl()), ctx.transportClient());

            SystemInitService.InitResult result = initService.init(
                    projectDir, ctx.routerBaseUrl(), ctx.defaultRequestTimeout(), ctx.adminToken(), startEdge);
            ctx.sessionState().remember(result);

            printStatus(ctx, result.origin());
            printStatus(ctx, result.edge());
            printStatus(ctx, result.router());

            if (result.success()) {
                ctx.out().println("[INIT] OK: " + result.message());
            } else {
                ctx.err().println("[INIT] FEHLER: " + result.message());
            }
            ctx.out().flush();
            ctx.err().flush();
        }

        private void printStatus(CliContext ctx, SystemInitService.ServiceStatus status) {
            if ("FAILED".equals(status.state())) {
                ctx.err().printf("[%s] %s (%s)%n", status.name().toUpperCase(), status.message(), status.state());
                return;
            }

            if (status.port() > 0) {
                ctx.out()
                        .printf(
                                "[%s] Port %d: %s (%s)%n",
                                status.name().toUpperCase(), status.port(), status.message(), status.state());
            } else {
                ctx.out().printf("[%s] %s (%s)%n", status.name().toUpperCase(), status.message(), status.state());
            }
        }
    }
}
