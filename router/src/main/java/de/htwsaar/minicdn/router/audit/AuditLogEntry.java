package de.htwsaar.minicdn.router.audit;

import java.time.Instant;

/**
 * Fachlicher Datensatz für einen Audit-Log-Eintrag.
 *
 * @param timestamp Zeitpunkt der Aktion (UTC)
 * @param userId technische User-ID
 * @param action technische Aktionskennung (z. B. "GET /api/cdn/admin/users")
 * @param resource betroffene Ressource (URI inkl. Query)
 * @param result fachliches Ergebnis
 * @param httpStatus HTTP-Statuscode der Aktion
 */
public record AuditLogEntry(
        Instant timestamp, long userId, String action, String resource, AuditResult result, int httpStatus) {}
