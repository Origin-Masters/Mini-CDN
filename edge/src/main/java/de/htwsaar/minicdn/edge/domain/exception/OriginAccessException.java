package de.htwsaar.minicdn.edge.domain.exception;

import java.util.Objects;

/**
 * Fachliche Exception für Zugriffsprobleme auf den Origin.
 *
 * <p>Enthält bewusst keine HTTP-Statuscodes.
 * Die Übersetzung in HTTP erfolgt ausschließlich im Web-Adapter.</p>
 */
public class OriginAccessException extends RuntimeException {

    /** Fachliche Fehlerursache des Origin-Zugriffs. */
    public enum Reason {
        NOT_FOUND,
        UNAVAILABLE,
        INVALID_RESPONSE
    }

    private final Reason reason;

    /**
     * Erstellt eine Exception ohne zugrunde liegende Ursache.
     *
     * @param reason fachliche Fehlerursache
     * @param message Fehlermeldung
     */
    public OriginAccessException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    /**
     * Erstellt eine Exception mit zugrunde liegender Ursache.
     *
     * @param reason fachliche Fehlerursache
     * @param message Fehlermeldung
     * @param cause ursprüngliche Ursache
     */
    public OriginAccessException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    /**
     * Gibt die fachliche Fehlerursache zurück.
     *
     * @return Fehlerursache
     */
    public Reason getReason() {
        return reason;
    }
}
