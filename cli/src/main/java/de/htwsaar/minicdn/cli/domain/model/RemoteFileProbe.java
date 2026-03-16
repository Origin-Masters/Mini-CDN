package de.htwsaar.minicdn.cli.domain.model;

/**
 * Fachliche Metadaten einer entfernten Datei für segmentierte Downloads.
 *
 * @param totalLength Gesamtgröße der Datei in Bytes
 */
public record RemoteFileProbe(long totalLength) {

    /**
     * Erzeugt ein Probe-Ergebnis.
     *
     * @param totalLength Gesamtgröße der Datei in Bytes
     * @return Probe-Ergebnis
     */
    public static RemoteFileProbe of(long totalLength) {
        if (totalLength <= 0) {
            throw new IllegalArgumentException("totalLength must be > 0");
        }
        return new RemoteFileProbe(totalLength);
    }
}
