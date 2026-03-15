package de.htwsaar.minicdn.cli.adapter.in.cli.admin;

import static de.htwsaar.minicdn.common.util.DefaultsURL.ROUTER_URL;
import static de.htwsaar.minicdn.common.util.ExitCodes.REJECTED;
import static de.htwsaar.minicdn.common.util.ExitCodes.REQUEST_FAILED;
import static de.htwsaar.minicdn.common.util.ExitCodes.SUCCESS;
import static de.htwsaar.minicdn.common.util.ExitCodes.VALIDATION;

import de.htwsaar.minicdn.cli.adapter.in.cli.support.ConsoleUtils;
import de.htwsaar.minicdn.cli.adapter.in.cli.support.JsonUtils;
import de.htwsaar.minicdn.cli.application.admin.AdminCacheService;
import de.htwsaar.minicdn.cli.application.context.CliContext;
import de.htwsaar.minicdn.cli.domain.model.CallResult;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Picocli-Einstieg für Cache-Admin-Befehle gegen den Router.
 *
 * <p>Das Kommando stellt Unterbefehle für das Invalidieren einzelner Dateien,
 * ganzer Präfixe und kompletter Regions-Caches bereit. Die eigentliche
 * Request-Logik bleibt im {@link AdminCacheService}; diese Klasse kümmert sich
 * nur um CLI-Parsing, Fehlerabbildung und Ausgabe.</p>
 */
@Command(
        name = "cache",
        description = "Invalidate edge caches via router admin API.",
        mixinStandardHelpOptions = true,
        footerHeading = "%nBeispiele:%n",
        footer = {
            "  admin cache file --region eu-west --path videos/intro.mp4",
            "  admin cache prefix --region eu-west --value videos/",
            "  admin cache clear --region eu-west"
        },
        subcommands = {
            AdminCacheCommand.AdminCacheInvalidateFileCommand.class,
            AdminCacheCommand.AdminCacheInvalidatePrefixCommand.class,
            AdminCacheCommand.AdminCacheClearRegionCommand.class
        })
public final class AdminCacheCommand implements Runnable {

    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    /**
     * Erzeugt das Root-Kommando für Cache-Admin-Aufrufe.
     *
     * @param ctx gemeinsamer CLI-Kontext
     */
    public AdminCacheCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    /**
     * Gibt die Usage des Kommandos aus, wenn kein Subcommand angegeben wurde.
     */
    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    /**
     * Erstellt den fachlichen Service für Cache-Admin-Aufrufe.
     *
     * @return konfigurierte Service-Instanz auf Basis des aktuellen CLI-Kontexts
     */
    private AdminCacheService service() {
        return new AdminCacheService(ctx.transportClient(), ctx.defaultRequestTimeout(), ctx.adminToken());
    }

    /**
     * Wertet ein Service-Ergebnis aus und schreibt die passende CLI-Ausgabe.
     *
     * @param result normiertes Request-Ergebnis
     * @param successMessage Fallback-Erfolgsmeldung bei leerem Response-Body
     * @return passender Prozess-Exit-Code
     */
    private int printResult(CallResult result, String successMessage) {
        if (result.error() != null) {
            ConsoleUtils.error(ctx.err(), "[CACHE] request failed: %s", result.error());
            return REQUEST_FAILED.code();
        }
        if (!result.is2xx()) {
            ConsoleUtils.error(
                    ctx.err(),
                    "[CACHE] request rejected: HTTP %s%s",
                    result.statusCode(),
                    result.body() == null || result.body().isBlank() ? "" : ", body=" + result.body());
            return REJECTED.code();
        }

        if (result.body() != null && !result.body().isBlank()) {
            ctx.out().println(JsonUtils.formatJson(result.body()));
        } else {
            ctx.out().println(successMessage);
        }
        ctx.out().flush();
        return SUCCESS.code();
    }

    @Command(
            name = "file",
            description = "Invalidate a single cached file in a region.",
            mixinStandardHelpOptions = true)
    /**
     * Invalidiert genau eine gecachte Datei innerhalb einer Region.
     */
    public static final class AdminCacheInvalidateFileCommand implements Callable<Integer> {

        @ParentCommand
        private AdminCacheCommand parent;

        @Option(names = "--router", defaultValue = ROUTER_URL, description = "Router base URL.")
        private URI router;

        @Option(names = "--region", required = true, description = "Target region.")
        private String region;

        @Option(names = "--path", required = true, description = "Relative file path to invalidate.")
        private String path;

        /**
         * Führt die Cache-Invalidierung für eine einzelne Datei aus.
         *
         * @return Exit-Code gemäß Request-Ergebnis oder Validierungsfehler
         */
        @Override
        public Integer call() {
            try {
                return parent.printResult(
                        parent.service().invalidateFile(router, region, path), "[CACHE] file invalidated successfully");
            } catch (IllegalArgumentException ex) {
                ConsoleUtils.error(parent.ctx.err(), "[CACHE] %s", ex.getMessage());
                return VALIDATION.code();
            }
        }
    }

    @Command(
            name = "prefix",
            description = "Invalidate all cached files of a prefix in a region.",
            mixinStandardHelpOptions = true)
    /**
     * Invalidiert alle gecachten Dateien eines Präfix in einer Region.
     */
    public static final class AdminCacheInvalidatePrefixCommand implements Callable<Integer> {

        @ParentCommand
        private AdminCacheCommand parent;

        @Option(names = "--router", defaultValue = ROUTER_URL, description = "Router base URL.")
        private URI router;

        @Option(names = "--region", required = true, description = "Target region.")
        private String region;

        @Option(names = "--value", required = true, description = "Prefix to invalidate.")
        private String value;

        /**
         * Führt die Cache-Invalidierung für ein Dateipräfix aus.
         *
         * @return Exit-Code gemäß Request-Ergebnis oder Validierungsfehler
         */
        @Override
        public Integer call() {
            try {
                return parent.printResult(
                        parent.service().invalidatePrefix(router, region, value),
                        "[CACHE] prefix invalidated successfully");
            } catch (IllegalArgumentException ex) {
                ConsoleUtils.error(parent.ctx.err(), "[CACHE] %s", ex.getMessage());
                return VALIDATION.code();
            }
        }
    }

    @Command(name = "clear", description = "Clear the full cache of a region.", mixinStandardHelpOptions = true)
    /**
     * Leert den kompletten Cache einer Region.
     */
    public static final class AdminCacheClearRegionCommand implements Callable<Integer> {

        @ParentCommand
        private AdminCacheCommand parent;

        @Option(names = "--router", defaultValue = ROUTER_URL, description = "Router base URL.")
        private URI router;

        @Option(names = "--region", required = true, description = "Target region.")
        private String region;

        /**
         * Führt das vollständige Leeren eines Regions-Caches aus.
         *
         * @return Exit-Code gemäß Request-Ergebnis oder Validierungsfehler
         */
        @Override
        public Integer call() {
            try {
                return parent.printResult(
                        parent.service().clearRegion(router, region), "[CACHE] region cache cleared successfully");
            } catch (IllegalArgumentException ex) {
                ConsoleUtils.error(parent.ctx.err(), "[CACHE] %s", ex.getMessage());
                return VALIDATION.code();
            }
        }
    }
}
