package de.htwsaar.minicdn.router.application.admin.model;

/**
 * Einzelanweisung für den Bulk-Endpunkt.
 *
 * @param region Zielregion
 * @param url URL des Edge-Knotens
 * @param action Aktion ({@code add} oder {@code remove})
 */
public record BulkRequest(String region, String url, String action) {}
