package de.htwsaar.minicdn.router.adapter;

import de.htwsaar.minicdn.router.domain.FileRouteLocationResolver;
import de.htwsaar.minicdn.router.dto.EdgeNode;
import de.htwsaar.minicdn.router.util.UrlUtil;
import java.net.URI;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HTTP-Adapter zur Ermittlung von Datei-Auslieferungszielen.
 *
 * <p>Diese Klasse kapselt die konkreten HTTP-Pfade für Edge- und Origin-Dateiabrufe.
 * Die Fachlogik kennt dadurch weder Pfadkonstanten noch URL-Zusammenbau.</p>
 */
@Component
public class HttpFileRouteLocationResolver implements FileRouteLocationResolver {

    private static final String EDGE_FILES_PREFIX = "api/edge/files/";
    private static final String ORIGIN_FILES_PREFIX = "api/origin/files/";

    private final URI originBaseUrl;

    /**
     * Erstellt den Resolver mit der Basis-URL des Origin-Service.
     *
     * @param originBaseUrl Basis-URL des Origin-Service, z. B. {@code http://localhost:8080}
     */
    public HttpFileRouteLocationResolver(@Value("${cdn.origin.base-url:http://localhost:8080}") String originBaseUrl) {

        this.originBaseUrl = URI.create(UrlUtil.ensureTrailingSlash(originBaseUrl));
    }

    /**
     * Ermittelt das HTTP-Ziel für eine Datei auf einer konkreten Edge-Instanz.
     *
     * @param node Edge-Knoten
     * @param path relativer Dateipfad
     * @return vollständige Ziel-URI für den Abruf über die Edge
     */
    @Override
    public URI resolveEdgeFileLocation(EdgeNode node, String path) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(node.url(), "node.url must not be null");

        URI edgeBaseUrl = URI.create(UrlUtil.ensureTrailingSlash(node.url()));
        return resolveFileLocation(edgeBaseUrl, EDGE_FILES_PREFIX, path);
    }

    /**
     * Ermittelt das HTTP-Ziel für eine Datei über den Origin-Fallback.
     *
     * @param path relativer Dateipfad
     * @return vollständige Ziel-URI für den Abruf über den Origin
     */
    @Override
    public URI resolveOriginFileLocation(String path) {
        return resolveFileLocation(originBaseUrl, ORIGIN_FILES_PREFIX, path);
    }

    /**
     * Baut eine vollständige Datei-URI aus Basis-URL, Transportpfad und fachlichem Dateipfad.
     *
     * @param baseUrl Basis-URL des Zielsystems
     * @param prefix transportabhängiger Dateipfad-Präfix
     * @param path fachlicher relativer Dateipfad
     * @return aufgelöste Ziel-URI
     */
    private static URI resolveFileLocation(URI baseUrl, String prefix, String path) {
        String cleanPath = UrlUtil.stripLeadingSlash(path == null ? "" : path.trim());
        String relativePath = prefix + cleanPath;
        return baseUrl.resolve(UrlUtil.stripLeadingSlash(relativePath));
    }
}
