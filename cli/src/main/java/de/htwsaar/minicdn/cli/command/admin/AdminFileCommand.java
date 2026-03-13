package de.htwsaar.minicdn.cli.command.admin;

import static de.htwsaar.minicdn.common.util.DefaultsURL.ROUTER_URL;
import static de.htwsaar.minicdn.common.util.ExitCodes.REQUEST_FAILED;
import static de.htwsaar.minicdn.common.util.ExitCodes.SUCCESS;
import static de.htwsaar.minicdn.common.util.ExitCodes.VALIDATION;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.dto.DownloadResult;
import de.htwsaar.minicdn.cli.service.admin.AdminFileService;
import de.htwsaar.minicdn.cli.service.user.UserFileService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import de.htwsaar.minicdn.cli.util.JsonUtils;
import de.htwsaar.minicdn.common.util.PathUtils;
import de.htwsaar.minicdn.common.util.UriUtils;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Stellt Admin-Befehle für Dateioperationen über den Router bereit.
 *
 * <p>Die Klasse bildet die CLI-Adapter-Schicht für Dateioperationen. Sie validiert
 * Benutzereingaben, normalisiert technische Parameter und delegiert die eigentliche
 * fachliche Arbeit an Services. Dadurch bleiben Transport- und Protokolldetails von
 * der aufrufenden CLI-Struktur getrennt.</p>
 */
@Command(
        name = "file",
        description = "Manage files via CDN router (origin hidden behind router).",
        mixinStandardHelpOptions = true,
        footerHeading = "%nBeispiele:%n",
        footer = {
            "  admin file upload --region EU --path docs/a.pdf --file ./a.pdf",
            "  admin file list",
            "  admin file show --path docs/a.pdf",
            "  admin file download --region EU --path docs/a.pdf --out ./a.pdf",
            "  admin file delete --region EU --path docs/a.pdf"
        },
        subcommands = {
            AdminFileCommand.AdminFileUploadCommand.class,
            AdminFileCommand.AdminFileListCommand.class,
            AdminFileCommand.AdminFileShowCommand.class,
            AdminFileCommand.AdminFileDownloadCommand.class,
            AdminFileCommand.AdminFileDeleteCommand.class
        })
public final class AdminFileCommand implements Runnable {

    /**
     * Log-Präfix für Ausgaben dieses Commands.
     */
    private static final String LOG_PREFIX = "[ADMIN]";

    /**
     * Technischer Fallback für eine nicht vorhandene Session-User-ID.
     */
    private static final long FALLBACK_USER_ID = -1L;

    /**
     * Gemeinsamer CLI-Kontext.
     */
    final CliContext ctx;

    /**
     * Wiederverwendbarer Download-Service.
     */
    private final UserFileService userFileService;

    @Spec
    private CommandSpec spec;

    /**
     * Erzeugt den Command mit gemeinsamem CLI-Kontext.
     *
     * @param ctx gemeinsamer CLI-Kontext
     */
    public AdminFileCommand(CliContext ctx) {
        this(
                ctx,
                new UserFileService(Objects.requireNonNull(ctx, "ctx").transportClient(), ctx.defaultRequestTimeout()));
    }

    /**
     * Erzeugt den Command mit explizit übergebenem Download-Service.
     *
     * <p>Dieser Konstruktor verbessert die Testbarkeit und macht Abhängigkeiten
     * im Sinne von Constructor Injection explizit.</p>
     *
     * @param ctx             gemeinsamer CLI-Kontext
     * @param userFileService Service für Download-Operationen
     */
    AdminFileCommand(CliContext ctx, UserFileService userFileService) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.userFileService = Objects.requireNonNull(userFileService, "userFileService");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    /**
     * Erzeugt einen Admin-Dateiservice für den angegebenen Router.
     *
     * <p>Der Service wird bewusst pro Aufruf aus dem aktuellen Kontext erzeugt,
     * damit Admin-Token und Session-User-ID nicht veralten.</p>
     *
     * @param routerBaseUrl Router-Basis-URL
     * @return konfigurierte Service-Instanz
     */
    AdminFileService adminFileService(URI routerBaseUrl) {
        return new AdminFileService(
                ctx.transportClient(),
                ctx.defaultRequestTimeout(),
                Objects.requireNonNull(routerBaseUrl, "routerBaseUrl"),
                ctx.adminToken(),
                resolveLoggedInUserId());
    }

    /**
     * Liefert den Download-Service für Router-Downloads.
     *
     * @return Service für Datei-Downloads
     */
    UserFileService userFileService() {
        return userFileService;
    }

    /**
     * Liefert die aktuell angemeldete User-ID oder einen technischen Fallback.
     *
     * @return User-ID oder {@code -1L}, falls keine Session-ID vorhanden ist
     */
    long resolveLoggedInUserId() {
        Long userId = ctx.sessionState().loggedInUserId();
        return userId == null ? FALLBACK_USER_ID : userId;
    }

    /**
     * Normalisiert eine Router-URL in eine konsistente Basis-URL.
     *
     * @param router rohe Router-URL
     * @return normalisierte Basis-URL mit abschließendem Slash
     */
    URI normalizeRouter(URI router) {
        return UriUtils.ensureTrailingSlash(Objects.requireNonNull(router, "router"));
    }

    /**
     * Normalisiert einen Remote-Pfad nach den gemeinsamen Regeln des Common-Moduls.
     *
     * @param rawPath roher Eingabepfad
     * @return normalisierter relativer Pfad
     * @throws IllegalArgumentException falls der Pfad ungültig ist
     */
    String normalizeRemotePath(String rawPath) {
        return PathUtils.normalizeRelativePath(rawPath);
    }

    /**
     * Validiert und normalisiert eine Region.
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
     * Gibt JSON nur dann aus, wenn ein nicht-leerer Body vorhanden ist.
     *
     * @param body JSON-Body
     */
    void printJsonIfPresent(String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        ctx.out().println(JsonUtils.formatJson(body));
        ctx.out().flush();
    }

    /**
     * Prüft, ob eine lokale Datei existiert und regulär ist.
     *
     * @param file lokale Datei
     * @throws IllegalArgumentException falls die Datei ungültig ist
     */
    void validateReadableFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("local file does not exist or is not a regular file: " + file);
        }
    }

    /**
     * Prüft, ob die angegebene Ausgabedatei grundsätzlich beschreibbar ist.
     *
     * @param outFile   Zieldatei
     * @param overwrite {@code true}, wenn Überschreiben erlaubt ist
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
     * Gibt einen Validierungsfehler einheitlich aus.
     *
     * @param message Fehlermeldung
     * @return Exit-Code für Validierungsfehler
     */
    int validationError(String message) {
        ConsoleUtils.error(ctx.err(), "%s %s", LOG_PREFIX, Objects.toString(message, "invalid input"));
        return VALIDATION.code();
    }

    /**
     * Gibt einen Request-Fehler einheitlich aus.
     *
     * @param pattern Formatmuster
     * @param args    Formatargumente
     * @return Exit-Code für Request-Fehler
     */
    int requestFailed(String pattern, Object... args) {
        ConsoleUtils.error(ctx.err(), pattern, args);
        return REQUEST_FAILED.code();
    }

    /**
     * Upload einer Datei über den Router.
     */
    @Command(
            name = "upload",
            description = "Upload a file via router (origin + cache invalidation).",
            mixinStandardHelpOptions = true)
    public static final class AdminFileUploadCommand implements Callable<Integer> {

        @ParentCommand
        private AdminFileCommand parent;

        @Option(
                names = "--router",
                defaultValue = ROUTER_URL,
                paramLabel = "ROUTER_URL",
                description = "Router base URL (default: ${DEFAULT-VALUE}).")
        private URI router;

        @Option(
                names = "--region",
                required = true,
                paramLabel = "REGION",
                description = "Region for cache invalidation. Example: EU")
        private String region;

        @Option(
                names = "--path",
                required = true,
                paramLabel = "REMOTE_PATH",
                description = "Remote file path. Example: docs/a.pdf")
        private String path;

        @Option(
                names = "--file",
                required = true,
                paramLabel = "LOCAL_FILE",
                description = "Local file path. Example: ./a.pdf")
        private Path file;

        @Override
        public Integer call() {
            try {
                URI baseRouter = parent.normalizeRouter(router);
                String cleanRegion = parent.normalizeRegion(region);
                String cleanPath = parent.normalizeRemotePath(path);
                parent.validateReadableFile(file);

                CallResult result = parent.adminFileService(baseRouter).uploadViaRouter(cleanPath, file, cleanRegion);

                if (result.is2xx()) {
                    ConsoleUtils.info(
                            parent.ctx.out(),
                            "%s Upload via router successful HTTP %d path=%s region=%s",
                            LOG_PREFIX,
                            result.statusCode(),
                            cleanPath,
                            cleanRegion);
                    return SUCCESS.code();
                }

                return parent.requestFailed(
                        "%s Upload via router failed HTTP %d error=%s body=%s path=%s region=%s",
                        LOG_PREFIX, result.statusCode(), result.error(), result.body(), cleanPath, cleanRegion);
            } catch (IllegalArgumentException ex) {
                return parent.validationError(ex.getMessage());
            }
        }
    }

    /**
     * Listet Dateien über die Admin-API des Routers auf.
     */
    @Command(
            name = "list",
            description = "List files via router (router asks origin).",
            mixinStandardHelpOptions = true)
    public static final class AdminFileListCommand implements Callable<Integer> {

        @ParentCommand
        private AdminFileCommand parent;

        @Option(
                names = "--router",
                defaultValue = ROUTER_URL,
                paramLabel = "ROUTER_URL",
                description = "Router base URL (default: ${DEFAULT-VALUE}).")
        private URI router;

        @Override
        public Integer call() {
            try {
                URI baseRouter = parent.normalizeRouter(router);
                CallResult result = parent.adminFileService(baseRouter).listFilesRaw();

                if (result.error() != null) {
                    return parent.requestFailed("%s File list failed: %s", LOG_PREFIX, result.error());
                }

                if (!result.is2xx()) {
                    return parent.requestFailed(
                            "%s File list failed HTTP %d body=%s", LOG_PREFIX, result.statusCode(), result.body());
                }

                if (result.body() == null || result.body().isBlank()) {
                    parent.ctx.out().println(LOG_PREFIX + " Files: (none)");
                    parent.ctx.out().flush();
                    return SUCCESS.code();
                }

                parent.printJsonIfPresent(result.body());
                return SUCCESS.code();
            } catch (IllegalArgumentException ex) {
                return parent.validationError(ex.getMessage());
            }
        }
    }

    /**
     * Zeigt Metadaten einer Datei über den Router an.
     */
    @Command(name = "show", description = "Show file metadata via router.", mixinStandardHelpOptions = true)
    public static final class AdminFileShowCommand implements Callable<Integer> {

        @ParentCommand
        private AdminFileCommand parent;

        @Option(
                names = "--router",
                defaultValue = ROUTER_URL,
                paramLabel = "ROUTER_URL",
                description = "Router base URL (default: ${DEFAULT-VALUE}).")
        private URI router;

        @Option(
                names = "--path",
                required = true,
                paramLabel = "REMOTE_PATH",
                description = "Remote file path. Example: docs/a.pdf")
        private String path;

        @Override
        public Integer call() {
            try {
                URI baseRouter = parent.normalizeRouter(router);
                String cleanPath = parent.normalizeRemotePath(path);

                CallResult result = parent.adminFileService(baseRouter).showViaRouter(cleanPath);

                if (result.is2xx()) {
                    ConsoleUtils.info(
                            parent.ctx.out(),
                            "%s Show via router successful HTTP %d router=%s path=%s",
                            LOG_PREFIX,
                            result.statusCode(),
                            baseRouter,
                            cleanPath);
                    parent.printJsonIfPresent(result.body());
                    return SUCCESS.code();
                }

                return parent.requestFailed(
                        "%s Show via router failed HTTP %d error=%s body=%s router=%s path=%s",
                        LOG_PREFIX, result.statusCode(), result.error(), result.body(), baseRouter, cleanPath);
            } catch (IllegalArgumentException ex) {
                return parent.validationError(ex.getMessage());
            }
        }
    }

    /**
     * Lädt eine Datei über den Router herunter.
     */
    @Command(
            name = "download",
            description = "Download a file via router (same flow as user download).",
            mixinStandardHelpOptions = true)
    public static final class AdminFileDownloadCommand implements Callable<Integer> {

        @ParentCommand
        private AdminFileCommand parent;

        @Option(
                names = "--router",
                defaultValue = ROUTER_URL,
                paramLabel = "ROUTER_URL",
                description = "Router base URL (default: ${DEFAULT-VALUE}).")
        private URI router;

        @Option(names = "--region", required = true, paramLabel = "REGION", description = "Target region. Example: EU")
        private String region;

        @Option(
                names = "--path",
                required = true,
                paramLabel = "REMOTE_PATH",
                description = "Remote file path. Example: docs/a.pdf")
        private String path;

        @Option(
                names = "--out",
                required = true,
                paramLabel = "OUT_FILE",
                description = "Output file path. Example: ./a.pdf")
        private Path outFile;

        @Option(
                names = "--overwrite",
                defaultValue = "false",
                description = "Overwrite output file if it already exists (default: ${DEFAULT-VALUE}).")
        private boolean overwrite;

        @Override
        public Integer call() {
            try {
                URI baseRouter = parent.normalizeRouter(router);
                String cleanRegion = parent.normalizeRegion(region);
                String cleanPath = parent.normalizeRemotePath(path);
                parent.validateOutputFile(outFile, overwrite);

                DownloadResult result = parent.userFileService()
                        .downloadViaRouter(baseRouter, cleanPath, cleanRegion, null, outFile, overwrite);

                if (result.error() != null) {
                    return parent.requestFailed("%s Download via router failed: %s", LOG_PREFIX, result.error());
                }

                Integer statusCode = result.statusCode();
                if (result.is2xx()) {
                    ConsoleUtils.info(
                            parent.ctx.out(),
                            "%s Download via router successful HTTP %d router=%s path=%s out=%s bytes=%d",
                            LOG_PREFIX,
                            statusCode,
                            baseRouter,
                            cleanPath,
                            outFile,
                            result.bytesWritten());
                    return SUCCESS.code();
                }

                return parent.requestFailed(
                        "%s Download via router failed HTTP %s path=%s out=%s",
                        LOG_PREFIX, statusCode, cleanPath, outFile);
            } catch (IllegalArgumentException ex) {
                return parent.validationError(ex.getMessage());
            }
        }
    }

    /**
     * Löscht eine Datei über den Router und stößt die Cache-Invalidierung an.
     */
    @Command(
            name = "delete",
            description = "Delete a file via router (origin + cache invalidation).",
            mixinStandardHelpOptions = true)
    public static final class AdminFileDeleteCommand implements Callable<Integer> {

        @ParentCommand
        private AdminFileCommand parent;

        @Option(
                names = "--router",
                defaultValue = ROUTER_URL,
                paramLabel = "ROUTER_URL",
                description = "Router base URL (default: ${DEFAULT-VALUE}).")
        private URI router;

        @Option(
                names = "--region",
                required = true,
                paramLabel = "REGION",
                description = "Region for cache invalidation. Example: EU")
        private String region;

        @Option(
                names = "--path",
                required = true,
                paramLabel = "REMOTE_PATH",
                description = "Remote file path. Example: docs/a.pdf")
        private String path;

        @Override
        public Integer call() {
            try {
                URI baseRouter = parent.normalizeRouter(router);
                String cleanRegion = parent.normalizeRegion(region);
                String cleanPath = parent.normalizeRemotePath(path);

                CallResult result = parent.adminFileService(baseRouter).deleteViaRouter(cleanPath, cleanRegion);

                if (result.is2xx()) {
                    ConsoleUtils.info(
                            parent.ctx.out(),
                            "%s Delete via router successful HTTP %d path=%s region=%s",
                            LOG_PREFIX,
                            result.statusCode(),
                            cleanPath,
                            cleanRegion);
                    return SUCCESS.code();
                }

                return parent.requestFailed(
                        "%s Delete via router failed HTTP %d error=%s body=%s path=%s region=%s",
                        LOG_PREFIX, result.statusCode(), result.error(), result.body(), cleanPath, cleanRegion);
            } catch (IllegalArgumentException ex) {
                return parent.validationError(ex.getMessage());
            }
        }
    }
}
