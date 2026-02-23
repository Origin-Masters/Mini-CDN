package de.htwsaar.minicdn.edge.web;

import de.htwsaar.minicdn.edge.cache.ReplacementStrategy;
import de.htwsaar.minicdn.edge.config.EdgeConfigService;
import de.htwsaar.minicdn.edge.config.EdgeRuntimeConfig;
import de.htwsaar.minicdn.edge.config.TtlPolicyService;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-API für Live-Konfiguration der Edge-Node.
 *
 * <ul>
 *   <li>GET  /api/edge/admin/config          – aktuelle Konfiguration</li>
 *   <li>PUT  /api/edge/admin/config          – vollständiges Update</li>
 *   <li>PATCH /api/edge/admin/config         – partielles Update</li>
 *   <li>GET  /api/edge/admin/config/ttl      – TTL-Policies</li>
 *   <li>PUT  /api/edge/admin/config/ttl      – TTL-Policy setzen</li>
 *   <li>DELETE /api/edge/admin/config/ttl    – TTL-Policy entfernen</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/edge/admin/config")
@Profile("edge")
public class EdgeAdminConfigController {

    private final EdgeConfigService configService;
    private final TtlPolicyService ttlPolicyService;

    /**
     * Constructor Injection.
     *
     * @param configService    Live-Konfiguration
     * @param ttlPolicyService TTL-Policies
     */
    public EdgeAdminConfigController(EdgeConfigService configService, TtlPolicyService ttlPolicyService) {
        this.configService = configService;
        this.ttlPolicyService = ttlPolicyService;
    }

    /** @return aktuelle {@link EdgeRuntimeConfig} */
    @GetMapping
    public ResponseEntity<EdgeRuntimeConfig> getConfig() {
        return ResponseEntity.ok(configService.current());
    }

    /**
     * Vollständiges Config-Update (ersetzt alle Felder).
     *
     * @param dto neues Konfigurations-DTO
     * @return aktualisierte Konfiguration
     */
    @PutMapping
    public ResponseEntity<EdgeRuntimeConfig> updateConfig(@RequestBody ConfigDto dto) {
        EdgeRuntimeConfig next = new EdgeRuntimeConfig(
                dto.region(),
                Math.max(0, dto.defaultTtlMs()),
                Math.max(0, dto.maxEntries()),
                dto.replacementStrategy());
        configService.update(next);
        return ResponseEntity.ok(next);
    }

    /**
     * Partielles Config-Update (nur gesetzte Felder werden übernommen).
     *
     * @param dto partielles Konfigurations-DTO (Felder können {@code null} sein)
     * @return aktualisierte Konfiguration
     */
    @PatchMapping
    public ResponseEntity<EdgeRuntimeConfig> patchConfig(@RequestBody ConfigPatchDto dto) {
        EdgeRuntimeConfig updated =
                configService.patch(dto.region(), dto.defaultTtlMs(), dto.maxEntries(), dto.replacementStrategy());
        return ResponseEntity.ok(updated);
    }

    /** @return alle TTL-Policies als Prefix → ms Map */
    @GetMapping("/ttl")
    public ResponseEntity<Map<String, Long>> getTtlPolicies() {
        return ResponseEntity.ok(ttlPolicyService.snapshot());
    }

    /**
     * Setzt eine TTL-Policy für einen Pfad-Prefix.
     *
     * @param dto Prefix + TTL in ms
     * @return Bestätigung
     */
    @PutMapping("/ttl")
    public ResponseEntity<Map<String, Object>> setTtlPolicy(@RequestBody TtlPolicyDto dto) {
        ttlPolicyService.setPrefixTtlMs(dto.prefix(), dto.ttlMs());
        return ResponseEntity.ok(Map.of("prefix", dto.prefix(), "ttlMs", dto.ttlMs()));
    }

    /**
     * Entfernt eine TTL-Policy.
     *
     * @param prefix Pfad-Prefix (Query-Parameter)
     * @return Bestätigung
     */
    @DeleteMapping("/ttl")
    public ResponseEntity<Map<String, Object>> removeTtlPolicy(@RequestParam("prefix") String prefix) {
        boolean removed = ttlPolicyService.removePrefix(prefix);
        return ResponseEntity.ok(Map.of("prefix", prefix, "removed", removed));
    }

    /**
     * DTO für vollständiges Config-Update.
     *
     * @param region              Region
     * @param defaultTtlMs        Standard-TTL in ms
     * @param maxEntries          maximale Cache-Einträge
     * @param replacementStrategy LRU oder LFU
     */
    public record ConfigDto(
            String region, long defaultTtlMs, int maxEntries, ReplacementStrategy replacementStrategy) {}

    /**
     * DTO für partielles Config-Update (alle Felder optional / nullable).
     *
     * @param region              neue Region oder {@code null}
     * @param defaultTtlMs        neue Standard-TTL oder {@code null}
     * @param maxEntries          neues Eintrags-Limit oder {@code null}
     * @param replacementStrategy neue Strategie oder {@code null}
     */
    public record ConfigPatchDto(
            String region, Long defaultTtlMs, Integer maxEntries, ReplacementStrategy replacementStrategy) {}

    /**
     * DTO für TTL-Policy.
     *
     * @param prefix Pfad-Prefix
     * @param ttlMs  TTL in ms
     */
    public record TtlPolicyDto(String prefix, long ttlMs) {}
}
