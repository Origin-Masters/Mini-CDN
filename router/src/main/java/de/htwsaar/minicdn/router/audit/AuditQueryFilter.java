package de.htwsaar.minicdn.router.audit;

import java.time.Instant;

/**
 * Filter für Audit-Abfragen.
 *
 * @param userId Pflichtfilter auf User
 * @param from inklusiver Startzeitpunkt (optional)
 * @param to inklusiver Endzeitpunkt (optional)
 * @param action exakte Action-Filterung (optional)
 * @param result Ergebnisfilter (optional)
 */
public record AuditQueryFilter(long userId, Instant from, Instant to, String action, AuditResult result) {}
