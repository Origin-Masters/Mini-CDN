package de.htwsaar.minicdn.router.util;

/**
 * Kleine Hilfsfunktionen zur sicheren Verarbeitung von Base-URLs und Pfaden.
 * <p>
 * Motivation: URLs niemals per String-Konkatenation zusammensetzen, sondern normalisieren
 * und anschließend via {@link java.net.URI#resolve(String)} kombinieren.
 * </p>
 */
public final class UrlUtil {

    private UrlUtil() {
        // Utility-Klasse, keine Instanzen.
    }

    /**
     * Stellt sicher, dass eine Base-URL mit einem Slash endet.
     *
     * @param baseUrl Base-URL, z. B. {@code http://localhost:8081}
     * @return normalisierte Base-URL, z. B. {@code http://localhost:8081/}; bei {@code null} ebenfalls {@code null}
     */
    public static String ensureTrailingSlash(String baseUrl) {
        if (baseUrl == null) return null;
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    /**
     * Entfernt führenden Slashes aus einem Pfad, damit {@link java.net.URI#resolve(String)} stabil funktioniert.
     *
     * @param path Pfad, z. B. {@code /api/edge/health}
     * @return Pfad ohne führenden Slash, z. B. {@code api/edge/health}; bei {@code null} ebenfalls {@code null}
     */
    public static String stripLeadingSlash(String path) {
        if (path == null) return null;
        String p = path;
        while (p.startsWith("/")) p = p.substring(1);
        return p;
    }
}
