package de.htwsaar.minicdn.edge.domain.exception;

/**
 * Fachliche Exception bei fehlgeschlagener Integritätsprüfung.
 */
public class IntegrityCheckFailedException extends RuntimeException {

    public IntegrityCheckFailedException(String message) {
        super(message);
    }
}
