package de.htwsaar.minicdn.router;

import de.htwsaar.minicdn.common.auth.SecurityConfig;
import de.htwsaar.minicdn.common.logging.LoggingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@Import({LoggingConfig.class, SecurityConfig.class})
public class RouterApp {
    public static void main(String[] args) {
        SpringApplication.run(RouterApp.class, args);
    }
}
