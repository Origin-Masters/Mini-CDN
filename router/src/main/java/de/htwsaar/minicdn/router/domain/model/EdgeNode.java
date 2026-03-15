package de.htwsaar.minicdn.router.domain.model;

/**
 * Repräsentiert einen registrierten Edge-Knoten über seine Basis-URL.
 *
 * @param url öffentlich erreichbare URL des Edge-Knotens
 */
public record EdgeNode(String url) {}
