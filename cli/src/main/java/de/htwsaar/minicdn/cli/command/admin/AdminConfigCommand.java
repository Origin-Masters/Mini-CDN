package de.htwsaar.minicdn.cli.command.admin;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.service.admin.AdminConfigService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import java.util.Objects;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Verwaltung der globalen Konfiguration für Admin-Zwecke.
 *
 * <p>Hinweis: Diese Klasse ist ein "Group-Command".
 * Ohne Subcommand wird nur die Usage angezeigt.
 */
@Command(
        name = "config",
        description = "Manage global configuration",
        subcommands = {AdminConfigCommand.AdminConfigSetCommand.class, AdminConfigCommand.AdminConfigShowCommand.class})
public final class AdminConfigCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    /**
     * Konstruktor für Constructor Injection via {@code ContextFactory}.
     *
     * @param ctx CLI-Kontext (Output, HTTP-Client, Timeouts, ...)
     */
    public AdminConfigCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    /**
     * Setzt einen Konfigurationswert.
     */
    @Command(name = "set", description = "Set a global configuration value")
    public static final class AdminConfigSetCommand implements Runnable {

        @ParentCommand
        private AdminConfigCommand parent;

        @Option(names = "--key", required = true, description = "Configuration key")
        private String key;

        @Option(names = "--value", required = true, description = "Configuration value")
        private String value;

        @Override
        public void run() {
            boolean ok = AdminConfigService.set(key, value);
            if (ok) {
                ConsoleUtils.info(parent.ctx.err(), "[ADMIN] Set config %s = %s", key, value);
            } else {
                ConsoleUtils.error(parent.ctx.err(), "[ADMIN] Failed to set config %s = %s", key, value);
            }
        }
    }

    /**
     * Zeigt alle gesetzten Konfigurationswerte.
     */
    @Command(name = "show", description = "Show global configuration")
    public static final class AdminConfigShowCommand implements Runnable {

        @ParentCommand
        private AdminConfigCommand parent;

        @Override
        public void run() {
            var lines = AdminConfigService.formatLines();

            if (lines.isEmpty()) {
                ConsoleUtils.info(parent.ctx.err(), "[ADMIN] No configurations found");
                return;
            }

            parent.ctx.out().println("[ADMIN] Global configuration:");
            for (String line : lines) {
                parent.ctx.out().printf("[ADMIN] %s%n", line);
            }
            parent.ctx.out().flush();
        }
    }
}
