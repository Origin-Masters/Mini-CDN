package de.htwsaar.minicdn.cli.util;

import de.htwsaar.minicdn.cli.dto.HttpCallResult;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

public final class HttpUtils {

    private HttpUtils() {
    }

    /**
     * Hilfsfunktion zum Senden eines HTTP-Requests und Erfassen des Statuscodes und der Antwort als String.
     * Behandelt InterruptedException und IOException und gibt ein HttpCallResult zurück,
     * das entweder den Statuscode und die Antwort oder eine Fehlermeldung enthält.
     */
    public static HttpCallResult sendForStringBody(HttpClient httpClient, HttpRequest request) {
        Objects.requireNonNull(httpClient, "httpClient");
        Objects.requireNonNull(request, "request");

        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return HttpCallResult.http(resp.statusCode(), resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HttpCallResult.ioError("interrupted");
        } catch (IOException e) {
            return HttpCallResult.ioError(e.getMessage());
        }
    }
}
