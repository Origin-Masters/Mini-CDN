package de.htwsaar.minicdn.server;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * REST-Controller für den Origin Server des Mini-CDN.
 * <p>
 * Der Origin Server stellt die ursprünglichen Inhalte bereit und dient als
 * Single Source of Truth im System. Er liefert Dateien unverändert aus einem
 * lokalen Verzeichnis und enthält keinerlei Cache- oder Persistenzlogik.
 * </p>
 */
@RestController
@RequestMapping("/origin")
public class OriginController {

    /**
     * Basisverzeichnis, aus dem der Origin Server Dateien ausliefert.
     * Dieses Verzeichnis enthält die Originalinhalte des Mini-CDN.
     */
    private static final Path ORIGIN_DIR = Path.of("data", "origin");

    /**
     * Liefert eine Datei aus dem Origin-Verzeichnis zurück.
     * <p>
     * Die Datei wird anhand ihres Namens aus dem lokalen Dateisystem gelesen
     * und unverändert an den Client übertragen. Existiert die Datei nicht,
     * wird ein HTTP-404-Status zurückgegeben.
     * </p>
     *
     * @param filename Name der angeforderten Datei
     * @return HTTP-Response mit dem Dateiinhalt oder 404, falls die Datei nicht existiert
     * @throws IOException falls ein Fehler beim Lesen der Datei auftritt
     */
    @GetMapping("/file/{filename}")
    public ResponseEntity<?> getFile(@PathVariable("filename") String filename) throws IOException {
        Path file = ORIGIN_DIR.resolve(filename);

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
}
