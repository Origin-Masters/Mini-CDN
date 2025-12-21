package de.htwsaar.minicdn.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class CDNController {

    @GetMapping("/health")
    public String health() {
        return "Hello World!";
    }
}
