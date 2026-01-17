package de.htwsaar.minicdn.server;

import java.net.URI;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cdn")
@Profile("cdn")
public class CDNController {

    private final CdnProperties cdnProperties;

    public CDNController(CdnProperties cdnProperties) {
        this.cdnProperties = cdnProperties;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        return ResponseEntity.ok("ready");
    }

    /**
     * Minimal routing: choose edge by region and redirect client to edge URL.
     *
     * Example:
     *  GET /api/cdn/files/test.txt?region=eu-west
     *  -> 307 Location: http://localhost:8081/api/edge/files/test.txt
     */
    @GetMapping("/files/{path:.+}")
    public ResponseEntity<Void> routeToEdge(
            @PathVariable("path") String path,
            @RequestParam(value = "region", required = false) String regionQuery,
            @RequestHeader(value = "X-Client-Region", required = false) String regionHeader) {

        String region = (regionQuery != null && !regionQuery.isBlank()) ? regionQuery : regionHeader;
        if (region == null || region.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        Map<String, String> edges = cdnProperties.getEdges();
        String edgeBaseUrl = edges.get(region);
        if (edgeBaseUrl == null || edgeBaseUrl.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String location = edgeBaseUrl + "/api/edge/files/" + path;

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(location));

        return new ResponseEntity<>(headers, HttpStatus.TEMPORARY_REDIRECT);
    }
}
