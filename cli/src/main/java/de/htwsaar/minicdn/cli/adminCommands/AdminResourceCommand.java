package de.htwsaar.minicdn.cli.adminCommands;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.service.AdminResourceService;
import java.io.FileNotFoundException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

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
public class AdminResourceCommand implements Runnable {

    final CliContext ctx;

    public AdminResourceCommand(CliContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void run() {
        new CommandLine(this).usage(ctx.out());
        ctx.out().flush();
    }

    AdminResourceService service() {
        return new AdminResourceService(ctx.httpClient(), ctx.defaultRequestTimeout());
    }

    @Command(name = "add", description = "Upload a file to the Origin server (admin API)")
    public static class AdminResourceAddCommand implements Callable<Integer> {

        @CommandLine.ParentCommand
        AdminResourceCommand parent;

        @Option(
                names = "--path",
                required = true,
                description = "Target path on origin, e.g. docs/Lebenslauf.pdf (stored under origin's data/ directory)")
        String path;

        @Option(names = "--origin", required = true, description = "Origin server base URL, e.g. http://localhost:8080")
        URI origin;

        @Option(
                names = "--file",
                required = true,
                description = "Local file path to upload, e.g. /Users/.../Lebenslauf.pdf")
        Path file;

        @Override
        public Integer call() throws FileNotFoundException {
            // 1) Validate local file (so we don't fail with a cryptic IO error later)
            if (file == null || !Files.exists(file) || !Files.isRegularFile(file)) {
                parent.ctx.err().println("[ADMIN] Local file does not exist or is not a regular file: " + file);
                parent.ctx.err().flush();
                return 1;
            }

            // 2) Compute + validate cleanPath (this was missing)
            String cleanPath = normalizePath(path);
            if (cleanPath.isBlank()) {
                parent.ctx.err().println("[ADMIN] Invalid target path after normalization: " + path);
                parent.ctx.err().flush();
                return 1;
            }

            // 3) Upload
            var result = parent.service().uploadToOrigin(origin, cleanPath, file);
            int rc = result.is2xx() ? 0 : 2;

            if (rc == 0) {
                parent.ctx.out().printf("[ADMIN] Upload OK: origin=%s, path=%s, file=%s%n", origin, cleanPath, file);
                parent.ctx.out().flush();
                return 0;
            }

            parent.ctx
                    .err()
                    .printf(
                            "[ADMIN] Upload failed: status=%s error=%s body=%s origin=%s path=%s file=%s%n",
                            result.statusCode(), result.error(), result.body(), origin, cleanPath, file);
            parent.ctx.err().flush();
            return rc;
        }

        private static String normalizePath(String raw) {
            String clean = (raw == null) ? "" : raw.trim();
            if (clean.startsWith("/")) clean = clean.substring(1);
            return clean.replaceFirst("^(origin/)?(data/)?", "");
        }
    }

    @Command(name = "update", description = "Update resource configuration")
    public static class AdminResourceUpdateCommand implements Runnable {
        @CommandLine.ParentCommand
        AdminResourceCommand parent;

        @Option(names = "--id", required = true, description = "Resource ID")
        long id;

        @Option(names = "--path", description = "New path (optional)")
        String path;

        @Option(names = "--origin", description = "New origin URL (optional)")
        String origin;

        @Option(names = "--cache-ttl", description = "New cache TTL in seconds (optional)")
        Integer cacheTtl;

        @Override
        public void run() {
            parent.ctx
                    .out()
                    .printf("[ADMIN] Update resource %d (path=%s, origin=%s, ttl=%s)%n", id, path, origin, cacheTtl);
            parent.ctx.out().flush();
        }
    }

    @Command(name = "delete", description = "Delete a resource")
    public static class AdminResourceDeleteCommand implements Runnable {
        @CommandLine.ParentCommand
        AdminResourceCommand parent;

        @Option(names = "--id", required = true, description = "Resource ID")
        long id;

        @Override
        public void run() {
            parent.ctx.out().printf("[ADMIN] Delete resource %d%n", id);
            parent.ctx.out().flush();
        }
    }

    @Command(name = "list", description = "List resources")
    public static class AdminResourceListCommand implements Runnable {
        @CommandLine.ParentCommand
        AdminResourceCommand parent;

        @Option(names = "--page", description = "Page number", defaultValue = "1")
        int page;

        @Option(names = "--size", description = "Page size", defaultValue = "20")
        int size;

        @Override
        public void run() {
            parent.ctx.out().printf("[ADMIN] List resources page=%d size=%d%n", page, size);
            parent.ctx.out().flush();
        }
    }

    @Command(name = "show", description = "Show resource details")
    public static class AdminResourceShowCommand implements Runnable {
        @CommandLine.ParentCommand
        AdminResourceCommand parent;

        @Option(names = "--id", required = true, description = "Resource ID")
        long id;

        @Override
        public void run() {
            parent.ctx.out().printf("[ADMIN] Show resource %d%n", id);
            parent.ctx.out().flush();
        }
    }
}
