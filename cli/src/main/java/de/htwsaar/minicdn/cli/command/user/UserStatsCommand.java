package de.htwsaar.minicdn.cli.command.user;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.service.user.UserStatsService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;

import java.util.Objects;

import picocli.CommandLine.*;

/**
 * User-Commands für Statistiken des aktuellen Nutzers.
 * <p>Ohne Subcommand wird die Usage angezeigt.</p>
 */
@Command(
        name = "stats",
        description = "Statistics for the current user",
        subcommands = {
                UserStatsCommand.FileCommand.class,
                UserStatsCommand.ListCommand.class,
                UserStatsCommand.OverallCommand.class
        })
public final class UserStatsCommand implements Runnable {
    private final CliContext ctx;
    private final UserStatsService statsService;

    @Spec
    private Model.CommandSpec spec;

    /**
     * Konstruktor für Constructor Injection via {@code ContextFactory}.
     *
     * @param ctx CLI-Kontext (Output, HTTP-Client, Timeouts, ...)
     */
    public UserStatsCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.statsService = new UserStatsService(ctx);
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    /**
     * Show stats for one of my files.
     */
    @Command(
            name = "file",
            description = "Show stats for one of my files",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                    "  user stats file --file-id 123",
                    "  user stats file --file-id 456"
            })
    public static final class FileCommand implements Runnable {
        @ParentCommand
        private UserStatsCommand parent;

        @Option(
                names = {"--file-id"},
                required = true,
                description = "File ID")
        private long fileId;

        @Override
        public void run() {
            var result = parent.statsService.fileStatsForCurrentUser(fileId);
            if (result.statusCode() >= 400) {
                ConsoleUtils.error(parent.ctx.err(), "[USER] Stats fetch failed: HTTP %d", result.statusCode());
                return;
            }
            ConsoleUtils.info(parent.ctx.out(), "[USER] Stats fetch successful");
        }
    }

    /**
     * List top files by downloads.
     */
    @Command(
            name = "list",
            description = "List my top file by activity",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                    "  user stats list",
                    "  user stats list --limit 20"
            })
    public static final class ListCommand implements Runnable {
        @ParentCommand
        private UserStatsCommand parent;

        @Option(
                names = {"--limit"},
                defaultValue = "10",
                description = "Max number of file (default: 10)")
        private int limit;

        @Override
        public void run() {
            var result = parent.statsService.listUserFilesStats(limit);
            if (result.statusCode() >= 400) {
                ConsoleUtils.error(parent.ctx.err(), "[USER] List fetch failed: HTTP %d", result.statusCode());
                return;
            }
            ConsoleUtils.info(parent.ctx.out(), "[USER] List fetch successful");
        }
    }

    /**
     * Overall user statistics.
     */
    @Command(name = "overall",
            description = "Overall statistics for current user",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                    "  user stats overall",
                    "  user stats overall --window-sec 7200"
            })
    public static final class OverallCommand implements Runnable {
        @ParentCommand
        private UserStatsCommand parent;

        @Option(
                names = {"--window-sec"},
                defaultValue = "3600",
                description = "Time window in seconds (default: 1h)")
        private int windowSec;

        @Override
        public void run() {
            var result = parent.statsService.overallStatsForCurrentUser(windowSec);
            if (result.statusCode() >= 400) {
                ConsoleUtils.error(parent.ctx.err(), "[USER] Overall stats failed: HTTP %d", result.statusCode());
                return;
            }
            ConsoleUtils.info(parent.ctx.out(), "[USER] Overall stats fetch successful");
        }
    }
}
