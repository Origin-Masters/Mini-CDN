package de.htwsaar.minicdn.cli.dto;

/**
 * Ergebnis eines Remote-Aufrufs, unabhängig vom konkreten Transportadapter.
 */
public record CallResult(Integer statusCode, String body, String error) {

    public static CallResult success(int statusCode, String body) {
        return new CallResult(statusCode, body, null);
    }

    public static CallResult transportError(String message) {
        return new CallResult(null, null, message == null ? "transport error" : message);
    }

    public static CallResult clientError(String message) {
        return new CallResult(400, null, message);
    }

    public boolean is2xx() {
        return statusCode != null && statusCode >= 200 && statusCode < 300;
    }

    public boolean is4xx() {
        return statusCode != null && statusCode >= 400 && statusCode < 500;
    }

    public boolean is5xx() {
        return statusCode != null && statusCode >= 500 && statusCode < 600;
    }
}
