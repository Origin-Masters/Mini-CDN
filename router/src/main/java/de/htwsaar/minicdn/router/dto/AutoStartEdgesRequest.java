package de.htwsaar.minicdn.router.dto;

import java.net.URI;

/**
 * Request zum Starten von mehreren Edge-Instanzen mit automatisch zugewiesenen Ports.
 *
 * @param region Ziel-Region
 * @param count Anzahl zu startender Edge-Prozesse
 * @param originBaseUrl Basis-URL des Origin-Servers
 * @param autoRegister Wenn {@code true}, werden alle gestarteten Edges registriert
 * @param waitUntilReady Wenn {@code true}, wartet der Router pro Edge aktiv auf {@code /api/edge/ready}
 */
public record AutoStartEdgesRequest(
        String region, int count, URI originBaseUrl, boolean autoRegister, boolean waitUntilReady) {}
