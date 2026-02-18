package de.htwsaar.minicdn.cli.dto;

/**
 * Ergebnis eines Downloads.
 *
 * @param statusCode HTTP-Status (nur bei HTTP-Antwort)
 * @param bytesWritten geschriebene Bytes (nur bei Erfolg)
 * @param error Fehlertext bei IO/Client-Problemen (kein HTTP-Status)
 */
public record DownloadResult(Integer statusCode, long bytesWritten, String error) {

    public static DownloadResult ok(int statusCode, long bytesWritten) {
        return new DownloadResult(statusCode, bytesWritten, null);
    }

    public static DownloadResult httpError(int statusCode) {
        return new DownloadResult(statusCode, 0L, null);
    }

    public static DownloadResult ioError(String message) {
        return new DownloadResult(null, 0L, message == null ? "io error" : message);
    }
}
