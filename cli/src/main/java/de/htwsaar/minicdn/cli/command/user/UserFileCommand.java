package de.htwsaar.minicdn.cli.command.user;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.DownloadResult;
import de.htwsaar.minicdn.cli.service.user.UserFileDownloadService;
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
 *
 * <p>Ohne Subcommand wird die Usage angezeigt.
 */
@Command(
        name = "file",
        description = "File operations",
        mixinStandardHelpOptions = true,
        footerHeading = "%nBeispiele:%n",
        footer = {
            "  user file download EU docs/manual.pdf -o ./manual.pdf -H http://localhost:8082",
            "  user file download EU docs/manual.pdf -o ./manual.pdf -H http://localhost:8082 --client-id alice",
            "  user file download EU docs/manual.pdf -o ./manual.pdf -H http://localhost:8082 --overwrite"
        },
        subcommands = {UserFileCommand.FileDownloadCommand.class})
public final class UserFileCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    /**
     * Konstruktor für Constructor Injection via {@code ContextFactory}.
     *
     * @param ctx CLI-Kontext (Output, HTTP-Client, Timeouts, ...)
     */
    public UserFileCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    UserFileDownloadService downloadService() {
        return new UserFileDownloadService(ctx.httpClient(), ctx.defaultRequestTimeout());
    }

    /**
     * Lädt eine Datei über den Router herunter und speichert sie lokal.
     *
     * <p>HTTP-Flow:
     * <ul>
     *   <li>GET {routerBaseUrl}/api/cdn/files/{remotePath} mit Header X-Client-Region</li>
     *   <li>Router antwortet i. d. R. mit 307 + Location auf Edge</li>
     *   <li>CLI folgt Location und lädt vom Edge (GET {edgeBaseUrl}/api/edge/files/{remotePath})</li>
     * </ul>
     *
     * <p>Exit-Codes:
     * <ul>
     *   <li>0 = OK</li>
     *   <li>3 = Client-Validation (kein Request)</li>
     *   <li>4 = HTTP 4xx</li>
     *   <li>2 = HTTP 5xx</li>
     *   <li>1 = Exception/IO</li>
     * </ul>
     */
    @Command(
            name = "download",
            description = "Download a file via router (handles redirect to edge)",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  user file download EU docs/manual.pdf -o ./manual.pdf -H http://localhost:8082",
                "  user file download EU docs/manual.pdf -o ./manual.pdf -H http://localhost:8082 --client-id alice",
                "  user file download EU docs/manual.pdf -o ./manual.pdf -H http://localhost:8082 --overwrite"
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
            if (cleanRemote.isBlank() || isUnsafeRemotePath(cleanRemote)) {
                parent.ctx.err().println("[FILE] Invalid remotePath (must be a safe, non-blank relative path)");
                parent.ctx.err().flush();
                return 3;
            }

            String cleanRegion = Objects.toString(region, "").trim();
            if (cleanRegion.isBlank()) {
                parent.ctx.err().println("[FILE] Missing/blank --region");
                parent.ctx.err().flush();
                return 3;
            }

            if (out == null) {
                parent.ctx.err().println("[FILE] Missing --out");
                parent.ctx.err().flush();
                return 3;
            }
            if (Files.exists(out) && !overwrite) {
                parent.ctx.err().printf("[FILE] Output file already exists (use --overwrite): %s%n", out);
                parent.ctx.err().flush();
                return 3;
            }
            if (Files.exists(out) && Files.isDirectory(out)) {
                parent.ctx.err().printf("[FILE] Output path is a directory: %s%n", out);
                parent.ctx.err().flush();
                return 3;
            }

            URI base = UriUtils.ensureTrailingSlash(host);
            DownloadResult result = parent.downloadService()
                    .downloadViaRouter(base, cleanRemote, cleanRegion, clientId, out, overwrite);

            if (result.error() != null) {
                parent.ctx.err().printf("[FILE] Download failed: %s%n", result.error());
                parent.ctx.err().flush();
                return 1;
            }

            int sc = Objects.requireNonNull(result.statusCode(), "statusCode");
            if (sc >= 200 && sc < 300) {
                parent.ctx
                        .out()
                        .printf("[FILE] Downloaded '%s' -> %s (%d bytes)%n", cleanRemote, out, result.bytesWritten());
                parent.ctx.out().flush();
                return 0;
            }

            if (sc >= 400 && sc < 500) {
                parent.ctx.err().printf("[FILE] Request rejected (HTTP %d) for '%s'%n", sc, cleanRemote);
                parent.ctx.err().flush();
                return 4;
            }

            parent.ctx.err().printf("[FILE] Server error (HTTP %d) for '%s'%n", sc, cleanRemote);
            parent.ctx.err().flush();
            return 2;
        }

        private static boolean isUnsafeRemotePath(String p) {
            return p.startsWith("/") || p.contains("..") || p.contains("\\");
        }
    }
}
