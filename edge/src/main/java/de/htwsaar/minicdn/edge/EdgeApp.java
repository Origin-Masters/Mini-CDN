package de.htwsaar.minicdn.edge;

import de.htwsaar.minicdn.common.auth.SecurityConfig;
import de.htwsaar.minicdn.common.logging.LoggingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
@Import({LoggingConfig.class, SecurityConfig.class})
@Profile("edge")
public class EdgeApp {
    public static void main(String[] args) {
        SpringApplication.run(EdgeApp.class, args);
    }
}
