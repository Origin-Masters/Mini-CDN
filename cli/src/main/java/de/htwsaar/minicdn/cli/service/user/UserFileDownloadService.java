package de.htwsaar.minicdn.cli.service.user;

import de.htwsaar.minicdn.cli.dto.DownloadResult;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;

/**
 * HTTP-Download-Service für den Router.
 *
 * <p>Implementiert Router-Redirect-Flow:
 * <ol>
 *   <li>GET Router: /api/cdn/files/{path} (mit Region)</li>
 *   <li>bei 307/308: Location lesen</li>
 *   <li>GET Edge: Location</li>
 * </ol>
 *
 * <p>Schreibt erfolgreiche Downloads atomar: erst in eine .part-Datei, dann Move auf Zielpfad.
 */
public final class UserFileDownloadService {

    private static final String HEADER_REGION = "X-Client-Region";
    private static final String HEADER_CLIENT_ID = "X-Client-Id";

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public UserFileDownloadService(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    }

    /**
     * Download über Router inkl. Redirect-Following (manuell).
     *
     * @param routerBaseUrl Router-Base-URL mit trailing slash (z. B. http://localhost:8082/)
     * @param remotePath relativer Pfad (z. B. docs/manual.pdf)
     * @param region Client-Region für Routing (nicht blank)
     * @param clientId optionale Client-ID
     * @param out Ziel-Dateipfad
     * @param overwrite ob bestehende Dateien ersetzt werden sollen
     * @return Ergebnis inkl. StatusCode/Bytes oder Error
     */
    public DownloadResult downloadViaRouter(
            URI routerBaseUrl, String remotePath, String region, String clientId, Path out, boolean overwrite) {
        Objects.requireNonNull(routerBaseUrl, "routerBaseUrl");
        Objects.requireNonNull(remotePath, "remotePath");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(out, "out");

        if (region.isBlank()) {
            return DownloadResult.ioError("region must not be blank");
        }

        URI routingUri = routerBaseUrl.resolve("api/cdn/files/" + remotePath);

        HttpRequest.Builder b =
                HttpRequest.newBuilder(routingUri).timeout(requestTimeout).GET().header(HEADER_REGION, region.trim());

        if (clientId != null && !clientId.isBlank()) {
            b.header(HEADER_CLIENT_ID, clientId.trim());
        }

        HttpResponse<InputStream> routerResp = sendForStream(b.build());
        if (routerResp == null) {
            return DownloadResult.ioError("router request failed");
        }

        int sc = routerResp.statusCode();
        try (InputStream body = routerResp.body()) {
            if (sc >= 200 && sc < 300) {
                writeAtomically(body, out, overwrite);
                return DownloadResult.ok(sc, Files.size(out));
            }

            if (sc == 307 || sc == 308) {
                // Redirect to edge
                String location = routerResp.headers().firstValue("location").orElse(null);
                drain(body);

                if (location == null || location.isBlank()) {
                    return DownloadResult.ioError("router redirect missing Location header");
                }

                URI edgeUri = routingUri.resolve(location);
                return downloadDirect(edgeUri, out, overwrite);
            }

            drain(body);
            return DownloadResult.httpError(sc);
        } catch (IOException e) {
            return DownloadResult.ioError(e.getMessage());
        }
    }

    private DownloadResult downloadDirect(URI url, Path out, boolean overwrite) {
        HttpRequest req =
                HttpRequest.newBuilder(url).timeout(requestTimeout).GET().build();

        HttpResponse<InputStream> resp = sendForStream(req);
        if (resp == null) {
            return DownloadResult.ioError("edge request failed");
        }

        int sc = resp.statusCode();
        try (InputStream body = resp.body()) {
            if (sc >= 200 && sc < 300) {
                writeAtomically(body, out, overwrite);
                return DownloadResult.ok(sc, Files.size(out));
            }

            drain(body);
            return DownloadResult.httpError(sc);
        } catch (IOException e) {
            return DownloadResult.ioError(e.getMessage());
        }
    }

    private HttpResponse<InputStream> sendForStream(HttpRequest req) {
        try {
            return httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeAtomically(InputStream in, Path out, boolean overwrite) throws IOException {
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        if (Files.exists(out) && !overwrite) {
            throw new IOException("output file exists");
        }

        String baseName =
                out.getFileName() == null ? "download" : out.getFileName().toString();
        Path tmp = Files.createTempFile(parent != null ? parent : Path.of("."), baseName, ".part");

        try {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            if (overwrite) {
                Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(tmp, out);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void drain(InputStream in) throws IOException {
        byte[] buf = new byte[8 * 1024];
        while (in.read(buf) != -1) {
            // discard
        }
    }
}
