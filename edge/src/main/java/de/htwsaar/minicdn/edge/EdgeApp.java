package de.htwsaar.minicdn.edge;

import de.htwsaar.minicdn.common.auth.SecurityConfig;
import de.htwsaar.minicdn.common.logging.LoggingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

/**
 * Einstiegspunkt der Edge-Anwendung.
 *
 * <p>Startet den Spring-Kontext für das Profil {@code edge} und bindet die
 * gemeinsame Logging- und Security-Konfiguration ein.</p>
 */
@SpringBootApplication
@Import({LoggingConfig.class, SecurityConfig.class})
@Profile("edge")
public class EdgeApp {

    /**
     * Startet die Edge-Anwendung.
     *
     * @param args Kommandozeilenargumente
     */
    public static void main(String[] args) {
        SpringApplication.run(EdgeApp.class, args);
    }
}
