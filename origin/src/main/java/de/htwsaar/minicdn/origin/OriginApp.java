package de.htwsaar.minicdn.origin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
@Profile("origin")
public class OriginApp {
    public static void main(String[] args) {
        SpringApplication.run(OriginApp.class, args);
    }
}
