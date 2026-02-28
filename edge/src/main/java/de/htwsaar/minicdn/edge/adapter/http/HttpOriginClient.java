package de.htwsaar.minicdn.edge.adapter.http;

import de.htwsaar.minicdn.edge.domain.OriginAccessException;
import de.htwsaar.minicdn.edge.domain.OriginClient;
import de.htwsaar.minicdn.edge.domain.OriginContent;
import de.htwsaar.minicdn.edge.domain.OriginMetadata;
import java.net.URI;
import java.util.Objects;
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
    private final URI originBaseUri;

    public HttpOriginClient(RestTemplate restTemplate, URI originBaseUri) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate must not be null");
        this.originBaseUri = Objects.requireNonNull(originBaseUri, "originBaseUri must not be null");
    }

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
                "Unexpected origin response for path " + path + ": " + status.value(),
                ex);
    }

    private URI fileUri(String path) {
        return originBaseUri.resolve("/api/origin/files/" + path);
    }
}
