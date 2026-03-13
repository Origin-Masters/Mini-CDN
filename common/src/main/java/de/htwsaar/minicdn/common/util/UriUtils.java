package de.htwsaar.minicdn.common.util;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Allgemeine Hilfsmethoden fuer URI-bezogene Operationen.
 */
public final class UriUtils {

    private UriUtils() {}

    /**
     * Stellt sicher, dass die URI mit einem Slash endet.
     *
     * <p>Beispiel:
     * {@code http://localhost:8080/api} wird zu
     * {@code http://localhost:8080/api/}.</p>
     *
     * @param uri die Eingabe-URI
     * @return eine URI mit abschliessendem Slash
     * @throws NullPointerException wenn {@code uri} null ist
     */
    public static URI ensureTrailingSlash(URI uri) {
        Objects.requireNonNull(uri, "uri");

        String value = uri.toString();
        return value.endsWith("/") ? uri : URI.create(value + "/");
    }

    /**
     * Kodiert einen String fuer die Verwendung in URL-Parametern.
     *
     * @param value der zu kodierende Wert
     * @return der URL-kodierte Wert
     * @throws NullPointerException wenn {@code value} null ist
     */
    public static String urlEncode(String value) {
        Objects.requireNonNull(value, "value");
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
