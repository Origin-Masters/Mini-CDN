package de.htwsaar.minicdn.cli.command.admin;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.HttpCallResult;
import de.htwsaar.minicdn.cli.service.admin.AdminFileService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import de.htwsaar.minicdn.cli.util.JsonUtils;
import de.htwsaar.minicdn.cli.util.PathUtils;
import de.htwsaar.minicdn.cli.util.UriUtils;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * Top-Level Command: admin file
 */
@Command(
        name = "file",
        description = "Manage files via CDN router (Origin hidden behind router).",
        mixinStandardHelpOptions = true,
        footerHeading = "\nBeispiele%n",
        footer = {
            "  admin file upload --router http://localhost:8082 --region EU --path docs/a.pdf --file ./a.pdf",
            "  admin file list --router http://localhost:8082",
            "  admin file show --router http://localhost:8082 --path docs/a.pdf",
            "  admin file download --router http://localhost:8082 --path docs/a.pdf --out ./a.pdf",
            "  admin file delete --router http://localhost:8082 --region EU --path docs/a.pdf"
        },
        subcommands = {
            AdminFileCommand.AdminFileUploadCommand.class,
            AdminFileCommand.AdminFileListCommand.class,
            AdminFileCommand.AdminFileShowCommand.class,
            AdminFileCommand.AdminFileDownloadCommand.class,
            AdminFileCommand.AdminFileDeleteCommand.class
        })
public class AdminFileCommand implements Runnable {

    final CliContext ctx;

    public AdminFileCommand(CliContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void run() {
        ConsoleUtils.info(ctx.out(), new CommandLine(this).getUsageMessage());
    }

    AdminFileService service() {
        return new AdminFileService(ctx.transportClient(), ctx.defaultRequestTimeout());
    }

    // Upload über den Router zum Origin + Cache Invalidation (Region erforderlich, da Invalidation über Router
    // erfolgt).
    @Command(
            name = "upload",
            description = "Upload a file via router (origin + cache invalidation).",
            mixinStandardHelpOptions = true)
    public static class AdminFileUploadCommand implements Callable<Integer> {

        @ParentCommand
        AdminFileCommand parent;

        @Option(
                names = {"--router"},
                required = true,
                paramLabel = "ROUTER_URL",
                description = "Router base URL, e.g. http://localhost:8082")
        URI router;

        @Option(
                names = {"--region"},
                required = true,
                paramLabel = "REGION",
                description = "Region for cache invalidation, e.g. EU")
        String region;

        @Option(
                names = {"--path"},
                required = true,
                paramLabel = "REMOTE_PATH, e.g. docs/a.pdf")
        String path;

        @Option(
                names = {"--file"},
                required = true,
                paramLabel = "LOCAL_FILEPATH, e,g ./a.pdf")
        Path file;

        @Override
        public Integer call() {
            if (file == null || !Files.exists(file) || !Files.isRegularFile(file)) {
                ConsoleUtils.error(
                        parent.ctx.err(), "[ADMIN] Local file does not exist or is not a regular file: %s", file);
                return 1;
            }
            String cleanPath = PathUtils.normalizePath(path);
            if (cleanPath.isBlank()) {
                ConsoleUtils.error(
                        parent.ctx.err(), "[ADMIN] Invalid path %s after normalization: %s", path, cleanPath);
                return 1;
            }

            HttpCallResult result = parent.service().uploadViaRouter(router, cleanPath, file, region);

            if (result.is2xx()) {
                ConsoleUtils.info(
                        parent.ctx.out(),
                        "[ADMIN] Upload via router successful HTTP %d path=%s region=%s",
                        result.statusCode(),
                        cleanPath,
                        region);
                return 0;
            }
            ConsoleUtils.error(
                    parent.ctx.err(),
                    "[ADMIN] Upload via router failed HTTP %d error=%s body=%s path=%s region=%s",
                    result.statusCode(),
                    result.error(),
                    result.body(),
                    cleanPath,
                    region);
            return 2;
        }
    }

    // list: über die Admin-API des Routers (der Router fragt den Origin).
    @Command(
            name = "list",
            description = "List files via router (router asks origin).",
            mixinStandardHelpOptions = true)
    public static class AdminFileListCommand implements Callable<Integer> {

        @ParentCommand
        AdminFileCommand parent;

        @Option(
                names = {"--router"},
                required = true,
                paramLabel = "ROUTER_URL")
        URI router;

        @Option(
                names = {"--page"},
                defaultValue = "1")
        int page;

        @Option(
                names = {"--size"},
                defaultValue = "20")
        int size;

        @Override
        public Integer call() {
            HttpCallResult result = parent.service().listViaRouter(router, page, size);
            var out = parent.ctx.out();
            var err = parent.ctx.err();

            if (result.is2xx()) {
                ConsoleUtils.info(
                        err,
                        "[ADMIN] List via router successful HTTP %d router=%s page=%d size=%d",
                        result.statusCode(),
                        router,
                        page,
                        size);
                if (result.body() != null && !result.body().isBlank()) {
                    out.println(JsonUtils.formatJson(result.body()));
                    out.flush();
                }
                return 0;
            }
            ConsoleUtils.error(
                    err,
                    "[ADMIN] List via router failed HTTP %d error=%s body=%s router=%s page=%d size=%d",
                    result.statusCode(),
                    result.error(),
                    result.body(),
                    router,
                    page,
                    size);
            return 2;
        }
    }

    // Show: Router leitet HEAD an Origin weiter und gibt Metadaten zurück (z.B. Content-Length, Last-Modified, ETag).
    // Keine Region nötig, da keine Cache-Invalidation.
    @Command(name = "show", description = "Show file metadata via router.", mixinStandardHelpOptions = true)
    public static class AdminFileShowCommand implements Callable<Integer> {

        @ParentCommand
        AdminFileCommand parent;

        @Option(
                names = {"--router"},
                required = true,
                paramLabel = "ROUTER_URL")
        URI router;

        @Option(
                names = {"--path"},
                required = true,
                paramLabel = "REMOTE_PATH")
        String path;

        @Override
        public Integer call() {
            String cleanPath = PathUtils.normalizePath(path);
            if (cleanPath.isBlank()) {
                ConsoleUtils.error(
                        parent.ctx.err(), "[ADMIN] Invalid path %s after normalization: %s", path, cleanPath);
                return 1;
            }

            HttpCallResult result = parent.service().showViaRouter(router, cleanPath);
            var out = parent.ctx.out();
            var err = parent.ctx.err();

            if (result.is2xx()) {
                ConsoleUtils.info(
                        err,
                        "[ADMIN] Show via router successful HTTP %d router=%s path=%s",
                        result.statusCode(),
                        router,
                        cleanPath);
                if (result.body() != null && !result.body().isBlank()) {
                    out.println(JsonUtils.formatJson(result.body()));
                    out.flush();
                }
                return 0;
            }
            ConsoleUtils.error(
                    err,
                    "[ADMIN] Show via router failed HTTP %d error=%s body=%s router=%s path=%s",
                    result.statusCode(),
                    result.error(),
                    result.body(),
                    router,
                    cleanPath);
            return 2;
        }
    }

    // Download: über den Router zum Origin (gleicher Flow wie User-Download). Keine Region nötig, da keine
    // Cache-Invalidation.
    @Command(
            name = "download",
            description = "Download a file via router (same flow as user download).",
            mixinStandardHelpOptions = true)
    public static class AdminFileDownloadCommand implements Callable<Integer> {

        @ParentCommand
        AdminFileCommand parent;

        @Option(
                names = {"--router"},
                required = true,
                paramLabel = "ROUTER_URL")
        URI router;

        @Option(
                names = {"--region"},
                required = true,
                paramLabel = "REGION")
        String region;

        @Option(
                names = {"--path"},
                required = true,
                paramLabel = "REMOTE_PATH")
        String path;

        @Option(
                names = {"--out"},
                required = true,
                paramLabel = "OUT_FILE")
        Path outFile;

        @Option(
                names = {"--overwrite"},
                defaultValue = "false")
        boolean overwrite;

        @Override
        public Integer call() {
            String cleanPath = PathUtils.normalizePath(path);
            if (cleanPath.isBlank()) {
                ConsoleUtils.error(
                        parent.ctx.err(), "[ADMIN] Invalid path %s after normalization: %s", path, cleanPath);
                return 1;
            }

            var userFileService = new de.htwsaar.minicdn.cli.service.user.UserFileService(
                    parent.ctx.transportClient(), parent.ctx.defaultRequestTimeout());

            var base = UriUtils.ensureTrailingSlash(router);
            var result = userFileService.downloadViaRouter(
                    base,
                    cleanPath,
                    region,
                    null, // kein clientId, damit die Statistik im Router nicht unnötig aufgebläht wird (Admin-Downloads
                    // landen dann in der gleichen Statistik wie User-Downloads ohne clientId)
                    outFile,
                    overwrite);

            if (result.error() != null) {
                ConsoleUtils.error(parent.ctx.err(), "[ADMIN] Download via router failed: %s", result.error());
                return 2;
            }
            ConsoleUtils.info(
                    parent.ctx.out(),
                    "[ADMIN] Download via router successful HTTP %d router=%s path=%s out=%s",
                    result.statusCode(),
                    router,
                    cleanPath,
                    outFile);
            return 0;
        }
    }

    // delete: über den Router zum Origin + Cache Invalidation (Region erforderlich, da Invalidation über Router
    // erfolgt).
    @Command(
            name = "delete",
            description = "Delete a file via router (origin + cache invalidation).",
            mixinStandardHelpOptions = true)
    public static class AdminFileDeleteCommand implements Callable<Integer> {

        @ParentCommand
        AdminFileCommand parent;

        @Option(
                names = {"--router"},
                required = true,
                paramLabel = "ROUTER_URL")
        URI router;

        @Option(
                names = {"--region"},
                required = true,
                paramLabel = "REGION")
        String region;

        @Option(
                names = {"--path"},
                required = true,
                paramLabel = "REMOTE_PATH")
        String path;

        @Override
        public Integer call() {
            String cleanPath = PathUtils.normalizePath(path);
            if (cleanPath.isBlank()) {
                ConsoleUtils.error(
                        parent.ctx.err(), "[ADMIN] Invalid path %s after normalization: %s", path, cleanPath);
                return 1;
            }

            HttpCallResult result = parent.service().deleteViaRouter(router, cleanPath, region);
            var err = parent.ctx.err();

            if (result.is2xx()) {
                ConsoleUtils.info(
                        err,
                        "[ADMIN] Delete via router successful HTTP %d path=%s region=%s",
                        result.statusCode(),
                        cleanPath,
                        region);
                return 0;
            }
            ConsoleUtils.error(
                    err,
                    "[ADMIN] Delete via router failed HTTP %d error=%s body=%s path=%s region=%s",
                    result.statusCode(),
                    result.error(),
                    result.body(),
                    cleanPath,
                    region);
            return 2;
        }
    }
}
