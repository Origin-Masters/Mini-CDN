package de.htwsaar.minicdn.router;

import de.htwsaar.minicdn.common.auth.SecurityConfig;
import de.htwsaar.minicdn.common.logging.LoggingConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync; // <---: Ermöglicht Hintergrund-Arbeit

@SpringBootApplication
@EnableAsync // <--- Aktiviert den Task-Pool für das NFA-Requirement (Parallelität)
@Import({LoggingConfig.class, SecurityConfig.class})
public class RouterApp {
    public static void main(String[] args) {
        SpringApplication.run(RouterApp.class, args);
    }

    /**
     * Zentraler HTTP-Client als Spring-Bean.
     *
     * <p>So wird nicht in Controllern {@code new HttpClient...} gebaut und Timeouts sind zentral steuerbar.
     */
    @Bean
    HttpClient httpClient() {
        // Tipp für das NFA: Da wir viele parallele Anfragen erwarten,
        // ist dieser Standard-Client gut, da er Verbindungen effizient verwaltet.
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }
}
