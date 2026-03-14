package de.htwsaar.minicdn.edge.adapter.out.origin;

import de.htwsaar.minicdn.edge.application.config.EdgeConfigService;
import de.htwsaar.minicdn.edge.domain.exception.OriginAccessException;
import de.htwsaar.minicdn.edge.domain.model.OriginContent;
import de.htwsaar.minicdn.edge.domain.model.OriginMetadata;
import de.htwsaar.minicdn.edge.domain.port.OriginClient;
import java.net.URI;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP-Adapter zum Origin-Server.
 *
 * <p>Alle HTTP-Details bleiben hier:
 * RestTemplate, URL-Bau, Headernamen und HTTP-Fehlerbehandlung.</p>
 */
public final class HttpOriginClient implements OriginClient {

    private static final String SHA256_HEADER = "X-Content-SHA256";

    private final RestTemplate restTemplate;
    private final Supplier<URI> originBaseUriSupplier;

    /**
     * Erstellt den HTTP-Adapter für Origin-Zugriffe.
     *
     * @param restTemplate HTTP-Client
     * @param edgeConfigService Zugriff auf die aktuelle Origin-Basis-URL
     */
    public HttpOriginClient(RestTemplate restTemplate, EdgeConfigService edgeConfigService) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate must not be null");
        Objects.requireNonNull(edgeConfigService, "edgeConfigService must not be null");
        this.originBaseUriSupplier = () ->
                URI.create(Objects.requireNonNull(edgeConfigService.current().originBaseUrl(), "originBaseUrl"));
    }

    /**
     * Lädt den vollständigen Dateiinhalt über HTTP und übersetzt die Antwort in ein Fachobjekt.
     *
     * @param path relativer Dateipfad
     * @return fachlicher Dateiinhalt
     */
    @Override
    public OriginContent fetchFile(String path) {
        try {
            ResponseEntity<byte[]> resp = restTemplate.getForEntity(fileUri(path), byte[].class);
            byte[] body = resp.getBody();
            if (body == null) {
                throw new OriginAccessException(
                        OriginAccessException.Reason.INVALID_RESPONSE, "Origin returned no body for path: " + path);
            }

            return new OriginContent(
                    body,
                    resp.getHeaders().getFirst("Content-Type"),
                    resp.getHeaders().getFirst(SHA256_HEADER));
        } catch (HttpStatusCodeException ex) {
            throw mapHttpException(path, ex);
        } catch (ResourceAccessException ex) {
            throw new OriginAccessException(
                    OriginAccessException.Reason.UNAVAILABLE, "Origin is unavailable for path: " + path, ex);
        } catch (RestClientException ex) {
            throw new OriginAccessException(
                    OriginAccessException.Reason.UNAVAILABLE, "Origin call failed for path: " + path, ex);
        }
    }

    /**
     * Lädt ausschließlich Metadaten per HTTP-HEAD und übersetzt sie in ein Fachobjekt.
     *
     * @param path relativer Dateipfad
     * @return fachliche Metadaten
     */
    @Override
    public OriginMetadata fetchMetadata(String path) {
        try {
            ResponseEntity<Void> resp = restTemplate.exchange(fileUri(path), HttpMethod.HEAD, null, Void.class);
            return new OriginMetadata(
                    resp.getHeaders().getFirst("Content-Type"),
                    resp.getHeaders().getFirst(SHA256_HEADER));
        } catch (HttpStatusCodeException ex) {
            throw mapHttpException(path, ex);
        } catch (ResourceAccessException ex) {
            throw new OriginAccessException(
                    OriginAccessException.Reason.UNAVAILABLE, "Origin is unavailable for path: " + path, ex);
        } catch (RestClientException ex) {
            throw new OriginAccessException(
                    OriginAccessException.Reason.UNAVAILABLE, "Origin call failed for path: " + path, ex);
        }
    }

    /**
     * Übersetzt HTTP-Fehler in fachliche Origin-Fehler.
     *
     * @param path angefragter Dateipfad
     * @param ex ursprüngliche HTTP-Exception
     * @return fachliche Exception
     */
    private RuntimeException mapHttpException(String path, HttpStatusCodeException ex) {
        HttpStatusCode status = ex.getStatusCode();

        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return new OriginAccessException(
                    OriginAccessException.Reason.NOT_FOUND, "Origin file not found: " + path, ex);
        }

        if (status.is5xxServerError()) {
            return new OriginAccessException(
                    OriginAccessException.Reason.UNAVAILABLE, "Origin server error for path: " + path, ex);
        }

        return new OriginAccessException(
                OriginAccessException.Reason.INVALID_RESPONSE,
                "Unerwartete Origin-Antwort für Pfad " + path + ": " + status.value(),
                ex);
    }

    /**
     * Baut die Ziel-URI für einen Datei-Endpunkt des Origin.
     *
     * @param path relativer Dateipfad
     * @return vollständige Ziel-URI
     */
    private URI fileUri(String path) {
        return originBaseUriSupplier.get().resolve("/api/origin/files/" + path);
    }
}
