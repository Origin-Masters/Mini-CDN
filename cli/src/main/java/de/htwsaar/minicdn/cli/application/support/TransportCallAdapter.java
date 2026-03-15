package de.htwsaar.minicdn.cli.application.support;

import de.htwsaar.minicdn.cli.domain.model.CallResult;
import de.htwsaar.minicdn.cli.domain.model.TransportRequest;
import de.htwsaar.minicdn.cli.domain.model.TransportResponse;
import de.htwsaar.minicdn.cli.domain.port.TransportClient;
import java.util.Objects;

/**
 * Adapter zwischen generischem Transport-Request und dem CLI-Aufrufergebnis.
 *
 * <p>Fachliche Services können damit Requests absetzen, ohne TransportResponse
 * direkt zu interpretieren oder Fehler-Mapping zu duplizieren.</p>
 */
public final class TransportCallAdapter {

    private TransportCallAdapter() {}

    /**
     * Führt einen Transport-Request aus und mappt das Ergebnis auf {@link CallResult}.
     *
     * @param transportClient konkreter Transport-Port
     * @param request auszufuehrender Request
     * @return normiertes Call-Ergebnis
     */
    public static CallResult execute(TransportClient transportClient, TransportRequest request) {
        Objects.requireNonNull(transportClient, "transportClient");
        Objects.requireNonNull(request, "request");

        try {
            TransportResponse response = transportClient.send(request);
            return toCallResult(response);
        } catch (Exception ex) {
            return CallResult.transportError(ex.getMessage());
        }
    }

    /**
     * Wandelt eine bereits vorliegende Transportantwort in ein {@link CallResult} um.
     *
     * @param response Transportantwort
     * @return normiertes Call-Ergebnis
     */
    public static CallResult toCallResult(TransportResponse response) {
        if (response == null) {
            return CallResult.transportError("response must not be null");
        }
        if (response.error() != null) {
            return CallResult.transportError(response.error());
        }
        return CallResult.success(Objects.requireNonNull(response.statusCode(), "statusCode"), response.body());
    }
}
