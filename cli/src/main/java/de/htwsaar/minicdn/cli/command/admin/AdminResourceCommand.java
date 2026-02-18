package de.htwsaar.minicdn.cli.command.admin;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.service.admin.AdminResourceService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import de.htwsaar.minicdn.cli.util.PathUtils;
import java.io.FileNotFoundException;
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
 * Admin-Command zur Verwaltung von CDN-Ressourcen.
 *
 * <p>Ohne Subcommand wird die Usage angezeigt.
 * Die eigentlichen Aktionen sind (teilweise) noch Stubs bzw. delegieren an Services.
 */
@Command(
        name = "resource",
        description = "Manage CDN resources",
        subcommands = {
            AdminResourceCommand.AdminResourceAddCommand.class,
            AdminResourceCommand.AdminResourceUpdateCommand.class,
            AdminResourceCommand.AdminResourceDeleteCommand.class,
            AdminResourceCommand.AdminResourceListCommand.class,
            AdminResourceCommand.AdminResourceShowCommand.class
        })
public final class AdminResourceCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    /**
     * Konstruktor für Constructor Injection via {@code ContextFactory}.
     *
     * @param ctx CLI-Kontext (Output, HTTP-Client, Timeouts, ...)
     */
    public AdminResourceCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    /**
     * Erstellt den Service für Admin-Resource-Operationen.
     *
     * <p>Hinweis: Aktuell wird pro Aufruf eine neue Instanz erstellt (leichtgewichtig).
     */
    private AdminResourceService service() {
        return new AdminResourceService(ctx.httpClient(), ctx.defaultRequestTimeout());
    }

    @Command(name = "add", description = "Upload a file to the Origin server (admin API)")
    public static final class AdminResourceAddCommand implements Callable<Integer> {

        @ParentCommand
        private AdminResourceCommand parent;

        @Option(
                names = "--path",
                required = true,
                description = "Target path on origin, e.g. docs/Lebenslauf.pdf (stored under origin's data/ directory)")
        private String path;

        @Option(names = "--origin", required = true, description = "Origin server base URL, e.g. http://localhost:8080")
        private URI origin;

        @Option(
                names = "--file",
                required = true,
                description = "Local file path to upload, e.g. /Users/.../Lebenslauf.pdf")
        private Path file;

        @Override
        public Integer call() throws FileNotFoundException {
            if (file == null || !Files.exists(file) || !Files.isRegularFile(file)) {
                ConsoleUtils.error(
                        parent.ctx.err(), "[ADMIN] Local file does not exist or is not a regular file: %s", file);
                return 1;
            }

            String cleanPath = PathUtils.normalizePath(path);
            if (cleanPath.isBlank()) {
                ConsoleUtils.error(
                        parent.ctx.err(), "[ADMIN] Invalid path: '%s' (after normalization: '%s')", path, cleanPath);
                return 1;
            }

            var result = parent.service().uploadToOrigin(origin, cleanPath, file);
            int rc = result.is2xx() ? 0 : 2;

            if (rc == 0) {
                ConsoleUtils.info(
                        parent.ctx.out(),
                        "[ADMIN] Upload successful: status=%s origin=%s path=%s file=%s",
                        result.statusCode(),
                        origin,
                        cleanPath,
                        file);
                return 0;
            }

            ConsoleUtils.error(
                    parent.ctx.err(),
                    "[ADMIN] Upload failed: status=%s error=%s body=%s origin=%s path=%s file=%s",
                    result.statusCode(),
                    result.error(),
                    result.body(),
                    origin,
                    cleanPath,
                    file);
            return rc;
        }
    }

    @Command(name = "update", description = "Update resource configuration")
    public static final class AdminResourceUpdateCommand implements Runnable {

        @ParentCommand
        private AdminResourceCommand parent;

        @Option(names = "--id", required = true, description = "Resource ID")
        private long id;

        @Option(names = "--path", description = "New path (optional)")
        private String path;

        @Option(names = "--origin", description = "New origin URL (optional)")
        private String origin;

        @Option(names = "--cache-ttl", description = "New cache TTL in seconds (optional)")
        private Integer cacheTtl;

        @Override
        public void run() {
            ConsoleUtils.info(
                    parent.ctx.out(),
                    "[ADMIN] Update resource %d (path=%s, origin=%s, ttl=%s)",
                    id,
                    path,
                    origin,
                    cacheTtl);
        }
    }

    @Command(name = "delete", description = "Delete a resource")
    public static final class AdminResourceDeleteCommand implements Runnable {

        @ParentCommand
        private AdminResourceCommand parent;

        @Option(names = "--id", required = true, description = "Resource ID")
        private long id;

        @Override
        public void run() {
            ConsoleUtils.info(parent.ctx.out(), "[ADMIN] Delete resource %d", id);
        }
    }

    @Command(name = "list", description = "List resources")
    public static final class AdminResourceListCommand implements Runnable {

        @ParentCommand
        private AdminResourceCommand parent;

        @Option(names = "--page", description = "Page number", defaultValue = "1")
        private int page;

        @Option(names = "--size", description = "Page size", defaultValue = "20")
        private int size;

        @Override
        public void run() {
            ConsoleUtils.info(parent.ctx.out(), "[ADMIN] List resources page=%d size=%d", page, size);
        }
    }

    @Command(name = "show", description = "Show resource details")
    public static final class AdminResourceShowCommand implements Runnable {

        @ParentCommand
        private AdminResourceCommand parent;

        @Option(names = "--id", required = true, description = "[ADMIN] Resource ID")
        private long id;

        @Override
        public void run() {
            ConsoleUtils.info(parent.ctx.out(), "[ADMIN] Show resource %d", id);
        }
    }
}
