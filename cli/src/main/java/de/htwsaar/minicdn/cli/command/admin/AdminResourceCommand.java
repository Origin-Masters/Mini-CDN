package de.htwsaar.minicdn.cli.command.admin;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.HttpCallResult;
import de.htwsaar.minicdn.cli.service.admin.AdminResourceService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import de.htwsaar.minicdn.cli.util.JsonUtils;
import de.htwsaar.minicdn.cli.util.PathUtils;

import java.io.FileNotFoundException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "file",
        description = "Manage files on Origin server.",
        mixinStandardHelpOptions = true,
        footerHeading = "%nBeispiele:%n",
        footer = {
            "  minicdn admin file upload --origin http://localhost:8080 --path docs/Lebenslauf.pdf --file ./Lebenslauf.pdf",
            "  minicdn admin file list --origin http://localhost:8080 --page 1 --size 20",
            "  minicdn admin file show --origin http://localhost:8080 --path docs/Lebenslauf.pdf"
        },
        subcommands = {
                AdminResourceCommand.AdminResourceUploadCommand.class,
                AdminResourceCommand.AdminResourceListCommand.class,
                AdminResourceCommand.AdminResourceShowCommand.class,
                AdminResourceCommand.AdminResourceDownloadCommand.class
        })
public class AdminResourceCommand implements Runnable {

    final CliContext ctx;

    public AdminResourceCommand(CliContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void run() {
        ConsoleUtils.info(ctx.out(), new CommandLine(this).getUsageMessage());
    }

    AdminResourceService service() {
        return new AdminResourceService(ctx.httpClient(), ctx.defaultRequestTimeout());
    }

    @Command(
            name = "upload",
            description = "Upload a file to the Origin server.",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  minicdn admin file upload --origin http://localhost:8080 --path docs/Lebenslauf.pdf --file ./Lebenslauf.pdf"
            })
    public static class AdminResourceUploadCommand implements Callable<Integer> {

        @CommandLine.ParentCommand
        AdminResourceCommand parent;

        @Option(
                names = "--path",
                required = true,
                paramLabel = "REMOTE_PATH",
                description = "Target path on origin, e.g. docs/Lebenslauf.pdf (stored under origin's data/ directory)")
        String path;

        @Option(
                names = "--origin",
                required = true,
                paramLabel = "ORIGIN_URL",
                description = "Origin server base URL, e.g. http://localhost:8080")
        URI origin;

        @Option(
                names = "--file",
                required = true,
                paramLabel = "LOCAL_FILE",
                description = "Local file path to upload, e.g. /Users/.../Lebenslauf.pdf")
        Path file;

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

            HttpCallResult result = parent.service().uploadToOrigin(origin, cleanPath, file);
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

    @Command(
            name = "list",
            description = "List files on Origin server with pagination.",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  minicdn admin file list --origin http://localhost:8080",
                "  minicdn admin file list --origin http://localhost:8080 --page 2 --size 50"
            })
    public static class AdminResourceListCommand implements Callable<Integer> {

        @CommandLine.ParentCommand
        AdminResourceCommand parent;

        @Option(
                names = "--origin",
                required = true,
                paramLabel = "ORIGIN_URL",
                description = "Origin server base URL, e.g. http://localhost:8080")
        URI origin;

        @Option(names = "--page", description = "Page number (>= 1)", defaultValue = "1")
        int page;

        @Option(names = "--size", description = "Page size (> 0)", defaultValue = "20")
        int size;

        @Override
        public Integer call() {
            HttpCallResult result = parent.service().listOriginFiles(origin, page, size);

            var out = parent.ctx.out();
            var err = parent.ctx.err();

            if (result.is2xx()) {
                ConsoleUtils.info(
                        err,
                        "List files successful: status=%s origin=%s page=%s size=%s",
                        result.statusCode(),
                        origin,
                        page,
                        size);

                String body = result.body();
                if (body != null && !body.isBlank()) {
                    out.println(JsonUtils.formatJson(body));
                    out.flush();
                }
                return 0;
            }

            ConsoleUtils.error(
                    err,
                    "List files failed: status=%s error=%s body=%s origin=%s page=%s size=%s",
                    result.statusCode(),
                    result.error(),
                    result.body(),
                    origin,
                    page,
                    size);
            return 2;
        }
    }

    @Command(
            name = "show",
            description = "Show a file on Origin server (metadata and content as text, if available)",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  minicdn admin file show --origin http://localhost:8080 --path docs/Lebenslauf.pdf"})
    public static class AdminResourceShowCommand implements Callable<Integer> {

        @CommandLine.ParentCommand
        AdminResourceCommand parent;

        @Option(
                names = "--origin",
                required = true,
                paramLabel = "ORIGIN_URL",
                description = "Origin server base URL, e.g. http://localhost:8080")
        URI origin;

        @Option(
                names = "--path",
                required = true,
                paramLabel = "REMOTE_PATH",
                description = "File path on origin, e.g. docs/Lebenslauf.pdf")
        String path;

        @Override
        public Integer call() {
            String cleanPath = PathUtils.normalizePath(path);
            if (cleanPath.isBlank()) {
                ConsoleUtils.error(parent.ctx.err(), "Invalid path: '%s' (after normalization: '%s')", path, cleanPath);
                return 1;
            }

            HttpCallResult result = parent.service().showOriginFile(origin, cleanPath);

            var out = parent.ctx.out();
            var err = parent.ctx.err();

            if (result.is2xx()) {
                ConsoleUtils.info(
                        err,
                        "Show file successful: status=%s origin=%s path=%s",
                        result.statusCode(),
                        origin,
                        cleanPath);

                String body = result.body();
                if (body != null && !body.isBlank()) {
                    out.println(JsonUtils.formatJson(body));
                    out.flush();
                }
                return 0;
            }

            ConsoleUtils.error(
                    err,
                    "Show file failed: status=%s error=%s body=%s origin=%s path=%s",
                    result.statusCode(),
                    result.error(),
                    result.body(),
                    origin,
                    cleanPath);
            return 2;
        }
    }

    @Command(
            name = "download",
            description = "Download a file from Origin Server to a local path (binary-safe)",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  minicdn admin file download --origin http://localhost:8080 --path docs/Lebenslauf.pdf --out ./downloads/Lebenslauf.pdf"
            })
    public static class AdminResourceDownloadCommand implements Callable<Integer> {

        @CommandLine.ParentCommand
        AdminResourceCommand parent;

        @Option(
                names = "--origin",
                required = true,
                paramLabel = "ORIGIN_URL",
                description = "Origin server base URL, e.g. http://localhost:8080")
        URI origin;

        @Option(
                names = "--path",
                required = true,
                paramLabel = "REMOTE_PATH",
                description = "File path on origin, e.g. docs/Lebenslauf.pdf")
        String path;

        @Option(
                names = "--out",
                required = true,
                paramLabel = "OUT_FILE",
                description = "Local output file path, e.g. ./downloads/Lebenslauf.pdf")
        Path outFile;

        @Override
        public Integer call() {
            String cleanPath = PathUtils.normalizePath(path);
            if (cleanPath.isBlank()) {
                ConsoleUtils.error(parent.ctx.err(), "Invalid path: '%s' (after normalization: '%s')", path, cleanPath);
                return 1;
            }

            HttpCallResult result = parent.service().downloadOriginFile(origin, cleanPath, outFile);

            var err = parent.ctx.err();
            if (result.is2xx()) {
                ConsoleUtils.info(
                        err,
                        "Download successful: status=%s origin=%s path=%s out=%s",
                        result.statusCode(),
                        origin,
                        cleanPath,
                        outFile);
                return 0;
            }

            ConsoleUtils.error(
                    err,
                    "Download failed: status=%s error=%s body=%s origin=%s path=%s out=%s",
                    result.statusCode(),
                    result.error(),
                    result.body(),
                    origin,
                    cleanPath,
                    outFile);
            return 2;
        }
    }
}
