package de.htwsaar.minicdn.edge.domain;

import java.util.Objects;

/**
 * Fachliche Exception für Zugriffsprobleme auf den Origin.
 *
 * <p>Enthält bewusst keine HTTP-Statuscodes.
 * Die Übersetzung in HTTP erfolgt ausschließlich im Web-Adapter.</p>
 */
public class OriginAccessException extends RuntimeException {

    public enum Reason {
        NOT_FOUND,
        UNAVAILABLE,
        INVALID_RESPONSE
    }

    private final Reason reason;

    public OriginAccessException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public OriginAccessException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public Reason getReason() {
        return reason;
    }
}
