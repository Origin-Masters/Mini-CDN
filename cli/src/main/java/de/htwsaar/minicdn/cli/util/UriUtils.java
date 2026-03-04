package de.htwsaar.minicdn.cli.util;

import java.net.URI;
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
}
