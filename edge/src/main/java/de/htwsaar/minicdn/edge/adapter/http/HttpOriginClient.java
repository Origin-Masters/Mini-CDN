package de.htwsaar.minicdn.edge.adapter.http;

import de.htwsaar.minicdn.edge.domain.OriginClient;
import de.htwsaar.minicdn.edge.domain.OriginFileResponse;
import java.net.URI;
import java.util.Objects;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP-Adapter zum Origin-Server.
 *
 * <p>Enthält bewusst alle HTTP-Details (RestTemplate, Headernamen, URL-Bau).
 * Die fachliche Edge-Logik hängt ausschließlich am {@link OriginClient}-Port –
 * ein Wechsel auf gRPC erfordert keine Änderung der Fachlogik.</p>
 */
public final class HttpOriginClient implements OriginClient {

    private static final String SHA256_HEADER = "X-Content-SHA256";

    private final RestTemplate restTemplate;
    private final URI originBaseUri;

    /**
     * Erstellt den HTTP-Adapter.
     *
     * @param restTemplate  HTTP-Client (darf nicht {@code null} sein)
     * @param originBaseUri Basis-URI des Origin-Servers (darf nicht {@code null} sein)
     */
    public HttpOriginClient(RestTemplate restTemplate, URI originBaseUri) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate must not be null");
        this.originBaseUri = Objects.requireNonNull(originBaseUri, "originBaseUri must not be null");
    }

    @Override
    public OriginFileResponse fetchFile(String path) {
        ResponseEntity<byte[]> resp = restTemplate.getForEntity(fileUri(path), byte[].class);
        return new OriginFileResponse(
                resp.getStatusCode().value(),
                resp.getBody(),
                resp.getHeaders().getFirst("Content-Type"),
                resp.getHeaders().getFirst(SHA256_HEADER));
    }

    @Override
    public OriginFileResponse headFile(String path) {
        ResponseEntity<Void> resp = restTemplate.exchange(fileUri(path), HttpMethod.HEAD, null, Void.class);
        return new OriginFileResponse(
                resp.getStatusCode().value(),
                null,
                resp.getHeaders().getFirst("Content-Type"),
                resp.getHeaders().getFirst(SHA256_HEADER));
    }

    /** Baut die vollständige Origin-URI für den gegebenen Pfad. */
    private URI fileUri(String path) {
        return originBaseUri.resolve("/api/origin/files/" + path);
    }
}
