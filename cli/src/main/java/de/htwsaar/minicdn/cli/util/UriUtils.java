package de.htwsaar.minicdn.cli.util;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Hilfsfunktionen zur Normalisierung und Validierung von URIs, z. B. Sicherstellen eines abschließenden Slash oder Parsen von HTTP-URIs aus Strings.
 */
public final class UriUtils {
    private UriUtils() {}

    public static URI ensureTrailingSlash(URI uri) {
        Objects.requireNonNull(uri, "uri");
        String s = uri.toString();
        return URI.create(s.endsWith("/") ? s : s + "/");
    }

    public static String urlEncode(String value) {
        Objects.requireNonNull(value, "value");
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
