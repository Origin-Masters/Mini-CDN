package de.htwsaar.minicdn.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
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

    // GET
    @GetMapping("/files/{path:.+}")
    public ResponseEntity<byte[]> getFile(@PathVariable("path") String path) {
        String url = originBaseUrl + "/api/origin/files/" + path;
        return restTemplate.getForEntity(url, byte[].class);
    }

    // HEAD
    @RequestMapping(value = "/files/{path:.+}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headFile(@PathVariable("path") String path) {
        String url = originBaseUrl + "/api/origin/files/" + path;

        ResponseEntity<Void> originResponse =
                restTemplate.exchange(url, HttpMethod.HEAD, HttpEntity.EMPTY, Void.class);

        return ResponseEntity.status(originResponse.getStatusCode())
                .headers(originResponse.getHeaders())
                .build();
    }

    // HEALTH
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    // READY
    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        return ResponseEntity.ok("ready");
    }


}
