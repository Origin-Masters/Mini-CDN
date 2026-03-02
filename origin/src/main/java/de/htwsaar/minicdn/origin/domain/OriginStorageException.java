package de.htwsaar.minicdn.origin.domain;

/**
 * Technische Exception für Fehler im Storage-Adapter.
 *
 * <p>Wird von konkreten Storage-Adaptern geworfen, wenn das zugrunde liegende
 * Speichersystem nicht korrekt gelesen oder geschrieben werden kann.</p>
 */
public class OriginStorageException extends RuntimeException {

    public OriginStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
