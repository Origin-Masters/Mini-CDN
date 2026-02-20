package de.htwsaar.minicdn.origin;

import de.htwsaar.minicdn.common.auth.SecurityConfig;
import de.htwsaar.minicdn.common.logging.LoggingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
@Import({LoggingConfig.class, SecurityConfig.class})
@Profile("origin")
public class OriginApp {
    public static void main(String[] args) {
        SpringApplication.run(OriginApp.class, args);
    }
}
