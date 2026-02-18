package de.htwsaar.minicdn.cli.util;

import de.htwsaar.minicdn.cli.dto.HttpCallResult;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Objects;

public final class HttpUtils {

    private HttpUtils() {}

    /**
     * Helper to send an HTTP request and return the response body as string.
     * Errors are captured in the HttpCallResult. Error field.
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
