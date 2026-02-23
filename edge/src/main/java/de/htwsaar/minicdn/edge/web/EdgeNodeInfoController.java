package de.htwsaar.minicdn.edge.web;

import de.htwsaar.minicdn.edge.config.EdgeConfigService;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liefert Identität und Region der Edge-Node.
 * Nützlich für den Router und Ops-Monitoring.
 */
@RestController
@RequestMapping("/api/edge/info")
@Profile("edge")
public class EdgeNodeInfoController {

    private final EdgeConfigService configService;

    /**
     * Constructor Injection.
     *
     * @param configService Live-Konfiguration (enthält Region)
     */
    public EdgeNodeInfoController(EdgeConfigService configService) {
        this.configService = configService;
    }

    /**
     * Gibt Region und aktuelle Konfigurationsparameter zurück.
     *
     * @return Node-Info als JSON
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> info() {
        var cfg = configService.current();
        return ResponseEntity.ok(Map.of(
                "region", cfg.region(),
                "replacementStrategy", cfg.replacementStrategy().name(),
                "maxEntries", cfg.maxEntries(),
                "defaultTtlMs", cfg.defaultTtlMs()));
    }
}
