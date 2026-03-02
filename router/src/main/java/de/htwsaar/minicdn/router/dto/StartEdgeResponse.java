package de.htwsaar.minicdn.router.dto;

/**
 * Response nach erfolgreichem Start einer Edge-Instanz.
 *
 * @param instanceId vom Router vergebene Instance-ID (Format: {@code edge-<pid>})
 * @param url Basis-URL der Edge (z.B. {@code http://localhost:8081})
 * @param pid OS-Prozess-ID der gestarteten Edge
 * @param region Region, in die die Edge gestartet wurde
 */
public record StartEdgeResponse(String instanceId, String url, long pid, String region) {}
