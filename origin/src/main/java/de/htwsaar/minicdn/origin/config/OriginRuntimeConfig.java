package de.htwsaar.minicdn.origin.config;

/**
 * Laufzeit-Konfiguration des Origin-Servers.
 *
 * @param maxUploadBytes Maximal erlaubte Upload-Größe in Bytes ({@code 0} = unbegrenzt)
 * @param logLevel       Log-Level als String (z. B. {@code INFO}, {@code DEBUG})
 */
public record OriginRuntimeConfig(long maxUploadBytes, String logLevel) {}
