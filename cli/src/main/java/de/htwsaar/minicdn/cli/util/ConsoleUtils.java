package de.htwsaar.minicdn.cli.util;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * Hilfsfunktionen zum formatierten Ausgeben von Info- und Fehlermeldungen auf der Konsole, mit konsistentem Präfix und automatischem Flush.
 */
public final class ConsoleUtils {
    private ConsoleUtils() {}

    public static void info(PrintStream out, String fmt, Object... args) {
        Objects.requireNonNull(out, "out");
        out.printf(fmt + "%n", args);
        out.flush();
    }

    public static void error(PrintStream err, String fmt, Object... args) {
        Objects.requireNonNull(err, "err");
        err.printf(fmt + "%n", args);
        err.flush();
    }

    // Overloads for PrintWriter (convenience for CliContext::out/err)
    public static void info(PrintWriter out, String fmt, Object... args) {
        Objects.requireNonNull(out, "out");
        out.printf(fmt + "%n", args);
        out.flush();
    }

    public static void error(PrintWriter err, String fmt, Object... args) {
        Objects.requireNonNull(err, "err");
        err.printf(fmt + "%n", args);
        err.flush();
    }
}
