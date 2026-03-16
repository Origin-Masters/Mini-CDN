package de.htwsaar.minicdn.origin.adapter.in.web.admin;

import de.htwsaar.minicdn.common.util.PathUtils;
import de.htwsaar.minicdn.origin.application.config.OriginRuntimeConfigService;
import de.htwsaar.minicdn.origin.domain.model.OriginPutResult;
import de.htwsaar.minicdn.origin.domain.port.OriginFiles;
import java.net.URI;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP-Adapter für Admin-Endpunkte (Write/Delete) des Origin.
 */
@RestController
@RequestMapping("/api/origin/admin")
@Profile("origin")
public class OriginAdminController {

    private final OriginFiles origin;
    private final OriginRuntimeConfigService runtimeConfigService;

    public OriginAdminController(OriginFiles origin, OriginRuntimeConfigService runtimeConfigService) {
        this.origin = origin;
        this.runtimeConfigService = runtimeConfigService;
    }

    @PutMapping("/files/{*path}")
    public ResponseEntity<Void> put(@PathVariable String path, @RequestBody byte[] body) {
        String cleanPath = PathUtils.normalizeRelativePath(path);
        long maxUploadBytes = runtimeConfigService.current().maxUploadBytes();
        if (maxUploadBytes > 0 && body != null && body.length > maxUploadBytes) {
            return ResponseEntity.status(413).build();
        }

        OriginPutResult r = origin.put(cleanPath, body);
        return r.created()
                ? ResponseEntity.created(URI.create("/api/origin/files/" + cleanPath))
                        .build()
                : ResponseEntity.noContent().build();
    }

    @DeleteMapping("/files/{*path}")
    public ResponseEntity<Void> delete(@PathVariable String path) {
        String cleanPath = PathUtils.normalizeRelativePath(path);
        return origin.delete(cleanPath)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
