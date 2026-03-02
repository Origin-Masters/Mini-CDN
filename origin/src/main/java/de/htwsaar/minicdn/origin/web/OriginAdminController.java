package de.htwsaar.minicdn.origin.web;

import de.htwsaar.minicdn.origin.config.OriginRuntimeConfigService;
import de.htwsaar.minicdn.origin.domain.OriginFiles;
import de.htwsaar.minicdn.origin.domain.OriginPutResult;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP-Adapter für Admin-Endpunkte (Write/Delete) des Origin.
 */
@RestController
@RequestMapping("/api/origin/admin")
public class OriginAdminController {

    private final OriginFiles origin;
    private final OriginRuntimeConfigService runtimeConfigService;

    public OriginAdminController(OriginFiles origin, OriginRuntimeConfigService runtimeConfigService) {
        this.origin = origin;
        this.runtimeConfigService = runtimeConfigService;
    }

    @PutMapping("/files/{path:.+}")
    public ResponseEntity<Void> put(@PathVariable String path, @RequestBody byte[] body) {
        long maxUploadBytes = runtimeConfigService.current().maxUploadBytes();
        if (maxUploadBytes > 0 && body != null && body.length > maxUploadBytes) {
            return ResponseEntity.status(413).build();
        }

        OriginPutResult r = origin.put(path, body);
        return r.created()
                ? ResponseEntity.created(URI.create("/api/origin/files/" + path))
                        .build()
                : ResponseEntity.noContent().build();
    }

    @DeleteMapping("/files/{path:.+}")
    public ResponseEntity<Void> delete(@PathVariable String path) {
        return origin.delete(path)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
