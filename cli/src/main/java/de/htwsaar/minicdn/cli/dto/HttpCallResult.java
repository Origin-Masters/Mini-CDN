package de.htwsaar.minicdn.cli.dto;

/**
 * Result of an HTTP call, encapsulating status code, response body, and any error message.
 */
public record HttpCallResult(Integer statusCode, String body, String error) {

    public static HttpCallResult http(int statusCode, String body) {
        return new HttpCallResult(statusCode, body, null);
    }

    public static HttpCallResult ioError(String message) {
        return new HttpCallResult(null, null, message == null ? "io error" : message);
    }

    public static HttpCallResult clientError(String message) {
        return new HttpCallResult(400, null, message);
    }

    public boolean is2xx() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }
}
