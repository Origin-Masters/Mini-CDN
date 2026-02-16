package de.htwsaar.minicdn.cli.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;

/**
 * URI helpers for CLI input validation and normalization.
 */
public final class UriUtils {
    private UriUtils() {}

    public static URI ensureTrailingSlash(URI uri) {
        Objects.requireNonNull(uri, "uri");
        String s = uri.toString();
        return URI.create(s.endsWith("/") ? s : s + "/");
    }

    public static Optional<URI> parseHttpUri(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim();
        try {
            URI u = new URI(trimmed);
            String scheme = u.getScheme();
            if (scheme == null) return Optional.empty();
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) return Optional.empty();
            return Optional.of(u);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}