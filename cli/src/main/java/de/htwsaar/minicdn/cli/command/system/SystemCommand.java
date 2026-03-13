package de.htwsaar.minicdn.cli.command.system;

import static de.htwsaar.minicdn.common.util.ExitCodes.REQUEST_FAILED;
import static de.htwsaar.minicdn.common.util.ExitCodes.SUCCESS;
import static de.htwsaar.minicdn.common.util.ExitCodes.VALIDATION;

import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.service.system.JavaJarServiceLauncher;
import de.htwsaar.minicdn.cli.service.system.SystemInitService;
import de.htwsaar.minicdn.cli.util.ConsoleUtils;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Top-Level-Command für lokale System-Bootstrap-Aufgaben.
 *
 * <p>Die Klasse gruppiert lokale Systemoperationen wie das Starten der
 * benötigten Dienste aus den gebauten Exec-JARs. Sie selbst enthält nur
 * CLI-Adapterlogik und delegiert fachliche Startlogik an {@link SystemInitService}.</p>
 */
@Command(
        name = "system",
        description = "Lokale System-Kommandos (Bootstrap/Init)",
        mixinStandardHelpOptions = true,
        subcommands = {SystemCommand.InitCommand.class})
public final class SystemCommand implements Runnable {

    /**
     * Technischer Statuswert für fehlgeschlagene Dienste.
     */
    private static final String STATE_FAILED = "FAILED";

    /**
     * Gemeinsamer CLI-Kontext.
     */
    private final CliContext ctx;

    @Spec
    private CommandSpec spec;

    /**
     * Erzeugt den Top-Level-Command mit gemeinsamem CLI-Kontext.
     *
     * @param ctx gemeinsamer CLI-Kontext
     */
    public SystemCommand(CliContext ctx) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    @Override
    public void run() {
        spec.commandLine().usage(ctx.out());
        ctx.out().flush();
    }

    /**
     * Liefert den fachlichen Initialisierungsservice.
     *
     * @return konfigurierte Service-Instanz für lokales Bootstrap
     */
    SystemInitService initService() {
        return new SystemInitService(
                new JavaJarServiceLauncher(ctx.adminToken(), ctx.routerBaseUrl()), ctx.transportClient());
    }

    /**
     * Validiert das Projektverzeichnis für den Bootstrap-Vorgang.
     *
     * @param projectDir Projektwurzel
     * @return normalisierter absoluter Pfad
     * @throws IllegalArgumentException falls das Verzeichnis ungültig ist
     */
    Path normalizeProjectDir(Path projectDir) {
        Path value = Objects.requireNonNull(projectDir, "projectDir")
                .toAbsolutePath()
                .normalize();
        if (!Files.exists(value)) {
            throw new IllegalArgumentException("project directory does not exist: " + value);
        }
        if (!Files.isDirectory(value)) {
            throw new IllegalArgumentException("project directory is not a directory: " + value);
        }
        return value;
    }

    /**
     * Gibt den Status eines einzelnen Dienstes auf dem passenden Ausgabekanal aus.
     *
     * @param status Dienststatus
     */
    void printStatus(SystemInitService.ServiceStatus status) {
        Objects.requireNonNull(status, "status");

        String serviceName = Objects.toString(status.name(), "unknown").toUpperCase(Locale.ROOT);
        String state = Objects.toString(status.state(), "UNKNOWN");
        String message = Objects.toString(status.message(), "");

        if (STATE_FAILED.equalsIgnoreCase(state)) {
            ctx.err().printf("[%s] %s (%s)%n", serviceName, message, state);
            return;
        }

        if (status.port() > 0) {
            ctx.out().printf("[%s] Port %d: %s (%s)%n", serviceName, status.port(), message, state);
            return;
        }

        ctx.out().printf("[%s] %s (%s)%n", serviceName, message, state);
    }

    /**
     * Gibt die zusammenfassende Abschlussmeldung aus.
     *
     * @param result Gesamtergebnis der Initialisierung
     * @return passender Exit-Code
     */
    int printSummary(SystemInitService.InitResult result) {
        Objects.requireNonNull(result, "result");

        if (result.success()) {
            ConsoleUtils.info(ctx.out(), "[INIT] OK: %s", result.message());
            return SUCCESS.code();
        }

        ConsoleUtils.error(ctx.err(), "[INIT] FEHLER: %s", result.message());
        return REQUEST_FAILED.code();
    }

    /**
     * Untercommand zum lokalen Bootstrap von Origin, Edge und Router.
     *
     * <p>Der Command startet benötigte Dienste aus den Exec-JARs im Projekt,
     * prüft deren Verfügbarkeit und speichert gestartete Prozesse im Session-State.</p>
     */
    @Command(
            name = "init",
            description = "Startet origin/edge/router lokal aus den gebauten Exec-JARs und registriert Edge am Router.",
            mixinStandardHelpOptions = true)
    static final class InitCommand implements Callable<Integer> {

        @ParentCommand
        private SystemCommand parent;

        /**
         * Projektwurzel mit den Modulen origin, edge und router.
         */
        @Option(
                names = "--project-dir",
                defaultValue = ".",
                paramLabel = "DIR",
                description = "Projektwurzel mit den Modulen origin/edge/router (default: ${DEFAULT-VALUE}).")
        private Path projectDir;

        /**
         * Steuert, ob Edge ebenfalls automatisch gestartet wird.
         */
        @Option(
                names = "--start-edge",
                defaultValue = "true",
                paramLabel = "true|false",
                description =
                        "Soll der Edge-Prozess automatisch gestartet und registriert werden? (default: ${DEFAULT-VALUE})")
        private boolean startEdge;

        @Override
        public Integer call() {
            CliContext ctx = parent.ctx;
            PrintWriter out = ctx.out();
            PrintWriter err = ctx.err();

            try {
                Path normalizedProjectDir = parent.normalizeProjectDir(projectDir);

                SystemInitService.InitResult result = parent.initService()
                        .init(
                                normalizedProjectDir,
                                ctx.routerBaseUrl(),
                                ctx.defaultRequestTimeout(),
                                ctx.adminToken(),
                                startEdge);

                ctx.sessionState().remember(result);

                parent.printStatus(result.origin());
                parent.printStatus(result.edge());
                parent.printStatus(result.router());

                int exitCode = parent.printSummary(result);
                out.flush();
                err.flush();
                return exitCode;

            } catch (IllegalArgumentException ex) {
                ConsoleUtils.error(err, "[INIT] Ungültige Eingabe: %s", ex.getMessage());
                err.flush();
                return VALIDATION.code();
            } catch (Exception ex) {
                ConsoleUtils.error(err, "[INIT] Technischer Fehler: %s", ex.getMessage());
                err.flush();
                return REQUEST_FAILED.code();
            }
        }
    }
}
