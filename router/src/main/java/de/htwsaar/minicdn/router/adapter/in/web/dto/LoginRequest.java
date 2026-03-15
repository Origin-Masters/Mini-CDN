package de.htwsaar.minicdn.router.adapter.in.web.dto;

/**
 * Request-DTO für einen einfachen Login über den Benutzernamen.
 *
 * @param name Benutzername des anzumeldenden Users
 */
public record LoginRequest(String name) {}
