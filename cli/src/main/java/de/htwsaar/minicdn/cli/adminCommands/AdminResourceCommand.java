package de.htwsaar.minicdn.cli.adminCommands;

import de.htwsaar.minicdn.cli.service.AdminResourceService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.Callable;

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

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "add", description = "Upload a file to the Origin server (admin API)")
    public static class AdminResourceAddCommand implements Callable<Integer> {

        @Option(names = "--path", required = true,
                description = "Target path on origin, e.g. docs/Lebenslauf.pdf (stored under origin's data/ directory)")
        String path;

        @Option(names = "--origin", required = true,
                description = "Origin server base URL, e.g. http://localhost:8080")
        String origin;

        @Option(names = "--file", required = true,
                description = "Local file path to upload, e.g. /Users/.../Lebenslauf.pdf")
        Path file;

        @Override
        public Integer call() {
            // Validate a local file
            if (file == null || !Files.exists(file) || !Files.isRegularFile(file)) {
                System.err.println("[ADMIN] Local file does not exist or is not a regular file: " + file);
                return 1;
            }

            // Normalize a path: remove leading slash and optional "origin/" or "data/" prefixes
            String cleanPath = (path == null) ? "" : path;
            // remove the leading slash if present
            if (cleanPath.startsWith("/")) {
                cleanPath = cleanPath.substring(1);
            }
            // remove optional "origin/" and/or "data/" prefixes
            cleanPath = cleanPath.replaceFirst("^(origin/)?(data/)?", "");

            if (cleanPath.isBlank()) {
                System.err.println("[ADMIN] Invalid target path after normalization: " + path);
                return 1;
            }

            AdminResourceService service = new AdminResourceService();
            int rc = service.uploadToOrigin(origin, cleanPath, file);

            if (rc != 0) {
                System.err.printf("[ADMIN] Upload failed: origin=%s, path=%s, file=%s%n",
                        origin, cleanPath, file);
            } else {
                System.out.printf("[ADMIN] Upload succeeded: origin=%s, path=%s, file=%s%n",
                        origin, cleanPath, file);
            }

            return rc;
        }
    }

    @Command(name = "update", description = "Update resource configuration")
    public static class AdminResourceUpdateCommand implements Runnable {

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
            // TODO: ResourceService.update(...)
            System.out.printf("[ADMIN] Update resource %d (path=%s, origin=%s, ttl=%s)%n", id, path, origin, cacheTtl);
        }
    }

    @Command(name = "delete", description = "Delete a resource")
    public static class AdminResourceDeleteCommand implements Runnable {

        @Option(names = "--id", required = true, description = "Resource ID")
        long id;

        @Override
        public void run() {
            // TODO: ResourceService.delete(id)
            System.out.printf("[ADMIN] Delete resource %d%n", id);
        }
    }

    @Command(name = "list", description = "List resources")
    public static class AdminResourceListCommand implements Runnable {

        @Option(names = "--page", description = "Page number", defaultValue = "1")
        int page;

        @Option(names = "--size", description = "Page size", defaultValue = "20")
        int size;

        @Override
        public void run() {
            // TODO: ResourceService.list(page, size)
            System.out.printf("[ADMIN] List resources page=%d size=%d%n", page, size);
        }
    }

    @Command(name = "show", description = "Show resource details")
    public static class AdminResourceShowCommand implements Runnable {

        @Option(names = "--id", required = true, description = "Resource ID")
        long id;

        @Override
        public void run() {
            // TODO: ResourceService.show(id)
            System.out.printf("[ADMIN] Show resource %d%n", id);
        }
    }
}
