package de.htwsaar.minicdn.common.util;

import java.util.Objects;

/**
 * Einheitliche Exit-Codes für die gesamte CLI.
 */
public enum ExitCodes {

    /**
     * Erfolgreiche Ausführung.
     */
    SUCCESS(0),

    /**
     * Technischer Fehler, z. B. IO-, Transport-, Parsing- oder Laufzeitfehler.
     */
    REQUEST_FAILED(1),

    /**
     * Fachlich abgelehnte Anfrage.
     */
    REJECTED(2),

    /**
     * Ungültige CLI-Eingabe.
     */
    VALIDATION(3),

    /**
     * Serverfehler, typischerweise 5xx.
     */
    SERVER_ERROR(4);

    private final int code;

    ExitCodes(int code) {
        this.code = code;
    }

    /**
     * Liefert den numerischen Prozess-Exit-Code.
     *
     * @return numerischer Exit-Code
     */
    public int code() {
        return code;
    }

    /**
     * Löst einen Exit-Code in seinen numerischen Wert auf.
     *
     * @param exitCode fachlicher Exit-Code
     * @return numerischer Prozess-Exit-Code
     */
    public static int resolve(ExitCodes exitCode) {
        return Objects.requireNonNull(exitCode, "exitCode must not be null").code;
    }
}
