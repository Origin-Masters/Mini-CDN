package de.htwsaar.minicdn.cli.util;

/**
 * Small path helpers used by multiple CLI commands.
 */
public final class PathUtils {
    private PathUtils() {}

    public static String normalizePath(String raw) {
        String clean = (raw == null) ? "" : raw.trim();
        if (clean.startsWith("/")) clean = clean.substring(1);
        return clean.replaceFirst("^(origin/)?(data/)?", "");
    }

    public static String stripLeadingSlash(String p) {
        if (p == null || p.isBlank()) return "";
        return p.startsWith("/") ? p.substring(1) : p;
    }

    public static boolean isBlankPath(String p) {
        return normalizePath(p).isBlank();
    }
}
