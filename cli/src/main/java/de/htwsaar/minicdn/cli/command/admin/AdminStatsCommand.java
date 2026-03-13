package de.htwsaar.minicdn.cli.command.admin;

import static de.htwsaar.minicdn.common.util.ExitCodes.REJECTED;
import static de.htwsaar.minicdn.common.util.ExitCodes.REQUEST_FAILED;
import static de.htwsaar.minicdn.common.util.ExitCodes.SUCCESS;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.service.admin.AdminStatsService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import java.io.PrintWriter;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Stellt Admin-Befehle zum Abruf von Router-/Edge-Statistiken bereit.
 *
 * <p>Die Klasse bildet die CLI-Adapter-Schicht für Statistikoperationen.
 * Sie validiert Benutzereingaben, nutzt Default-Werte aus dem {@link CliContext}
 * und delegiert den eigentlichen Abruf sowie die Formatierung an den
 * {@link AdminStatsService}.</p>
 */
@Command(
        name = "stats",
        description = "Show Mini-CDN runtime statistics",
        mixinStandardHelpOptions = true,
        footerHeading = "%nBeispiele:%n",
        footer = {
            "  admin stats show",
            "  admin stats show -H http://localhost:8082",
            "  admin stats show --window-sec 120 --aggregate-edge=false",
            "  admin stats show --json",
            "  admin stats show --token my-admin-token"
        },
        subcommands = {AdminStatsCommand.AdminStatsShowCommand.class})
public final class AdminStatsCommand implements Runnable {

    /**
     * Gemeinsamer CLI-Kontext.
     */
    private final CliContext ctx;

    /**
     * Fachlicher Service für Statistikabrufe.
     */
    private final AdminStatsService adminStatsService;

    @Spec
    private CommandSpec spec;

    /**
     * Erzeugt den Command mit gemeinsamem CLI-Kontext.
     *
     * @param ctx gemeinsamer CLI-Kontext
     */
    public AdminStatsCommand(CliContext ctx) {
        this(
                ctx,
                new AdminStatsService(
                        Objects.requireNonNull(ctx, "ctx").transportClient(), ctx.defaultRequestTimeout()));
    }

    /**
     * Interner Konstruktor für Tests und explizite Dependency Injection.
     *
     * @param ctx gemeinsamer CLI-Kontext
     * @param adminStatsService fachlicher Statistik-Service
     */
    AdminStatsCommand(CliContext ctx, AdminStatsService adminStatsService) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.adminStatsService = Objects.requireNonNull(adminStatsService, "adminStatsService");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    /**
     * Liefert den gemeinsamen CLI-Kontext.
     *
     * @return CLI-Kontext
     */
    CliContext ctx() {
        return ctx;
    }

    /**
     * Liefert den Statistik-Service.
     *
     * @return Statistik-Service
     */
    AdminStatsService service() {
        return adminStatsService;
    }

    /**
     * Unterbefehl zum Abruf und zur Ausgabe strukturierter Router-Statistiken.
     *
     * <p>Der Command orchestriert nur den Ablauf: Eingaben prüfen, Service aufrufen
     * und das vom Service formatierte Ergebnis ausgeben.</p>
     */
    @Command(
            name = "show",
            description = "Fetch and display structured stats from the router",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {
                "  admin stats show",
                "  admin stats show -H http://localhost:8082",
                "  admin stats show --window-sec 10",
                "  admin stats show --aggregate-edge=false",
                "  admin stats show --json"
            })
    public static final class AdminStatsShowCommand implements Callable<Integer> {

        @ParentCommand
        private AdminStatsCommand parent;

        /**
         * Basis-URL des Routers.
         *
         * <p>Wenn kein Wert gesetzt wird, wird die Router-URL aus dem CLI-Kontext verwendet.</p>
         */
        @Option(
                names = {"-H", "--host"},
                paramLabel = "ROUTER_URL",
                description = "Basis-URL des Routers, z. B. http://localhost:8082")
        private URI host;

        /**
         * Zeitfenster in Sekunden für Requests/Minute.
         */
        @Option(
                names = "--window-sec",
                defaultValue = "60",
                paramLabel = "SECONDS",
                description = "Zeitfenster in Sekunden für Requests/Minute (min. 1)")
        private int windowSec;

        /**
         * Steuert, ob Edge-Metriken aggregiert werden sollen.
         */
        @Option(
                names = "--aggregate-edge",
                defaultValue = "true",
                paramLabel = "true|false",
                description = "Edge-Metriken aggregieren (true/false)")
        private boolean aggregateEdge;

        /**
         * Gibt die komplette JSON-Antwort pretty-printed aus.
         */
        @Option(
                names = "--json",
                defaultValue = "false",
                description = "Vollständige JSON-Antwort pretty-printed ausgeben")
        private boolean printJson;

        /**
         * Optionales Admin-Token.
         *
         * <p>Wenn kein Wert gesetzt wird, wird das Token aus dem CLI-Kontext verwendet.</p>
         */
        @Option(
                names = "--token",
                paramLabel = "TOKEN",
                description = "Optionales Admin-Token, Standard ist das Token aus dem CLI-Kontext")
        private String tokenOverride;

        @Override
        public Integer call() {
            PrintWriter out = parent.ctx().out();
            PrintWriter err = parent.ctx().err();

            if (windowSec < 1) {
                ConsoleUtils.error(err, "[ADMIN] --window-sec muss >= 1 sein.");
                return REJECTED.code();
            }

            URI effectiveHost = host != null ? host : parent.ctx().routerBaseUrl();
            String effectiveToken =
                    hasText(tokenOverride) ? tokenOverride.trim() : parent.ctx().adminToken();

            try {
                AdminStatsService.StatsResponse response =
                        parent.service().fetchStats(effectiveHost, windowSec, aggregateEdge, effectiveToken);

                if (!response.isSuccess()) {
                    return handleFailure(err, response);
                }

                String output = printJson
                        ? parent.service().formatPrettyJson(response)
                        : parent.service().formatHumanReadable(response, windowSec);

                out.println(output);
                out.flush();
                return SUCCESS.code();

            } catch (Exception ex) {
                ConsoleUtils.error(err, "[ADMIN] Stats request failed: %s", ex.getMessage());
                return REQUEST_FAILED.code();
            }
        }

        /**
         * Behandelt nicht erfolgreiche Antworten des Statistik-Services einheitlich.
         *
         * @param err Fehlerausgabe
         * @param response Service-Antwort
         * @return passender Exit-Code
         */
        private int handleFailure(PrintWriter err, AdminStatsService.StatsResponse response) {
            if (response.hasError()) {
                ConsoleUtils.error(err, "[ADMIN] Stats request failed: %s", response.error());
                return REQUEST_FAILED.code();
            }

            Integer statusCode = response.statusCode();
            ConsoleUtils.error(err, "[ADMIN] Stats request failed: HTTP %s", statusCode);

            if (hasText(response.rawBody())) {
                ConsoleUtils.error(err, response.rawBody());
            }

            if (response.isAuthError()) {
                ConsoleUtils.error(
                        err,
                        "[ADMIN] Hint: pass --token TOKEN or configure MINICDN_ADMIN_TOKEN / -Dminicdn.admin.token.");
            }

            return REJECTED.code();
        }

        /**
         * Prüft, ob ein Text gesetzt ist.
         *
         * @param value zu prüfender Text
         * @return {@code true}, wenn der Text nicht leer ist
         */
        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
