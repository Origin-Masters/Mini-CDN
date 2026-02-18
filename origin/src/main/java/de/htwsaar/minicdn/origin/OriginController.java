package de.htwsaar.minicdn.origin;

import de.htwsaar.minicdn.common.util.Sha256Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST-Controller des Origin-Servers im Mini-CDN.
 *
 * <p>
 * Dieser Controller stellt Dateien aus dem lokalen Origin-Verzeichnis bereit.
 * Er fungiert als zentrale Datenquelle des CDN und wird vom Edge-Server verwendet,
 * um Datei-Inhalte und Metadaten abzurufen.
 * </p>
 *
 * <p>
 * Für jede ausgelieferte Datei wird ein SHA-256 Hash berechnet und im HTTP-Header
 * {@code X-Content-SHA256} mitgesendet. Dieser dient der Integritätsprüfung auf
 * Edge-Seite.
 * </p>
 *
 * <p>
 * Der Controller ist nur aktiv, wenn das Spring-Profil {@code origin} gesetzt ist.
 * </p>
 */
@RestController
@RequestMapping("/api/origin")
@Profile("origin")
public class OriginController {

    // TODO : Use Streams instead of Byte Arrays

    /**
     * Basisverzeichnis im Dateisystem, in dem die Origin-Dateien gespeichert sind.
     *
     * <p>
     * Alle angeforderten Pfade werden relativ zu diesem Verzeichnis aufgelöst.
     * </p>
     */
    private static final Path ORIGIN_DIR = Path.of("data");

    /**
     * Name des HTTP-Headers, der den SHA-256 Hash der Datei enthält.
     */
    private static final String SHA256_HEADER = "X-Content-SHA256";

    /**
     * Repräsentiert die Metadaten einer Datei im Origin-Verzeichnis.
     *
     * @param path         relativer Pfad der Datei
     * @param size         Größe der Datei in Bytes
     * @param lastModified ISO-8601 formatierter Zeitstempel der letzten Änderung
     * @param contentType  ermittelter Content-Type der Datei
     */
    public record FileMeta(String path, long size, String lastModified, String contentType) {
    }

    /**
     * Repräsentiert die Antwortstruktur für die Auflistung von Dateien mit Paginierung.
     *
     * @param page  aktueller Seitenindex (beginnend bei 1)
     * @param size  Anzahl der Einträge pro Seite
     * @param total Gesamtanzahl der verfügbaren Dateien
     * @param items Liste der Dateimetadaten für die aktuelle Seite
     */
    public record FileListResponse(int page, int size, int total, List<FileMeta> items) {
    }

    /**
     * Liefert eine Datei aus dem Origin-Verzeichnis als binäre HTTP-Antwort zurück.
     *
     * <p>
     * Der angeforderte Pfad wird relativ zu {@link #ORIGIN_DIR} aufgelöst. Existiert
     * die Datei nicht, wird {@code 404 Not Found} zurückgegeben.
     * </p>
     *
     * <p>
     * Die komplette Datei wird in den Speicher geladen und als {@link ByteArrayResource}
     * zurückgegeben. Die Antwort enthält folgende Header:
     * </p>
     *
     * <ul>
     *   <li>{@code Content-Length}: Größe der Datei in Bytes</li>
     *   <li>{@code Content-Type}: ermittelt über {@link Files#probeContentType(Path)}
     *       (Fallback: {@code application/octet-stream})</li>
     *   <li>{@code X-Content-SHA256}: SHA-256 Hash des Datei-Inhalts</li>
     * </ul>
     *
     * <p>
     * Hinweis: Die Pfadvariable wird mit {@code {path:.+}} erfasst, sodass auch Punkte
     * und Unterverzeichnisse erlaubt sind (z.B. {@code images/logo.png}).
     * </p>
     *
     * @param path relativer Pfad der Datei innerhalb des Origin-Verzeichnisses
     * @return {@code 200 OK} mit Dateiinhalt und Headern oder {@code 404 Not Found}
     * @throws IOException falls ein Ein-/Ausgabefehler auftritt
     */
    // GET
    // {path:.+} means slashes are allowed too
    @GetMapping("/files/{path:.+}")
    public ResponseEntity<?> getFile(@PathVariable("path") String path) throws IOException {
        Path file = ORIGIN_DIR.resolve(path);

        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        byte[] data = Files.readAllBytes(file);
        String sha256 = Sha256Util.sha256Hex(data);
        String contentType = Files.probeContentType(file);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .header("Content-Length", String.valueOf(data.length))
                .header("Content-Type", contentType)
                .header(SHA256_HEADER, sha256)
                .body(new ByteArrayResource(data));
    }

    /**
     * Health-Endpunkt zur einfachen Prüfung, ob der Origin-Server läuft.
     *
     * @return String {@code "ok"}
     */
    // HEALTH
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    /**
     * Ready-Endpunkt, der signalisiert, dass der Origin-Server bereit ist,
     * Anfragen zu verarbeiten.
     *
     * @return String {@code "ready"}
     */
    // READY
    @GetMapping("/ready")
    public ResponseEntity<String> ready() {
        return ResponseEntity.ok("ready");
    }

    /**
     * Behandelt HTTP-HEAD-Anfragen für Dateien im Origin-Verzeichnis.
     *
     * <p>
     * Dieser Endpunkt liefert ausschließlich Metadaten (Header), ohne den eigentlichen
     * Dateiinhalt zu übertragen. Er wird primär vom Edge-Server genutzt, um Dateigröße,
     * Content-Type und Hash abzufragen.
     * </p>
     *
     * @param path relativer Pfad der Datei
     * @return {@code 200 OK} mit Headern oder {@code 404 Not Found}, falls die Datei nicht existiert
     * @throws IOException falls ein Ein-/Ausgabefehler auftritt
     */
    // HEAD
    @RequestMapping(value = "/files/{path:.+}", method = RequestMethod.HEAD)
    public ResponseEntity<Void> headFile(@PathVariable("path") String path) throws IOException {
        Path file = ORIGIN_DIR.resolve(path);
        if (!Files.exists(file)) return ResponseEntity.notFound().build();

        String contentType = Files.probeContentType(file);
        if (contentType == null) contentType = "application/octet-stream";
        byte[] data = Files.readAllBytes(file);
        String sha256 = Sha256Util.sha256Hex(data);

        return ResponseEntity.ok()
                .header("Content-Length", String.valueOf(Files.size(file)))
                .header("Content-Type", contentType)
                .header(SHA256_HEADER, sha256)
                .build();
    }

    /**
     * Lädt eine Datei in das Origin-Verzeichnis hoch oder ersetzt eine bestehende Datei.
     *
     * <p>
     * Existiert die Datei noch nicht, wird sie angelegt und {@code 201 Created} zurückgegeben.
     * Existiert sie bereits, wird sie überschrieben und {@code 204 No Content} geliefert.
     * Fehlende Elternverzeichnisse werden automatisch erzeugt.
     * </p>
     *
     * @param path relativer Zielpfad der Datei
     * @param body binärer Dateiinhalt
     * @return HTTP-Antwort mit Status über Erstellung oder Aktualisierung
     * @throws IOException falls ein Ein-/Ausgabefehler auftritt
     */
    // PUT
    @PutMapping("/admin/files/{path:.+}")
    public ResponseEntity<?> putFile(@PathVariable("path") String path, @RequestBody byte[] body) throws IOException {
        Path file = ORIGIN_DIR.resolve(path);

        //  parent directories if needed
        Files.createDirectories(file.getParent());

        // file already exists ?
        boolean isNew = !Files.exists(file);

        Files.write(file, body);

        if (isNew) {
            return ResponseEntity.status(201)
                    .header("Location", "/api/files/" + path)
                    .build();
        } else {
            return ResponseEntity.noContent().build();
        }
    }

    /**
     * Löscht eine Datei aus dem Origin-Verzeichnis.
     *
     * <p>
     * Existiert die Datei nicht, wird {@code 404 Not Found} zurückgegeben.
     * Andernfalls wird die Datei entfernt und {@code 204 No Content} geliefert.
     * </p>
     *
     * @param path relativer Pfad der zu löschenden Datei
     * @return HTTP-Antwort mit Erfolgs- oder Fehlerstatus
     * @throws IOException falls ein Ein-/Ausgabefehler auftritt
     */
    // DELETE
    @DeleteMapping("/admin/files/{path:.+}")
    public ResponseEntity<?> deleteFile(@PathVariable("path") String path) throws IOException {
        Path file = ORIGIN_DIR.resolve(path);

        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        Files.delete(file);
        return ResponseEntity.noContent().build();
    }

    /**
     * Listet Dateien im Origin-Verzeichnis mit Paginierung auf.
     *
     * <p>
     * Liefert für jede Datei Metadaten (Pfad, Größe, letzter Änderungszeitpunkt, Content-Type).
     * Falls das Basisverzeichnis nicht existiert, wird eine leere Ergebnismenge zurückgegeben.
     * Ungültige Anfrageparameter (z.\,B. {@code page < 1} oder {@code size <= 0}) führen zu {@code 400 Bad Request}.
     * </p>
     *
     * @param page Seitenindex, beginnend bei 1
     * @param size Anzahl der Einträge pro Seite
     * @return {@code 200 OK} mit einer {@link FileListResponse} (page, size, total, items)
     * @throws IOException falls ein Ein-/Ausgabefehler auftritt
     */
    @GetMapping("/files")
    public ResponseEntity<FileListResponse> listFiles(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size)
            throws IOException {

        if (page < 1 || size <= 0) {
            return ResponseEntity.badRequest().build();
        }

        if (!Files.exists(ORIGIN_DIR)) {
            return ResponseEntity.ok(new FileListResponse(page, size, 0, List.of()));
        }

        try (Stream<Path> s = Files.walk(ORIGIN_DIR)) {
            List<Path> allFiles = s.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> ORIGIN_DIR.relativize(p).toString()))
                    .toList();

            int total = allFiles.size();
            int from = Math.min((page - 1) * size, total);
            int to = Math.min(from + size, total);

            List<FileMeta> items = allFiles.subList(from, to).stream()
                    .map(p -> {
                        try {
                            String rel = ORIGIN_DIR.relativize(p).toString().replace('\\', '/');
                            long fileSize = Files.size(p);
                            String lastModified =
                                    Files.getLastModifiedTime(p).toInstant().toString();
                            String contentType = Files.probeContentType(p);
                            if (contentType == null) contentType = "application/octet-stream";
                            return new FileMeta(rel, fileSize, lastModified, contentType);
                        } catch (IOException e) {
                            String rel = ORIGIN_DIR.relativize(p).toString().replace('\\', '/');
                            return new FileMeta(rel, -1L, null, "application/octet-stream");
                        }
                    })
                    .toList();

            return ResponseEntity.ok(new FileListResponse(page, size, total, items));
        }
    }
}
