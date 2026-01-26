package de.htwsaar.minicdn.router;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
@Profile("cdn")
public class RouterApp {
    public static void main(String[] args) {
        SpringApplication.run(RouterApp.class, args);
    }
}
