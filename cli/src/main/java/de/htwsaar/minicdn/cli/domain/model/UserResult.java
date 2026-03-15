package de.htwsaar.minicdn.cli.domain.model;

/**
 * Normiertes Benutzerobjekt aus Router-Auth- oder Admin-Antworten.
 *
 * @param id eindeutige Benutzer-ID
 * @param name Anzeigename bzw. Login-Name des Benutzers
 * @param role numerische Rolle des Benutzers, z. B. {@code 0} für User und {@code 1} für Admin
 */
public record UserResult(long id, String name, int role) {}
