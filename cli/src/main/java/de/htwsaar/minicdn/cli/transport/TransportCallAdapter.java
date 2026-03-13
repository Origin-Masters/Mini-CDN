package de.htwsaar.minicdn.cli.transport;

import de.htwsaar.minicdn.cli.dto.CallResult;
import java.util.Objects;

/**
 * Adapter zwischen generischem Transport-Request und dem CLI-Aufrufergebnis.
 *
 * <p>Fachliche Services können damit Requests absetzen, ohne TransportResponse
 * direkt zu interpretieren oder Fehler-Mapping zu duplizieren.</p>
 */
public final class TransportCallAdapter {

    private TransportCallAdapter() {}

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
