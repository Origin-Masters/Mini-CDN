package de.htwsaar.minicdn.router.dto;

/**
 * Beschreibt den Gesundheitszustand eines Edge-Knotens.
 *
 * @param url URL des Edge-Knotens
 * @param healthy {@code true}, wenn der Knoten als gesund gilt
 */
public record EdgeNodeStatus(String url, boolean healthy) {}
