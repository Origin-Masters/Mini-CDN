package de.htwsaar.minicdn.cli.command.user;

import static de.htwsaar.minicdn.common.util.DefaultsURL.ROUTER_URL;
import static de.htwsaar.minicdn.common.util.ExitCodes.REJECTED;
import static de.htwsaar.minicdn.common.util.ExitCodes.REQUEST_FAILED;
import static de.htwsaar.minicdn.common.util.ExitCodes.SERVER_ERROR;
import static de.htwsaar.minicdn.common.util.ExitCodes.SUCCESS;
import static de.htwsaar.minicdn.common.util.ExitCodes.VALIDATION;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.DownloadResult;
import de.htwsaar.minicdn.cli.service.user.UserFileService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import de.htwsaar.minicdn.common.util.PathUtils;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Stellt User-Befehle für Dateioperationen über den Router bereit.
 *
 * <p>Die Klasse kapselt ausschließlich CLI-spezifische Aufgaben wie
 * Usage-Anzeige, Eingabevalidierung, Exit-Code-Mapping und Konsolenausgabe.
 * Die eigentliche Download-Fachlogik bleibt im {@link UserFileService}.</p>
 */
@Command(
        name = "file",
        description = "File operations",
        mixinStandardHelpOptions = true,
        footerHeading = "%nBeispiele:%n",
        footer = {
            "  user file download -r EU docs/manual.pdf -o ./manual.pdf",
            "  user file download -r EU docs/manual.pdf -o ./manual.pdf --client-id alice",
            "  user file download-segmented -r EU docs/manual.pdf -o ./manual-seg.pdf --segments 4 --retries 2"
        },
        subcommands = {UserFileCommand.FileDownloadCommand.class, UserFileCommand.FileSegmentedDownloadCommand.class})
public final class UserFileCommand implements Runnable {

    private final CliContext ctx;
    private final UserFileService downloadService;

    @Spec
    private CommandSpec spec;

    /**
     * Erzeugt den Command mit gemeinsamem CLI-Kontext.
     *
     * @param ctx gemeinsamer CLI-Kontext
     */
    public UserFileCommand(CliContext ctx) {
        this(
                ctx,
                new UserFileService(Objects.requireNonNull(ctx, "ctx").transportClient(), ctx.defaultRequestTimeout()));
    }

    /**
     * Interner Konstruktor für Tests und explizite Dependency Injection.
     *
     * @param ctx gemeinsamer CLI-Kontext
     * @param downloadService fachlicher Download-Service
     */
    UserFileCommand(CliContext ctx, UserFileService downloadService) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.downloadService = Objects.requireNonNull(downloadService, "downloadService");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    /**
     * Liefert den fachlichen Download-Service.
     *
     * @return Download-Service
     */
    UserFileService downloadService() {
        return downloadService;
    }

    /**
     * Normalisiert und validiert die Router-Basis-URL.
     *
     * @param router rohe Router-URI
     * @return normalisierte Basis-URI mit Trailing Slash
     * @throws IllegalArgumentException falls die URI ungültig ist
     */
    URI normalizeRouter(URI router) {
        URI value = Objects.requireNonNull(router, "router");
        if (!value.isAbsolute() || value.getScheme() == null) {
            throw new IllegalArgumentException("router URL must be an absolute http/https URI");
        }

        String scheme = value.getScheme().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("router URL must use http or https");
        }

        return UriUtils.ensureTrailingSlash(value);
    }

    /**
     * Normalisiert und validiert den Remote-Pfad.
     *
     * @param rawPath roher Eingabepfad
     * @return sicherer relativer Pfad
     * @throws IllegalArgumentException falls der Pfad ungültig ist
     */
    String normalizeRemotePath(String rawPath) {
        return PathUtils.normalizeRelativePath(rawPath);
    }

    /**
     * Validiert und normalisiert die Region.
     *
     * @param rawRegion rohe Region
     * @return getrimmte Region
     * @throws IllegalArgumentException falls die Region leer ist
     */
    String normalizeRegion(String rawRegion) {
        String value = Objects.toString(rawRegion, "").trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("region must not be blank");
        }
        return value;
    }

    /**
     * Normalisiert die optionale Client-ID.
     *
     * @param rawClientId rohe Client-ID
     * @return getrimmte Client-ID oder {@code null}, wenn leer
     */
    String normalizeClientId(String rawClientId) {
        String value = Objects.toString(rawClientId, "").trim();
        return value.isBlank() ? null : value;
    }

    /**
     * Prüft, ob die angegebene Ausgabedatei grundsätzlich gültig ist.
     *
     * @param outFile Zieldatei
     * @param overwrite Kennzeichen, ob bestehende Dateien überschrieben werden dürfen
     * @throws IllegalArgumentException falls das Ziel ungültig ist
     */
    void validateOutputFile(Path outFile, boolean overwrite) {
        if (outFile == null) {
            throw new IllegalArgumentException("output file must not be null");
        }
        if (Files.exists(outFile) && Files.isDirectory(outFile)) {
            throw new IllegalArgumentException("output path is a directory: " + outFile);
        }
        if (Files.exists(outFile) && !overwrite) {
            throw new IllegalArgumentException("output file already exists: " + outFile);
        }
    }

    /**
     * Validiert die gewünschte Segmentanzahl.
     *
     * @param segments Segmentanzahl aus der CLI
     * @return validierte Segmentanzahl
     */
    int validateSegments(int segments) {
        if (segments <= 0) {
            throw new IllegalArgumentException("--segments must be greater than 0");
        }
        return segments;
    }

    /**
     * Validiert die Retry-Anzahl pro Segment.
     *
     * @param retries Retry-Anzahl aus der CLI
     * @return validierte Retry-Anzahl
     */
    int validateRetries(int retries) {
        if (retries < 0) {
            throw new IllegalArgumentException("--retries must be greater than or equal to 0");
        }
        return retries;
    }

    /**
     * Gibt einen Validierungsfehler einheitlich aus.
     *
     * @param message Fehlermeldung
     * @return Exit-Code für Validierungsfehler
     */
    int validationError(String message) {
        ConsoleUtils.error(ctx.err(), "[USER] %s", Objects.toString(message, "invalid input"));
        return VALIDATION.code();
    }

    /**
     * Gibt einen technischen Fehler einheitlich aus.
     *
     * @param message Fehlermeldung
     * @return Exit-Code für technische Fehler
     */
    int requestFailed(String message) {
        ConsoleUtils.error(ctx.err(), "[USER] %s", Objects.toString(message, "request failed"));
        return REQUEST_FAILED.code();
    }

    /**
     * Bewertet das Download-Ergebnis zentral und gibt den passenden Exit-Code zurück.
     *
     * @param routerBaseUrl normalisierte Router-Basis-URL
     * @param remotePath normalisierter Remote-Pfad
     * @param outFile lokale Zieldatei
     * @param result Ergebnis des Download-Aufrufs
     * @return passender Exit-Code
     */
    int handleDownloadResult(URI routerBaseUrl, String remotePath, Path outFile, DownloadResult result) {
        Objects.requireNonNull(result, "result");

        if (result.error() != null) {
            return requestFailed("Download failed: " + result.error());
        }

        Integer statusCode = Objects.requireNonNull(result.statusCode(), "statusCode");
        if (result.is2xx()) {
            ConsoleUtils.info(
                    ctx.out(),
                    "[USER] Download via router successful HTTP %d router=%s path=%s out=%s bytes=%d",
                    statusCode,
                    routerBaseUrl,
                    remotePath,
                    outFile,
                    result.bytesWritten());
            return SUCCESS.code();
        }

        if (result.is4xx()) {
            ConsoleUtils.error(ctx.err(), "[USER] Request rejected (HTTP %d) for '%s'", statusCode, remotePath);
            return REJECTED.code();
        }

        ConsoleUtils.error(ctx.err(), "[USER] Server error (HTTP %d) for '%s'", statusCode, remotePath);
        return SERVER_ERROR.code();
    }

    /**
     * Download-Command für Dateien über den Router.
     *
     * <p>Der Command validiert alle CLI-Eingaben, delegiert den eigentlichen
     * Download an den {@link UserFileService} und mappt das Ergebnis auf
     * konsistente Exit-Codes.</p>
     */
    @Command(
            name = "download",
            description = "Download a file via router (handles redirect to edge)",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  user file download -r EU docs/manual.pdf -o ./manual.pdf",
                "  user file download -r EU docs/manual.pdf -o ./manual.pdf --client-id alice",
                "  user file download -r EU docs/manual.pdf -o ./manual.pdf --overwrite"
            })
    public static final class FileDownloadCommand implements Callable<Integer> {

        @ParentCommand
        private UserFileCommand parent;

        /**
         * Relativer Remote-Pfad der herunterzuladenden Datei.
         */
        @Parameters(index = "0", paramLabel = "REMOTE_PATH", description = "Remote file path, e.g. docs/manual.pdf")
        private String remotePath;

        /**
         * Lokale Zieldatei.
         */
        @Option(
                names = {"-o", "--out"},
                required = true,
                paramLabel = "OUT_FILE",
                description = "Local output file path")
        private Path out;

        /**
         * Router-Basis-URL.
         */
        @Option(
                names = {"-H", "--host"},
                defaultValue = ROUTER_URL,
                paramLabel = "ROUTER_URL",
                description = "Router base URL (scheme://host:port)")
        private URI host;

        /**
         * Client-Region für das Routing.
         */
        @Option(
                names = {"-r", "--region"},
                required = true,
                paramLabel = "REGION",
                description = "Client region for routing, e.g. EU")
        private String region;

        /**
         * Optionale Client-ID für Routing- und Statistikzwecke.
         */
        @Option(
                names = {"--client-id"},
                paramLabel = "CLIENT_ID",
                description = "Optional client id, e.g. alice")
        private String clientId;

        /**
         * Steuert, ob eine bestehende Datei überschrieben werden darf.
         */
        @Option(
                names = {"--overwrite"},
                defaultValue = "false",
                description = "Overwrite existing local file")
        private boolean overwrite;

        @Override
        public Integer call() {
            try {
                URI routerBaseUrl = parent.normalizeRouter(host);
                String cleanRemotePath = parent.normalizeRemotePath(remotePath);
                String cleanRegion = parent.normalizeRegion(region);
                String cleanClientId = parent.normalizeClientId(clientId);
                Long loggedInUserId = parent.ctx.sessionState().loggedInUserId();
                Path targetFile =
                        Objects.requireNonNull(out, "out").toAbsolutePath().normalize();

                parent.validateOutputFile(targetFile, overwrite);

                DownloadResult result = parent.downloadService()
                        .downloadViaRouter(
                                routerBaseUrl,
                                cleanRemotePath,
                                cleanRegion,
                                cleanClientId,
                                loggedInUserId,
                                targetFile,
                                overwrite);

                return parent.handleDownloadResult(routerBaseUrl, cleanRemotePath, targetFile, result);

            } catch (IllegalArgumentException ex) {
                return parent.validationError(ex.getMessage());
            } catch (Exception ex) {
                return parent.requestFailed("Download failed: " + ex.getMessage());
            }
        }
    }

    /**
     * Download-Command für segmentiertes, paralleles Laden über mehrere Edges.
     */
    @Command(
            name = "download-segmented",
            description = "Download a file in parallel segments (with retry)",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  user file download-segmented -r EU docs/manual.pdf -o ./manual.pdf --segments 4",
                "  user file download-segmented -r EU docs/manual.pdf -o ./manual.pdf --segments 6 --retries 3",
                "  user file download-segmented -r EU docs/manual.pdf -o ./manual.pdf --edge http://localhost:8083 --edge http://localhost:8084"
            })
    public static final class FileSegmentedDownloadCommand implements Callable<Integer> {

        @ParentCommand
        private UserFileCommand parent;

        /** Relativer Remote-Pfad der herunterzuladenden Datei. */
        @Parameters(index = "0", paramLabel = "REMOTE_PATH", description = "Remote file path, e.g. docs/manual.pdf")
        private String remotePath;

        /** Lokale Zieldatei. */
        @Option(
                names = {"-o", "--out"},
                required = true,
                paramLabel = "OUT_FILE",
                description = "Local output file path")
        private Path out;

        /** Router-Basis-URL. */
        @Option(
                names = {"-H", "--host"},
                defaultValue = ROUTER_URL,
                paramLabel = "ROUTER_URL",
                description = "Router base URL (scheme://host:port)")
        private URI host;

        /** Client-Region für das Routing. */
        @Option(
                names = {"-r", "--region"},
                required = true,
                paramLabel = "REGION",
                description = "Client region for routing, e.g. EU")
        private String region;

        /** Optionale Client-ID. */
        @Option(
                names = {"--client-id"},
                paramLabel = "CLIENT_ID",
                description = "Optional client id, e.g. alice")
        private String clientId;

        /** Anzahl der Segmente/Parallel-Downloads. */
        @Option(
                names = {"--segments"},
                defaultValue = "4",
                paramLabel = "N",
                description = "Number of segments / parallel downloads")
        private int segments;

        /** Wiederholversuche pro Segment. */
        @Option(
                names = {"--retries"},
                defaultValue = "2",
                paramLabel = "N",
                description = "Retry count for failed or invalid segments")
        private int retries;

        /** Optionale feste Edge-Knoten für Segment-Downloads. */
        @Option(
                names = {"--edge"},
                paramLabel = "EDGE_URL",
                description = "Optional edge base URL (can be repeated)")
        private List<URI> edges;

        /** Steuert, ob eine bestehende Datei überschrieben werden darf. */
        @Option(
                names = {"--overwrite"},
                defaultValue = "false",
                description = "Overwrite existing local file")
        private boolean overwrite;

        @Override
        public Integer call() {
            try {
                URI routerBaseUrl = parent.normalizeRouter(host);
                String cleanRemotePath = parent.normalizeRemotePath(remotePath);
                String cleanRegion = parent.normalizeRegion(region);
                String cleanClientId = parent.normalizeClientId(clientId);
                Long loggedInUserId = parent.ctx.sessionState().loggedInUserId();
                Path targetFile =
                        Objects.requireNonNull(out, "out").toAbsolutePath().normalize();

                parent.validateOutputFile(targetFile, overwrite);

                DownloadResult result = parent.downloadService()
                        .downloadSegmentedViaEdges(
                                routerBaseUrl,
                                cleanRemotePath,
                                cleanRegion,
                                cleanClientId,
                                loggedInUserId,
                                targetFile,
                                overwrite,
                                segments,
                                retries,
                                edges);

                return parent.handleDownloadResult(routerBaseUrl, cleanRemotePath, targetFile, result);
            } catch (IllegalArgumentException ex) {
                return parent.validationError(ex.getMessage());
            } catch (Exception ex) {
                return parent.requestFailed("Segmented download failed: " + ex.getMessage());
            }
        }
    }
}
