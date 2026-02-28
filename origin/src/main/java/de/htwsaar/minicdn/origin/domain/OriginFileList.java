package de.htwsaar.minicdn.origin.domain;

import java.util.List;

/**
 * Transportneutrale Antwort für File-Listing mit Pagination.
 *
 * @param page Seite ab 1
 * @param size Seitengröße
 * @param total Gesamtanzahl
 * @param items Einträge der aktuellen Seite
 */
public record OriginFileList(int page, int size, int total, List<OriginFileMeta> items) {}
