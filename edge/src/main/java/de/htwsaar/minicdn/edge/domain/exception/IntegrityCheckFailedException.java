package de.htwsaar.minicdn.edge.domain.exception;

/**
 * Fachliche Exception bei fehlgeschlagener Integritätsprüfung.
 */
public class IntegrityCheckFailedException extends RuntimeException {

    /**
     * Erstellt die Exception mit einer fachlichen Fehlermeldung.
     *
     * @param message Fehlerbeschreibung
     */
    public IntegrityCheckFailedException(String message) {
        super(message);
    }
}
