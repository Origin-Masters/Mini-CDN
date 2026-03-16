package de.htwsaar.minicdn.router.application.admin.model;

import java.net.URI;

/**
 * Request zum Starten einer Edge-Instanz.
 *
 * @param region Ziel-Region, in die die Edge optional registriert wird (z.B. {@code EU})
 * @param port TCP-Port für den Edge-Service (1..65535)
 * @param originBaseUrl Basis-URL des Origin-Servers, die an die Edge übergeben wird
 * @param autoRegister Wenn {@code true}, wird die Edge nach erfolgreichem Start registriert
 * @param waitUntilReady Wenn {@code true}, wartet der Router aktiv auf die Ready-Probe
 */
public record StartEdgeRequest(
        String region, int port, URI originBaseUrl, boolean autoRegister, boolean waitUntilReady) {}
