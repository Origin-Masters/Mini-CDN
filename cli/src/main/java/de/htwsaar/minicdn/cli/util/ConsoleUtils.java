package de.htwsaar.minicdn.cli.util;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * Kleine Utility-Klasse fuer konsistente Konsolenausgaben.
 *
 * <p>Stellt formatierte Info- und Fehlerausgaben bereit und flusht den Stream
 * jeweils direkt nach dem Schreiben.
 */
public final class ConsoleUtils {
    private ConsoleUtils() {}

    /**
     * Schreibt eine formatierte Info-Zeile auf den gegebenen {@link PrintStream}.
     *
     * @param out Ziel-Stream fuer die Ausgabe
     * @param fmt Format-String fuer {@code printf}
     * @param args Argumente zum Format-String
     */
    public static void info(PrintStream out, String fmt, Object... args) {
        Objects.requireNonNull(out, "out");
        out.printf(fmt + "%n", args);
        out.flush();
    }

    /**
     * Schreibt eine formatierte Fehler-Zeile auf den gegebenen {@link PrintStream}.
     *
     * @param err Ziel-Stream fuer die Fehlerausgabe
     * @param fmt Format-String fuer {@code printf}
     * @param args Argumente zum Format-String
     */
    public static void error(PrintStream err, String fmt, Object... args) {
        Objects.requireNonNull(err, "err");
        err.printf(fmt + "%n", args);
        err.flush();
    }

    /**
     * Schreibt eine formatierte Info-Zeile auf den gegebenen {@link PrintWriter}.
     *
     * @param out Ziel-Writer fuer die Ausgabe
     * @param fmt Format-String fuer {@code printf}
     * @param args Argumente zum Format-String
     */
    public static void info(PrintWriter out, String fmt, Object... args) {
        Objects.requireNonNull(out, "out");
        out.printf(fmt + "%n", args);
        out.flush();
    }

    /**
     * Schreibt eine formatierte Fehler-Zeile auf den gegebenen {@link PrintWriter}.
     *
     * @param err Ziel-Writer fuer die Fehlerausgabe
     * @param fmt Format-String fuer {@code printf}
     * @param args Argumente zum Format-String
     */
    public static void error(PrintWriter err, String fmt, Object... args) {
        Objects.requireNonNull(err, "err");
        err.printf(fmt + "%n", args);
        err.flush();
    }
}
