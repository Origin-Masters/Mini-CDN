package de.htwsaar.minicdn.origin.web;

import de.htwsaar.minicdn.origin.domain.OriginFileContent;
import de.htwsaar.minicdn.origin.domain.OriginFileList;
import de.htwsaar.minicdn.origin.domain.OriginFileMetadata;
import de.htwsaar.minicdn.origin.domain.OriginFiles;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * HTTP-Adapter für öffentliche Datei-Endpunkte des Origin.
 *
 * <p>Enthält ausschließlich HTTP concerns (Mappings, Statuscodes, Header).</p>
 */
@RestController
@RequestMapping("/api/origin")
public class OriginFileController {

    private static final String SHA256_HEADER = "X-Content-SHA256";

    private final OriginFiles origin;

    public OriginFileController(OriginFiles origin) {
        this.origin = origin;
    }

    @GetMapping("/files")
    public ResponseEntity<OriginFileList> list(
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {

        try {
            return ResponseEntity.ok(origin.list(page, size));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/files/{path:.+}")
    public ResponseEntity<ByteArrayResource> get(@PathVariable String path) {
        return origin.get(path).map(this::toGetResponse).orElseGet(() -> ResponseEntity.notFound()
                .build());
    }

    @RequestMapping(value = "/files/{path:.+}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> head(@PathVariable String path) {
        return origin.getMetadata(path).map(this::toHeadResponse).orElseGet(() -> ResponseEntity.notFound()
                .build());
    }

    private ResponseEntity<ByteArrayResource> toGetResponse(OriginFileContent content) {
        return ResponseEntity.ok()
                .header("Content-Type", content.contentType())
                .header("Content-Length", String.valueOf(content.body().length))
                .header(SHA256_HEADER, content.sha256Hex())
                .body(new ByteArrayResource(content.body()));
    }

    private ResponseEntity<Void> toHeadResponse(OriginFileMetadata metadata) {
        return ResponseEntity.ok()
                .header("Content-Type", metadata.contentType())
                .header("Content-Length", String.valueOf(metadata.lengthBytes()))
                .header(SHA256_HEADER, metadata.sha256Hex())
                .build();
    }
}
