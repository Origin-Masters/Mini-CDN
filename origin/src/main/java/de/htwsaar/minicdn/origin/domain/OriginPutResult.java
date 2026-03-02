package de.htwsaar.minicdn.origin.domain;

/**
 * Ergebnis eines PUT im Origin.
 *
 * @param created true wenn Datei neu angelegt wurde, false wenn überschrieben
 */
public record OriginPutResult(boolean created) {}
