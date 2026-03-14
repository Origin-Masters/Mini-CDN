package de.htwsaar.minicdn.cli.command.user;

import static de.htwsaar.minicdn.common.util.ExitCodes.REJECTED;
import static de.htwsaar.minicdn.common.util.ExitCodes.REQUEST_FAILED;
import static de.htwsaar.minicdn.common.util.ExitCodes.SUCCESS;
import static de.htwsaar.minicdn.common.util.ExitCodes.VALIDATION;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.service.user.UserStatsService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import de.htwsaar.minicdn.cli.util.JsonUtils;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * User-Commands für Statistiken des aktuell eingeloggten Nutzers.
 *
 * <p>Die Klasse kapselt ausschließlich CLI-spezifische Aufgaben wie
 * Usage-Anzeige, Eingabevalidierung, Delegation an den Service,
 * Exit-Code-Mapping und Konsolenausgabe. Die fachliche HTTP-Logik
 * verbleibt vollständig im {@link UserStatsService}.</p>
 */
@Command(
        name = "stats",
        description = "Statistics for the current user",
        mixinStandardHelpOptions = true,
        subcommands = {
            UserStatsCommand.FileCommand.class,
            UserStatsCommand.ListCommand.class,
            UserStatsCommand.OverallCommand.class
        })
public final class UserStatsCommand implements Runnable {

    private final CliContext ctx;
    private final UserStatsService statsService;

    @Spec
    private CommandSpec spec;

    /**
     * Erzeugt den Command mit gemeinsamem CLI-Kontext.
     *
     * @param ctx gemeinsamer CLI-Kontext
     */
    public UserStatsCommand(CliContext ctx) {
        this(
                ctx,
                new UserStatsService(
                        Objects.requireNonNull(ctx, "ctx").transportClient(),
                        ctx.defaultRequestTimeout(),
                        ctx.routerBaseUrl(),
                        () -> {
                            Long loggedInUserId = ctx.sessionState().loggedInUserId();
                            return loggedInUserId == null ? -1L : loggedInUserId;
                        }));
    }

    /**
     * Interner Konstruktor für Tests und explizite Dependency Injection.
     *
     * @param ctx gemeinsamer CLI-Kontext
     * @param statsService fachlicher Service für User-Statistiken
     */
    UserStatsCommand(CliContext ctx, UserStatsService statsService) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.statsService = Objects.requireNonNull(statsService, "statsService");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    /**
     * Liefert den fachlichen Service für User-Statistiken.
     *
     * @return Service für Statistikabfragen
     */
    UserStatsService statsService() {
        return statsService;
    }

    /**
     * Validiert eine Datei-ID.
     *
     * @param fileId Datei-ID aus der CLI
     * @return validierte Datei-ID
     * @throws IllegalArgumentException falls die Datei-ID ungültig ist
     */
    long validateFileId(long fileId) {
        if (fileId <= 0) {
            throw new IllegalArgumentException("--file-id muss größer als 0 sein");
        }
        return fileId;
    }

    /**
     * Validiert ein Limit für Listenabfragen.
     *
     * @param limit Limit aus der CLI
     * @return validiertes Limit
     * @throws IllegalArgumentException falls das Limit ungültig ist
     */
    int validateLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("--limit muss größer als 0 sein");
        }
        return limit;
    }

    /**
     * Validiert das Statistikfenster in Sekunden.
     *
     * @param windowSec Zeitfenster in Sekunden
     * @return validiertes Zeitfenster
     * @throws IllegalArgumentException falls das Zeitfenster ungültig ist
     */
    int validateWindowSec(int windowSec) {
        if (windowSec <= 0) {
            throw new IllegalArgumentException("--window-sec muss größer als 0 sein");
        }
        return windowSec;
    }

    /**
     * Gibt einen Validierungsfehler einheitlich aus.
     *
     * @param message Fehlermeldung
     * @return Exit-Code für Validierungsfehler
     */
    int validationError(String message) {
        ConsoleUtils.error(ctx.err(), "[USER] %s", Objects.toString(message, "Ungültige Eingabe"));
        return VALIDATION.code();
    }

    /**
     * Prüft, ob ein User eingeloggt ist.
     *
     * @throws IllegalArgumentException falls kein Login vorhanden ist
     */
    void requireLoggedInUser() {
        if (!ctx.sessionState().isLoggedIn()) {
            throw new IllegalArgumentException("Bitte zuerst einloggen: system login --name <user>");
        }
    }

    /**
     * Gibt einen technischen Fehler einheitlich aus.
     *
     * @param operation Name der Operation
     * @param message Fehlermeldung
     * @return Exit-Code für technische Fehler
     */
    int requestFailed(String operation, String message) {
        ConsoleUtils.error(
                ctx.err(),
                "[USER] %s fehlgeschlagen: %s",
                Objects.toString(operation, "Request"),
                Objects.toString(message, "unbekannter Fehler"));
        return REQUEST_FAILED.code();
    }

    /**
     * Gibt eine fachliche Ablehnung einheitlich aus.
     *
     * @param operation Name der Operation
     * @param statusCode HTTP-Statuscode
     * @param body optionaler Response-Body
     * @return Exit-Code für fachliche Ablehnung
     */
    int rejected(String operation, Integer statusCode, String body) {
        ConsoleUtils.error(
                ctx.err(), "[USER] %s abgelehnt: HTTP %s", Objects.toString(operation, "Request"), statusCode);

        if (body != null && !body.isBlank()) {
            ctx.err().println(body);
            ctx.err().flush();
        }

        return REJECTED.code();
    }

    /**
     * Gibt einen erfolgreichen Response-Body formatiert aus.
     *
     * @param title kurze Überschrift für die Ausgabe
     * @param body Response-Body
     * @return Exit-Code für Erfolg
     */
    int printSuccessBody(String title, String body) {
        ConsoleUtils.info(ctx.out(), "[USER] %s", title);

        if (body != null && !body.isBlank()) {
            ctx.out().println(JsonUtils.formatJson(body));
            ctx.out().flush();
        }

        return SUCCESS.code();
    }

    /**
     * Bewertet ein HTTP-Aufrufergebnis zentral und gibt den passenden Exit-Code zurück.
     *
     * @param operation Name der Operation
     * @param successTitle Überschrift für den Erfolgsfall
     * @param result Ergebnis des Service-Aufrufs
     * @return passender Exit-Code
     */
    int handleResult(String operation, String successTitle, CallResult result) {
        Objects.requireNonNull(result, "result");

        if (result.error() != null) {
            return requestFailed(operation, result.error());
        }

        Integer statusCode = result.statusCode();
        if (statusCode == null) {
            return requestFailed(operation, "fehlender HTTP-Status");
        }

        if (result.is2xx()) {
            return printSuccessBody(successTitle, result.body());
        }

        return rejected(operation, statusCode, result.body());
    }

    /**
     * Zeigt Statistiken zu einer einzelnen Datei des aktuellen Nutzers.
     */
    @Command(
            name = "file",
            description = "Show stats for one of my files",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  user stats file --file-id 123", "  user stats file --file-id 456"})
    public static final class FileCommand implements Callable<Integer> {

        @ParentCommand
        private UserStatsCommand parent;

        /**
         * Technische Datei-ID.
         */
        @Option(
                names = {"--file-id"},
                required = true,
                paramLabel = "FILE_ID",
                description = "File ID")
        private long fileId;

        @Override
        public Integer call() {
            try {
                parent.requireLoggedInUser();
                long validatedFileId = parent.validateFileId(fileId);
                CallResult result = parent.statsService().fileStatsForCurrentUser(validatedFileId);
                return parent.handleResult("Datei-Statistik", "Datei-Statistik erfolgreich geladen", result);
            } catch (IllegalArgumentException ex) {
                return parent.validationError(ex.getMessage());
            } catch (Exception ex) {
                return parent.requestFailed("Datei-Statistik", ex.getMessage());
            }
        }
    }

    /**
     * Listet Dateistatistiken des aktuellen Nutzers.
     */
    @Command(
            name = "list",
            description = "List my top file by activity",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  user stats list", "  user stats list --limit 20"})
    public static final class ListCommand implements Callable<Integer> {

        @ParentCommand
        private UserStatsCommand parent;

        /**
         * Maximale Anzahl zurückgelieferter Dateien.
         */
        @Option(
                names = {"--limit"},
                defaultValue = "10",
                paramLabel = "LIMIT",
                description = "Max number of files (default: ${DEFAULT-VALUE})")
        private int limit;

        @Override
        public Integer call() {
            try {
                parent.requireLoggedInUser();
                int validatedLimit = parent.validateLimit(limit);
                CallResult result = parent.statsService().listUserFilesStats(validatedLimit);
                return parent.handleResult("Datei-Statistikliste", "Datei-Statistikliste erfolgreich geladen", result);
            } catch (IllegalArgumentException ex) {
                return parent.validationError(ex.getMessage());
            } catch (Exception ex) {
                return parent.requestFailed("Datei-Statistikliste", ex.getMessage());
            }
        }
    }

    /**
     * Zeigt Gesamtstatistiken des aktuellen Nutzers in einem Zeitfenster.
     */
    @Command(
            name = "overall",
            description = "Overall statistics for current user",
            mixinStandardHelpOptions = true,
            footerHeading = "%nBeispiele:%n",
            footer = {"  user stats overall", "  user stats overall --window-sec 7200"})
    public static final class OverallCommand implements Callable<Integer> {

        @ParentCommand
        private UserStatsCommand parent;

        /**
         * Zeitfenster in Sekunden.
         */
        @Option(
                names = {"--window-sec"},
                defaultValue = "3600",
                paramLabel = "SECONDS",
                description = "Time window in seconds (default: ${DEFAULT-VALUE})")
        private int windowSec;

        @Override
        public Integer call() {
            try {
                parent.requireLoggedInUser();
                int validatedWindowSec = parent.validateWindowSec(windowSec);
                CallResult result = parent.statsService().overallStatsForCurrentUser(validatedWindowSec);
                return parent.handleResult("Gesamtstatistik", "Gesamtstatistik erfolgreich geladen", result);
            } catch (IllegalArgumentException ex) {
                return parent.validationError(ex.getMessage());
            } catch (Exception ex) {
                return parent.requestFailed("Gesamtstatistik", ex.getMessage());
            }
        }
    }
}
