package de.htwsaar.minicdn.cli;

import de.htwsaar.minicdn.cli.bootstrap.CliBootstrap;

/**
 * Schlanker Einstiegspunkt der Mini-CDN-CLI.
 *
 * <p>Wie in den anderen Modulen bleibt im Wurzelpaket nur die eigentliche
 * Main-Klasse. Die komplette Start- und Verdrahtungslogik liegt in
 * {@link CliBootstrap}.</p>
 */
public final class MiniCdnCliMain {

    private MiniCdnCliMain() {}

    /**
     * Startet die CLI-Anwendung.
     *
     * @param args rohe Kommandozeilenargumente
     * @throws Exception wenn das Bootstrapping fehlschlägt
     */
    public static void main(String[] args) throws Exception {
        CliBootstrap.run(args);
    }
}
