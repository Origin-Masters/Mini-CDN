package de.htwsaar.minicdn.router.dto;

/**
 * API-Response für einen Audit-Eintrag.
 *
 * @param timestamp ISO-8601 Zeitstempel
 * @param userId technische User-ID
 * @param action technische Aktionskennung
 * @param resource URI inkl. Query
 * @param result Ergebnis der Aktion
 * @param httpStatus HTTP-Statuscode
 */
public record AuditLogResponse(
        String timestamp, long userId, String action, String resource, String result, int httpStatus) {}
