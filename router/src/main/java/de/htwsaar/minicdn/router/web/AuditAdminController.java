package de.htwsaar.minicdn.router.web;

import de.htwsaar.minicdn.router.audit.AuditLogService;
import de.htwsaar.minicdn.router.audit.AuditQueryFilter;
import de.htwsaar.minicdn.router.audit.AuditResult;
import de.htwsaar.minicdn.router.dto.AuditLogResponse;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-API für Abfrage und Export von Audit-Logs.
 */
@RestController
@RequestMapping("/api/cdn/admin/audit")
public class AuditAdminController {

    private final AuditLogService auditLogService;

    public AuditAdminController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * Liefert Audit-Logs eines spezifizierten Users als JSON-Liste.
     */
    @GetMapping
    public ResponseEntity<List<AuditLogResponse>> query(
            @RequestParam("userId") long userId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "result", required = false) String result) {

        AuditQueryFilter filter = new AuditQueryFilter(
                userId, parseInstant(from), parseInstant(to), sanitize(action), parseResult(result));

        List<AuditLogResponse> response = auditLogService.query(filter).stream()
                .map(entry -> new AuditLogResponse(
                        entry.timestamp().toString(),
                        entry.userId(),
                        entry.action(),
                        entry.resource(),
                        entry.result().name(),
                        entry.httpStatus()))
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Exportiert Audit-Logs eines spezifizierten Users als CSV.
     */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @RequestParam("userId") long userId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "result", required = false) String result) {

        AuditQueryFilter filter = new AuditQueryFilter(
                userId, parseInstant(from), parseInstant(to), sanitize(action), parseResult(result));

        String csv = auditLogService.exportCsv(filter);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-user-" + userId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    /**
     * Parst ISO-8601-Zeitwerte robust.
     */
    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /**
     * Parst den Ergebnisfilter (SUCCESS/FAILURE) robust.
     */
    private static AuditResult parseResult(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return AuditResult.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Normalisiert optionale String-Filter.
     */
    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
