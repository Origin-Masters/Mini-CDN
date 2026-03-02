package de.htwsaar.minicdn.cli.command.user;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.DownloadResult;
import de.htwsaar.minicdn.cli.service.user.UserFileService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import de.htwsaar.minicdn.cli.util.PathUtils;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Datei-Operationen (Download über den Router).
 */
@Command(
        name = "file",
        description = "File operations",
        mixinStandardHelpOptions = true,
        footerHeading = "%nBeispiele:%n",
        footer = {
            "  user file download -r EU docs/manual.pdf -o ./manual.pdf -H http://localhost:8082",
            "  user file download -r EU docs/manual.pdf -o ./manual.pdf -H http://localhost:8082 --client-id alice",
            "  user file download -r EU docs/manual.pdf -o ./manual.pdf -H http://localhost:8082 --overwrite"
        },
        subcommands = {UserFileCommand.FileDownloadCommand.class})
public final class UserFileCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    public UserFileCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    UserFileService downloadService() {
        return new UserFileService(ctx.transportClient(), ctx.defaultRequestTimeout());
    }

    @Command(
            name = "download",
            description = "Download a file via router (handles redirect to edge)",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  user file download -r EU docs/manual.pdf -o ./manual.pdf -H http://localhost:8082",
                "  user file download -r EU docs/manual.pdf -o ./manual.pdf -H http://localhost:8082 --client-id alice",
                "  user file download -r EU docs/manual.pdf -o ./manual.pdf -H http://localhost:8082 --overwrite"
            })
    public static final class FileDownloadCommand implements Callable<Integer> {

        @ParentCommand
        private UserFileCommand parent;

        @Parameters(index = "0", paramLabel = "<remotePath>", description = "Remote file path, e.g. docs/manual.pdf")
        private String remotePath;

        @Option(
                names = {"-o", "--out"},
                required = true,
                paramLabel = "OUT_FILE",
                description = "Local output file path")
        private Path out;

        @Option(
                names = {"-H", "--host"},
                defaultValue = "http://localhost:8082",
                paramLabel = "ROUTER_URL",
                description = "Router base URL (scheme://host:port)")
        private URI host;

        @Option(
                names = {"-r", "--region"},
                required = true,
                paramLabel = "REGION",
                description = "Client region for routing, e.g. EU")
        private String region;

        @Option(
                names = {"--client-id"},
                paramLabel = "CLIENT_ID",
                description = "Optional client id (used by router stats), e.g. alice")
        private String clientId;

        @Option(
                names = {"--overwrite"},
                defaultValue = "false",
                description = "Overwrite existing local file")
        private boolean overwrite;

        @Override
        public Integer call() {
            String cleanRemote =
                    PathUtils.stripLeadingSlash(Objects.toString(remotePath, "").trim());
            if (cleanRemote.isBlank() || PathUtils.isUnsafeRemotePath(cleanRemote)) {
                ConsoleUtils.error(
                        parent.ctx.err(), "[FILE] Invalid remotePath (must be a safe, non-blank relative path)");
                return 3;
            }

            String cleanRegion = Objects.toString(region, "").trim();
            if (cleanRegion.isBlank()) {
                ConsoleUtils.error(parent.ctx.err(), "[FILE] Missing/blank --region");
                return 3;
            }

            if (out == null) {
                ConsoleUtils.error(parent.ctx.err(), "[FILE] Missing --out");
                return 3;
            }
            if (Files.exists(out) && !overwrite) {
                ConsoleUtils.error(parent.ctx.err(), "[FILE] Output file already exists (use --overwrite): %s", out);
                return 3;
            }
            if (Files.exists(out) && Files.isDirectory(out)) {
                ConsoleUtils.error(parent.ctx.err(), "[FILE] Output path is a directory: %s", out);
                return 3;
            }

            URI base = UriUtils.ensureTrailingSlash(host);
            DownloadResult result = parent.downloadService()
                    .downloadViaRouter(base, cleanRemote, cleanRegion, clientId, out, overwrite);

            if (result.error() != null) {
                ConsoleUtils.error(parent.ctx.err(), "[FILE] Download failed: %s", result.error());
                return 1;
            }

            int sc = Objects.requireNonNull(result.statusCode(), "statusCode");
            if (sc >= 200 && sc < 300) {
                ConsoleUtils.info(
                        parent.ctx.out(),
                        "[FILE] Downloaded '%s' -> %s (%d bytes)",
                        cleanRemote,
                        out,
                        result.bytesWritten());
                return 0;
            }

            if (sc >= 400 && sc < 500) {
                ConsoleUtils.error(parent.ctx.err(), "[FILE] Request rejected (HTTP %d) for '%s'", sc, cleanRemote);
                return 4;
            }

            ConsoleUtils.error(parent.ctx.err(), "[FILE] Server error (HTTP %d) for '%s'", sc, cleanRemote);
            return 2;
        }
    }
}
