package de.htwsaar.minicdn.router;

import de.htwsaar.minicdn.common.logging.LoggingConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(LoggingConfig.class)
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
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }
}
