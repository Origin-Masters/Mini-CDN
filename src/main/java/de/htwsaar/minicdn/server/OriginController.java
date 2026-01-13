package de.htwsaar.minicdn.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller that serves files from the local origin storage directory.
 *
 * <p>This controller exposes an HTTP GET endpoint for retrieving files located under
 * `data/origin` and returns them as a binary response with appropriate HTTP headers.</p>
 *
 * <p>\@Note The path variable is captured with the `{path:.+}` pattern to allow dots and
 * nested path segments (e.g., `images/logo.png`).</p>
 */
@RestController
@RequestMapping("/api")
public class OriginController {

    /**
     * Base directory on the filesystem where origin files are stored.
     */
    private static final Path ORIGIN_DIR = Path.of("data", "origin");

    /**
     * Retrieves a file from the origin directory and returns it as a binary HTTP response.
     *
     * <p>Behavior:</p>
     * <ul>
     *   <li>Resolves the requested path relative to `data/origin`.</li>
     *   <li>Returns HTTP `404 Not Found` if the file does not exist.</li>
     *   <li>Reads the entire file into memory and returns it as a `ByteArrayResource`.</li>
     *   <li>Sets `Content-Type` based on {@link Files#probeContentType(Path)} and falls back to
     *       `application/octet-stream` when unknown.</li>
     *   <li>Sets `Content-Length` to the number of bytes in the response body.</li>
     * </ul>
     *
     * <p>\@Security Considerations: The path is resolved via {@link Path#resolve(String)} without
     * normalization or traversal checks. Requests containing sequences like `..` could potentially
     * access files outside the intended directory.</p>
     *
     * @param path relative path to the file inside the origin directory (may include subfolders)
     * @return `200 OK` with the file contents and headers, or `404 Not Found` if absent
     * @throws IOException if an I/O error occurs while reading the file or probing its content type
     */
    @GetMapping("/files/{path:.+}")
    public ResponseEntity<?> getFile(@PathVariable("path") String path) throws IOException {
        Path file = ORIGIN_DIR.resolve(path);

        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        byte[] data = Files.readAllBytes(file);
        String contentType = Files.probeContentType(file);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .header("Content-Length", String.valueOf(data.length))
                .header("Content-Type", contentType)
                .body(new ByteArrayResource(data));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        return ResponseEntity.ok("ready");
    }

    @RequestMapping(value = "/files/{path:.+}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headFile(@PathVariable("path") String pathname) throws IOException {
        Path file = ORIGIN_DIR.resolve(pathname);
        if (!Files.exists(file)) return ResponseEntity.notFound().build();

        String contentType = Files.probeContentType(file);
        if (contentType == null) contentType = "application/octet-stream";

        return ResponseEntity.ok()
                .header("Content-Length", String.valueOf(Files.size(file)))
                .header("Content-Type", contentType)
                .build();
    }
}
