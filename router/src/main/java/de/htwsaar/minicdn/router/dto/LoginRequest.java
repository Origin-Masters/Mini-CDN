package de.htwsaar.minicdn.router.dto;

/**
 * Request-DTO für einen einfachen Login über den Benutzernamen.
 *
 * @param name Benutzername des anzumeldenden Users
 */
public record LoginRequest(String name) {}
