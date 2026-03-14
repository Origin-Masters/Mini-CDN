package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.service.RouterStatsService;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-API für Statistikabfragen am Router.
 *
 * <p>Die Endpunkte sind bewusst read-only und liefern Daten aus den
 * in-memory Router-Metriken.</p>
 */
@RestController
@RequestMapping("/api/cdn/stats")
public class UserStatsController {

    private final RouterStatsService routerStatsService;

    /**
     * Erzeugt den Controller mit dem Statistik-Service.
     *
     * @param routerStatsService Service für Router-Metriken
     */
    public UserStatsController(RouterStatsService routerStatsService) {
        this.routerStatsService = routerStatsService;
    }

    /**
     * Liefert eine aggregierte Statistik für ein Zeitfenster.
     *
     * @param windowSec Zeitfenster in Sekunden
     * @return aggregierte Kennzahlen als JSON-Objekt
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> overall(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(value = "windowSec", defaultValue = "60") int windowSec) {

        Long userId = parseUserId(userIdHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "missing or invalid X-User-Id"));
        }

        int safeWindow = Math.max(1, windowSec);
        RouterStatsService.UserStatsSnapshot snapshot = routerStatsService.snapshotForUser(userId, safeWindow);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", userId);
        response.put("windowSec", safeWindow);
        response.put("totalRequests", snapshot.totalRequests());
        response.put("requestsPerWindow", snapshot.requestsPerWindow());
        response.put("downloadsByFile", snapshot.downloadsByFile());
        return ResponseEntity.ok(response);
    }

    /**
     * Liefert die Top-Dateien nach Download-Anzahl.
     *
     * @param limit maximale Anzahl Einträge
     * @return Liste mit Datei-ID, Pfad und Download-Anzahl
     */
    @GetMapping("/files")
    public ResponseEntity<?> files(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {

        Long userId = parseUserId(userIdHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "missing or invalid X-User-Id"));
        }

        int safeLimit = Math.max(1, limit);
        List<FileStat> ranked = rankedFiles(routerStatsService.snapshotForUser(userId, 60));

        List<Map<String, Object>> response = ranked.stream()
                .limit(safeLimit)
                .map(file -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("fileId", file.fileId());
                    item.put("path", file.path());
                    item.put("downloads", file.downloads());
                    return item;
                })
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Liefert Detaildaten für eine Datei-ID aus der aktuellen Rangliste.
     *
     * @param fileId technische Datei-ID aus der Rangliste
     * @return Dateidetails oder {@code 404}, wenn die ID unbekannt ist
     */
    @GetMapping("/file/{fileId}")
    public ResponseEntity<Map<String, Object>> fileById(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @PathVariable("fileId") long fileId) {

        Long userId = parseUserId(userIdHeader);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "missing or invalid X-User-Id"));
        }

        if (fileId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "fileId must be greater than 0"));
        }

        List<FileStat> ranked = rankedFiles(routerStatsService.snapshotForUser(userId, 60));
        return ranked.stream()
                .filter(file -> file.fileId() == fileId)
                .findFirst()
                .map(file -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("fileId", file.fileId());
                    payload.put("path", file.path());
                    payload.put("downloads", file.downloads());
                    payload.put("downloadsByEdge", file.downloadsByEdge());
                    return ResponseEntity.ok(payload);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "fileId not found")));
    }

    private static List<FileStat> rankedFiles(RouterStatsService.UserStatsSnapshot snapshot) {
        List<Map.Entry<String, Long>> rankedEntries = snapshot.downloadsByFile().entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();

        long fileId = 1;
        List<FileStat> ranked = new java.util.ArrayList<>();
        for (Map.Entry<String, Long> entry : rankedEntries) {
            Map<String, Long> byEdge = snapshot.downloadsByFileByEdge().getOrDefault(entry.getKey(), Map.of());
            ranked.add(new FileStat(fileId++, entry.getKey(), entry.getValue(), byEdge));
        }
        return ranked;
    }

    private static Long parseUserId(String rawHeader) {
        if (rawHeader == null || rawHeader.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(rawHeader.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record FileStat(long fileId, String path, long downloads, Map<String, Long> downloadsByEdge) {}
}
