package de.htwsaar.minicdn.cli.command.user;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.service.user.UserStatsService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import java.util.Objects;
import picocli.CommandLine.*;

/**
 * User-Commands für Statistiken des aktuellen Nutzers.
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

    public UserStatsCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.statsService = new UserStatsService(
                ctx.transportClient(), ctx.defaultRequestTimeout(), ctx.routerBaseUrl(), ctx.adminToken());
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    @Command(
            name = "file",
            description = "Show stats for one of my files",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  user stats file --file-id 123", "  user stats file --file-id 456"})
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
            if (result.statusCode() == null || result.statusCode() >= 400) {
                ConsoleUtils.error(parent.ctx.err(), "[USER] Stats fetch failed: HTTP %s", result.statusCode());
                return;
            }
            ConsoleUtils.info(parent.ctx.out(), "[USER] Stats fetch successful");
        }
    }

    @Command(
            name = "list",
            description = "List my top file by activity",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  user stats list", "  user stats list --limit 20"})
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
            if (result.statusCode() == null || result.statusCode() >= 400) {
                ConsoleUtils.error(parent.ctx.err(), "[USER] List fetch failed: HTTP %s", result.statusCode());
                return;
            }
            ConsoleUtils.info(parent.ctx.out(), "[USER] List fetch successful");
        }
    }

    @Command(
            name = "overall",
            description = "Overall statistics for current user",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  user stats overall", "  user stats overall --window-sec 7200"})
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
            if (result.statusCode() == null || result.statusCode() >= 400) {
                ConsoleUtils.error(parent.ctx.err(), "[USER] Overall stats failed: HTTP %s", result.statusCode());
                return;
            }
            ConsoleUtils.info(parent.ctx.out(), "[USER] Overall stats fetch successful");
        }
    }
}
