package de.htwsaar.minicdn.edge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
@Profile("edge")
public class EdgeApp {
    public static void main(String[] args) {
        SpringApplication.run(EdgeApp.class, args);
    }
}
