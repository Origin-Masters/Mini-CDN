package de.htwsaar.minicdn.cli.domain.model;

import java.util.Objects;

/**
 * Fachliches Ergebnis für das Starten einer verwalteten Edge über den Router.
 *
 * @param status fachlicher Status des Startversuchs
 * @param message optionale Detailmeldung
 */
public record ManagedEdgeStartResult(Status status, String message) {

    /**
     * Fachliche Statuswerte des Startversuchs.
     */
    public enum Status {
        STARTED,
        CONFLICT,
        FAILED
    }

    /**
     * Erzeugt ein Erfolgsresultat.
     *
     * @return Erfolgsresultat
     */
    public static ManagedEdgeStartResult started() {
        return new ManagedEdgeStartResult(Status.STARTED, null);
    }

    /**
     * Erzeugt ein Konfliktresultat.
     *
     * @param message Detailmeldung
     * @return Konfliktresultat
     */
    public static ManagedEdgeStartResult conflict(String message) {
        return new ManagedEdgeStartResult(Status.CONFLICT, Objects.toString(message, ""));
    }

    /**
     * Erzeugt ein Fehlerresultat.
     *
     * @param message Detailmeldung
     * @return Fehlerresultat
     */
    public static ManagedEdgeStartResult failed(String message) {
        return new ManagedEdgeStartResult(Status.FAILED, Objects.toString(message, ""));
    }
}
