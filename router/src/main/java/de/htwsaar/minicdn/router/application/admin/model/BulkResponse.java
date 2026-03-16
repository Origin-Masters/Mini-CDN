package de.htwsaar.minicdn.router.application.admin.model;

/**
 * Ergebnisdatensatz für eine verarbeitete Bulk-Anweisung.
 *
 * @param region betroffene Region
 * @param url betroffene Knoten-URL
 * @param status Verarbeitungsergebnis
 */
public record BulkResponse(String region, String url, String status) {}
