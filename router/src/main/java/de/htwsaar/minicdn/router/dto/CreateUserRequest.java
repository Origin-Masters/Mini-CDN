package de.htwsaar.minicdn.router.dto;

/**
 * DTO für die Anforderung zum Erstellen eines neuen Users über die Admin-API.
 *
 * @param name Name des neuen Users
 * @param role Rolle des neuen Users (z.B. 0 = normaler User, 1 = Admin)
 */
public record CreateUserRequest(String name, int role) {}
