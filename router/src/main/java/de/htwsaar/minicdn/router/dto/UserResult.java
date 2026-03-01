package de.htwsaar.minicdn.router.dto;

/**
 * DTO für die Antwort auf eine Anfrage zum Abrufen von User-Informationen über die Admin-API.
 *
 * @param id eindeutige ID des Users
 * @param name Name des Users
 * @param role Rolle des Users (z.B. 0 = normaler User, 1 = Admin)
 */
public record UserResult(long id, String name, int role) {}
