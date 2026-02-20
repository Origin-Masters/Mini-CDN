package de.htwsaar.minicdn.router.dto;

import java.util.List;

/**
 * Response für Bulk-Start mit Auto-Ports.
 *
 * @param region Region der gestarteten Edges
 * @param requested Anzahl angeforderter Starts
 * @param started Anzahl erfolgreich gestarteter Edges
 * @param edges Liste der gestarteten Edge-Instanzen
 */
public record AutoStartEdgesResponse(String region, int requested, int started, List<StartEdgeResponse> edges) {}
