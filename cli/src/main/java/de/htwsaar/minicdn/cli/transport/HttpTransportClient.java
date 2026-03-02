package de.htwsaar.minicdn.cli.transport;

import de.htwsaar.minicdn.cli.dto.DownloadResult;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP-Adapter für die Transport-Abstraktion.
 *
 * <p>Nur diese Klasse kennt konkrete HTTP-JDK-Klassen. Fachliche Services
 * sprechen ausschließlich mit dem Interface {@link TransportClient}.
 */
public final class HttpTransportClient implements TransportClient {

    private final HttpClient httpClient;

    public HttpTransportClient(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public TransportResponse send(TransportRequest request) {
        Objects.requireNonNull(request, "request");

        try {
            HttpRequest httpRequest = toHttpRequest(request);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            return TransportResponse.success(
                    response.statusCode(),
                    response.body(),
                    normalizeHeaders(response.headers().map()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TransportResponse.ioError("interrupted");
        } catch (IOException e) {
            return TransportResponse.ioError(e.getMessage());
        }
    }

    @Override
    public DownloadResult download(TransportRequest request, Path targetFile, boolean overwrite) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(targetFile, "targetFile");

        try {
            HttpRequest httpRequest = toHttpRequest(request);
            HttpResponse<InputStream> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

            int statusCode = response.statusCode();
            try (InputStream body = response.body()) {
                if (statusCode < 200 || statusCode >= 300) {
                    drain(body);
                    return DownloadResult.httpError(statusCode);
                }

                writeAtomically(body, targetFile, overwrite);
                return DownloadResult.ok(statusCode, Files.size(targetFile));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DownloadResult.ioError("interrupted");
        } catch (IOException e) {
            return DownloadResult.ioError(e.getMessage());
        }
    }

    private HttpRequest toHttpRequest(TransportRequest request) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri()).timeout(request.timeout());

        request.headers().forEach(builder::header);

        if (request.bodyFile() != null) {
            return builder.method(request.method(), HttpRequest.BodyPublishers.ofFile(request.bodyFile()))
                    .build();
        }

        if (request.body() != null) {
            return builder.method(request.method(), HttpRequest.BodyPublishers.ofString(request.body()))
                    .build();
        }

        if ("GET".equals(request.method())) {
            return builder.GET().build();
        }

        if ("DELETE".equals(request.method())) {
            return builder.DELETE().build();
        }

        return builder.method(request.method(), HttpRequest.BodyPublishers.noBody())
                .build();
    }

    private static Map<String, List<String>> normalizeHeaders(Map<String, List<String>> headers) {
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        headers.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), List.copyOf(value)));
        return normalized;
    }

    private static void writeAtomically(InputStream in, Path out, boolean overwrite) throws IOException {
        Path absoluteOut = out.toAbsolutePath();
        Path parent = absoluteOut.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (Files.exists(absoluteOut) && Files.isDirectory(absoluteOut)) {
            throw new IOException("output path is a directory");
        }

        if (Files.exists(absoluteOut) && !overwrite) {
            throw new IOException("output file exists");
        }

        String baseName = absoluteOut.getFileName() == null
                ? "download"
                : absoluteOut.getFileName().toString();
        Path tempFile = Files.createTempFile(parent != null ? parent : Path.of("."), baseName, ".part");

        try {
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);

            if (overwrite) {
                Files.move(tempFile, absoluteOut, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(tempFile, absoluteOut);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static void drain(InputStream in) throws IOException {
        byte[] buffer = new byte[8 * 1024];
        while (in.read(buffer) != -1) {
            // discard
        }
    }
}
