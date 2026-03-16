package de.htwsaar.minicdn.cli.domain.model;

import java.net.URI;
import java.util.Objects;

/**
 * Fachliches Ergebnis einer Dateirouten-Auflösung.
 *
 * <p>Das Objekt kapselt das aufgelöste Ziel einer Datei bewusst als fachliches
 * Routing-Ergebnis. Fachliche Services sollen mit diesem Wert als Handle
 * arbeiten und keine transportabhängigen Details direkt verarbeiten.</p>
 *
 * @param targetUri aufgelöstes Ziel für den Dateizugriff
 */
public record ResolvedFileRoute(URI targetUri) {

    /**
     * Erzeugt ein aufgelöstes Routen-Ziel.
     *
     * @param targetUri Ziel-URI
     * @return Ergebnisobjekt
     */
    public static ResolvedFileRoute of(URI targetUri) {
        return new ResolvedFileRoute(Objects.requireNonNull(targetUri, "targetUri"));
    }
}
