package de.htwsaar.minicdn.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

/**
 * Edge-Controller des Mini-CDN.
 * Proxy zum Origin-Server.
 */
@RestController
@RequestMapping("/api/edge")
@Profile("edge")
public class EdgeController {

    // TODO : Use Streams instead of Byte Arrays

    @Value("${origin.base-url}")
    private String originBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/files/{path:.+}")
    public ResponseEntity<byte[]> getFile(@PathVariable("path") String path) {
        String url = originBaseUrl + "/api/origin/files/" + path;
        return restTemplate.getForEntity(url, byte[].class);
    }
}
