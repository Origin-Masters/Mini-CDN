package de.htwsaar.minicdn.origin.web;

import de.htwsaar.minicdn.origin.config.OriginRuntimeConfig;
import de.htwsaar.minicdn.origin.config.OriginRuntimeConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-API für Live-Konfiguration des Origin-Servers.
 */
@RestController
@RequestMapping("/api/origin/admin/config")
public class OriginAdminConfigController {

    private final OriginRuntimeConfigService runtimeConfigService;

    public OriginAdminConfigController(OriginRuntimeConfigService runtimeConfigService) {
        this.runtimeConfigService = runtimeConfigService;
    }

    /** @return aktuell aktive Origin-Runtime-Konfiguration. */
    @GetMapping
    public ResponseEntity<OriginRuntimeConfig> get() {
        return ResponseEntity.ok(runtimeConfigService.current());
    }

    /**
     * Partielles Runtime-Update ohne Neustart.
     *
     * @param dto Patch-Daten
     * @return aktualisierte Konfiguration
     */
    @PatchMapping
    public ResponseEntity<OriginRuntimeConfig> patch(@RequestBody OriginConfigPatchDto dto) {
        if (dto == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(runtimeConfigService.patch(dto.maxUploadBytes(), dto.logLevel()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * DTO für partielles Origin-Config-Update.
     *
     * @param maxUploadBytes neue Maximalgröße in Bytes ({@code null} = unverändert)
     * @param logLevel       neues Root-Log-Level ({@code null} = unverändert)
     */
    public record OriginConfigPatchDto(Long maxUploadBytes, String logLevel) {}
}
