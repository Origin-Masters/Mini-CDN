package de.htwsaar.minicdn.cli.shell;

import com.fasterxml.jackson.databind.JsonNode;
import de.htwsaar.minicdn.cli.di.CliContext;
import de.htwsaar.minicdn.cli.dto.CallResult;
import de.htwsaar.minicdn.cli.service.admin.AdminEdgeService;
import de.htwsaar.minicdn.cli.service.system.SystemShutdownService;
import de.htwsaar.minicdn.common.serialization.JacksonCodec;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;
import picocli.CommandLine;
import picocli.shell.jline3.PicocliCommands;

/**
 * Interaktive Shell (REPL) für die Mini-CDN CLI.
 *
 * <p>Diese Klasse kapselt die komplette Laufzeitlogik der interaktiven
 * Kommandozeilen-Sitzung. Sie verbindet:
 *
 * <ul>
 *   <li>JLine für Eingabe, Prompt, History und Komfortfunktionen</li>
 *   <li>Picocli für das Parsen und Ausführen der eigentlichen Befehle</li>
 *   <li>den {@link CliContext} für Terminal-, Ausgabe- und Sitzungszustand</li>
 * </ul>
 *
 * <p>Aufgaben:
 * <ul>
 *   <li>Liest Benutzereingaben zeilenweise und führt sie als Picocli-Kommandos aus.</li>
 *   <li>Bietet Autovervollständigung über {@link PicocliCommands} + JLine.</li>
 *   <li>Verwaltet Shell-spezifische Befehle (z. B. {@code clear}, {@code exit}) und History.</li>
 * </ul>
 *
 * <p>Beenden:
 * <ul>
 *   <li>{@code Ctrl+C}: aktuelle Eingabe verwerfen, Shell bleibt aktiv.</li>
 *   <li>{@code Ctrl+D}: Shell beenden.</li>
 *   <li>{@code exit}/{@code quit}: Shell beenden und ggf. verwaltete Dienste sauber stoppen.</li>
 * </ul>
 */
public final class MiniCdnInteractiveShell {

    /**
     * Das bereits konfigurierte Picocli-Root-Kommando.
     *
     * <p>Alle eingegebenen Shell-Befehle werden letztlich über dieses Objekt
     * ausgeführt.
     */
    private final CommandLine cmd;

    /**
     * Zentraler CLI-Kontext mit Terminal, Ein-/Ausgabe und Sitzungszustand.
     */
    private final CliContext ctx;

    /**
     * Erzeugt eine neue interaktive Shell.
     *
     * @param cmd vorkonfiguriertes Picocli-Root-CommandLine-Objekt
     * @param ctx CLI-Kontext (Terminal, I/O, Session-Status)
     * @throws NullPointerException wenn {@code cmd} oder {@code ctx} {@code null} ist
     */
    public MiniCdnInteractiveShell(CommandLine cmd, CliContext ctx) {
        this.cmd = Objects.requireNonNull(cmd, "cmd");
        this.ctx = Objects.requireNonNull(ctx, "ctx");
    }

    /**
     * Startet die Shell-Schleife und blockiert bis zum Exit.
     *
     * <p>Die Methode initialisiert JLine, richtet Autocomplete und History ein
     * und verarbeitet anschließend Benutzereingaben in einer Endlosschleife.
     *
     * <p>History wird in {@code .minicdn.history} im aktuellen Arbeitsverzeichnis gespeichert.
     *
     * <p>Ablauf pro Iteration:
     * <ol>
     *   <li>Prompt anzeigen</li>
     *   <li>Benutzereingabe lesen</li>
     *   <li>Sonderfälle wie {@code exit}, {@code quit}, {@code clear} behandeln</li>
     *   <li>übrige Eingaben als Picocli-Kommando ausführen</li>
     *   <li>Fehler und Exit-Codes an den Benutzer zurückmelden</li>
     * </ol>
     */
    public void run() {
        // Zugriff auf die im Kontext zentral bereitgestellten I/O-Komponenten.
        Terminal terminal = ctx.terminal();
        PrintWriter out = ctx.out();
        PrintWriter err = ctx.err();

        // Steuert, ob vor dem nächsten Prompt eine Leerzeile eingefügt werden soll.
        // Das verbessert die Lesbarkeit nach ausgeführten Kommandos.
        boolean addSpacingBeforePrompt = false;

        // Dienst zum sauberen Herunterfahren verwalteter Systemressourcen beim Shell-Exit.
        SystemShutdownService shutdownService = new SystemShutdownService();

        // Brücke zwischen Picocli und JLine für Completion-Unterstützung.
        PicocliCommands picocli = new PicocliCommands(cmd);

        // Aufbau des Readers mit Terminal, Completion und Parser.
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(picocli.compileCompleters())
                .parser(new DefaultParser())
                .build();

        // Konfiguriert die Datei, in der die Command-History persistiert wird.
        reader.setVariable(LineReader.HISTORY_FILE, Path.of(".minicdn.history"));

        out.println("Mini-CDN Shell. Type 'help', 'exit', 'clear'.");
        out.println();
        out.flush();

        while (true) {
            final String line;
            try {
                if (addSpacingBeforePrompt) {
                    out.println();
                    out.flush();
                }

                // Baut einen grünen, fetten Prompt im Terminal.
                String prompt = new AttributedStringBuilder()
                        .style(AttributedStyle.DEFAULT
                                .foreground(AttributedStyle.GREEN)
                                .bold())
                        .append("mini-cdn> ")
                        .toAnsi();

                // Liest eine Zeile und entfernt führende/trailing Leerzeichen.
                line = reader.readLine(prompt).trim();
            } catch (UserInterruptException e) {
                // Ctrl+C verwirft nur die aktuelle Eingabe und startet die Schleife neu.
                continue;
            } catch (EndOfFileException e) {
                // Ctrl+D beendet die Shell direkt.
                break;
            }

            // Leere Eingaben werden ignoriert.
            if (line.isBlank()) {
                continue;
            }

            // Shell-interner Exit-Befehl: fährt verwaltete Dienste sauber herunter.
            if (equalsAnyIgnoreCase(line, "exit", "quit")) {
                shutdownOnExit(shutdownService, out, err);
                break;
            }

            // Bildschirmbereinigung über Terminal-Capability.
            if (equalsAnyIgnoreCase(line, "clear", "cls")) {
                terminal.puts(InfoCmp.Capability.clear_screen);
                terminal.flush();
                continue;
            }

            try {
                // Parser der Shell zerlegt die Eingabe in einzelne Tokens/Wörter.
                ParsedLine parsed = reader.getParser().parse(line, 0);
                List<String> words = parsed.words();
                String[] argv = words.toArray(new String[0]);

                // Übergibt die Argumente an Picocli zur eigentlichen Befehlsausführung.
                int exitCode = cmd.execute(argv);
                if (exitCode != 0) {
                    err.println("Command failed with exit code: " + exitCode);
                    err.flush();
                }

                // Nach einer Befehlsausführung vor dem nächsten Prompt optisch trennen.
                addSpacingBeforePrompt = true;
            } catch (Exception ex) {
                // Fängt unerwartete Fehler bei Parsing oder Ausführung ab,
                // damit die Shell weiterbenutzbar bleibt.
                err.println("Error executing command: " + ex.getMessage());
                err.flush();
                addSpacingBeforePrompt = true;
            }
        }
    }

    /**
     * Prüft, ob ein Eingabestring einem der übergebenen Kandidaten entspricht,
     * ohne Groß-/Kleinschreibung zu berücksichtigen.
     *
     * @param input zu prüfender Eingabestring
     * @param candidates erlaubte Vergleichswerte
     * @return {@code true}, wenn {@code input} einem Kandidaten entspricht, sonst {@code false}
     */
    private static boolean equalsAnyIgnoreCase(String input, String... candidates) {
        for (String c : candidates) {
            if (input.equalsIgnoreCase(c)) return true;
        }
        return false;
    }

    /**
     * Führt beim Verlassen der Shell einen kontrollierten Shutdown verwalteter Dienste aus.
     *
     * <p>Wenn keine verwalteten Ressourcen in der aktuellen Session registriert sind,
     * wird kein Shutdown ausgelöst.
     *
     * @param shutdownService Dienst für das Herunterfahren der Systemkomponenten
     * @param out Standardausgabe für erfolgreiche Statusmeldungen
     * @param err Fehlerausgabe für fehlgeschlagene Statusmeldungen
     */
    private void shutdownOnExit(SystemShutdownService shutdownService, PrintWriter out, PrintWriter err) {
        StopManagedEdgesResult managedEdgesResult = stopAllManagedEdgesOnExit();
        printStopStatus(out, err, managedEdgesResult.asStopStatus());

        if (!ctx.sessionState().hasManagedResources()) {
            return;
        }

        // Führt den Shutdown aller bekannten Dienste aus und sammelt deren Einzelstatus.
        SystemShutdownService.ShutdownResult result = shutdownService.shutdown(ctx.sessionState());

        // Gibt den Status der einzelnen Komponenten in fester Reihenfolge aus.
        printStopStatus(out, err, result.edge());
        printStopStatus(out, err, result.router());
        printStopStatus(out, err, result.origin());
    }

    /**
     * Stoppt beim Exit alle aktuell beim Router als managed gefuehrten Edge-Instanzen.
     */
    private StopManagedEdgesResult stopAllManagedEdgesOnExit() {
        try {
            AdminEdgeService edgeService =
                    new AdminEdgeService(ctx.transportClient(), ctx.defaultRequestTimeout(), ctx.adminToken());

            CallResult listResult = edgeService.listManaged(ctx.routerBaseUrl());
            if (listResult.error() != null) {
                return StopManagedEdgesResult.failed(
                        "Managed-Edges konnten nicht abgefragt werden: " + listResult.error());
            }
            if (!listResult.is2xx()) {
                return StopManagedEdgesResult.failed("Managed-Edges konnten nicht abgefragt werden (HTTP "
                        + Objects.toString(listResult.statusCode(), "n/a") + ").");
            }

            JsonNode managedEdges = JacksonCodec.fromJson(Objects.toString(listResult.body(), ""), JsonNode.class);
            if (!managedEdges.isArray()) {
                return StopManagedEdgesResult.failed("Ungueltige Antwort fuer managed Edges.");
            }
            if (managedEdges.isEmpty()) {
                return StopManagedEdgesResult.skipped();
            }

            int total = managedEdges.size();
            int stopped = 0;
            StringBuilder errors = new StringBuilder();

            for (JsonNode edge : managedEdges) {
                String instanceId = edge.path("instanceId").asText("").trim();
                if (instanceId.isBlank()) {
                    appendError(errors, "instanceId fehlt in managed-Liste");
                    continue;
                }

                CallResult stopResult = edgeService.stopEdge(ctx.routerBaseUrl(), instanceId, true);
                if (stopResult.error() != null) {
                    appendError(errors, instanceId + ": " + stopResult.error());
                    continue;
                }
                if (!stopResult.is2xx()) {
                    appendError(errors, instanceId + ": HTTP " + Objects.toString(stopResult.statusCode(), "n/a"));
                    continue;
                }

                stopped++;
            }

            if (errors.length() > 0) {
                return StopManagedEdgesResult.failed(
                        "Managed-Edges gestoppt " + stopped + "/" + total + ". Fehler: " + errors);
            }

            return StopManagedEdgesResult.stopped(stopped);
        } catch (Exception ex) {
            return StopManagedEdgesResult.failed("Managed-Edge-Stop fehlgeschlagen: " + ex.getMessage());
        }
    }

    private static void appendError(StringBuilder errors, String msg) {
        if (errors.length() > 0) {
            errors.append("; ");
        }
        errors.append(msg);
    }

    /**
     * Gibt das Ergebnis des Stop-Vorgangs für einen Dienst aus.
     *
     * <p>Verhalten nach Status:
     * <ul>
     *   <li>{@code SKIPPED}: keine Ausgabe</li>
     *   <li>{@code FAILED}: Ausgabe auf {@code err}</li>
     *   <li>sonst: Ausgabe auf {@code out}</li>
     * </ul>
     *
     * @param out Standardausgabe für Erfolgsmeldungen
     * @param err Fehlerausgabe für Fehlermeldungen
     * @param status Ergebnisobjekt des Stop-Vorgangs
     */
    private void printStopStatus(PrintWriter out, PrintWriter err, SystemShutdownService.StopStatus status) {
        // Übersprungene Dienste sollen die Ausgabe nicht unnötig aufblähen.
        if ("SKIPPED".equals(status.state())) {
            return;
        }

        // Fehlerstatus gehen auf STDERR, damit sie klar vom normalen Output getrennt sind.
        if ("FAILED".equals(status.state())) {
            err.printf("[EXIT] %s: %s%n", status.serviceName().toUpperCase(), status.message());
            err.flush();
            return;
        }

        // Erfolgs- oder Informationsstatus werden regulär auf STDOUT ausgegeben.
        out.printf("[EXIT] %s: %s%n", status.serviceName().toUpperCase(), status.message());
        out.flush();
    }

    private record StopManagedEdgesResult(String state, String message) {
        private static StopManagedEdgesResult skipped() {
            return new StopManagedEdgesResult("SKIPPED", "keine managed Edges vorhanden");
        }

        private static StopManagedEdgesResult stopped(int count) {
            return new StopManagedEdgesResult("STOPPED", "alle managed Edges gestoppt (count=" + count + ")");
        }

        private static StopManagedEdgesResult failed(String message) {
            return new StopManagedEdgesResult("FAILED", message);
        }

        private SystemShutdownService.StopStatus asStopStatus() {
            return switch (state) {
                case "FAILED" -> SystemShutdownService.StopStatus.failed("edge-managed", message);
                case "STOPPED" -> SystemShutdownService.StopStatus.stopped("edge-managed", message);
                default -> SystemShutdownService.StopStatus.skipped("edge-managed");
            };
        }
    }
}
