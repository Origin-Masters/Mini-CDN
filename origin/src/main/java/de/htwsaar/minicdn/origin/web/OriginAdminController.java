package de.htwsaar.minicdn.origin.web;

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

    public OriginAdminController(OriginFiles origin) {
        this.origin = origin;
    }

    @PutMapping("/files/{path:.+}")
    public ResponseEntity<Void> put(@PathVariable String path, @RequestBody byte[] body) {
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
