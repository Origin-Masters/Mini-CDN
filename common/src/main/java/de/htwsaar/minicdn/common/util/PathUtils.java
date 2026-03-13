package de.htwsaar.minicdn.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Allgemeine Hilfsmethoden zur sicheren Normalisierung von Remote- und HTTP-Pfaden.
 *
 * <p>Die Klasse definiert eine gemeinsame Regel für Pfadangaben aus CLI-,
 * Router-, Edge- oder Origin-Kontext. Ziel ist eine konsistente Behandlung
 * von Slashes, Backslashes, optionalen Präfixen und unsicheren Segmenten.</p>
 */
public final class PathUtils {

    private static final Pattern MULTI_SLASH_PATTERN = Pattern.compile("/+");

    private PathUtils() {}

    /**
     * Normalisiert einen relativen Pfad.
     *
     * <p>Der zurückgegebene Pfad enthält keinen führenden Slash, keine
     * doppelten Slashes, keine Backslashes und keine unsicheren Segmente
     * wie {@code .} oder {@code ..}.</p>
     *
     * @param rawPath der vom Aufrufer gelieferte Pfad
     * @return der normalisierte relative Pfad, z. B. {@code docs/manual.pdf}
     * @throws IllegalArgumentException wenn der Pfad leer oder unsicher ist
     */
    public static String normalizeRelativePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("Pfad darf nicht leer sein.");
        }

        String normalized = rawPath.trim().replace('\\', '/');
        normalized = MULTI_SLASH_PATTERN.matcher(normalized).replaceAll("/");

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Pfad darf nicht leer sein.");
        }

        String[] rawSegments = normalized.split("/");
        List<String> safeSegments = new ArrayList<>(rawSegments.length);

        for (String segment : rawSegments) {
            if (segment.isBlank()) {
                continue;
            }
            if (".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Pfad enthält unsichere Segmente.");
            }
            safeSegments.add(segment);
        }

        if (safeSegments.isEmpty()) {
            throw new IllegalArgumentException("Pfad darf nicht leer sein.");
        }

        return String.join("/", safeSegments);
    }

    /**
     * Normalisiert einen absoluten HTTP-Pfad.
     *
     * @param rawPath der zu normalisierende Pfad
     * @return der normalisierte absolute Pfad, z. B. {@code /docs/manual.pdf}
     * @throws IllegalArgumentException wenn der Pfad leer oder unsicher ist
     */
    public static String normalizeAbsolutePath(String rawPath) {
        return "/" + normalizeRelativePath(rawPath);
    }

    /**
     * Entfernt bekannte führende Präfixe aus einem relativen Pfad.
     *
     * <p>Beispiele:
     * <ul>
     *   <li>{@code stripLeadingPrefixes("origin/data/file.txt", "origin", "data")} ergibt {@code file.txt}</li>
     *   <li>{@code stripLeadingPrefixes("data/file.txt", "origin", "data")} ergibt {@code file.txt}</li>
     * </ul>
     * </p>
     *
     * <p>Die Eingabe wird zuerst normalisiert. Anschließend werden die
     * angegebenen Präfixe nur am Anfang des Pfads entfernt, in der
     * übergebenen Reihenfolge und solange sie passen.</p>
     *
     * @param rawPath der Eingabepfad
     * @param prefixes optionale führende Präfixe ohne Slash, z. B. {@code origin}, {@code data}
     * @return der normalisierte Pfad ohne passende führende Präfixe
     * @throws IllegalArgumentException wenn der Pfad leer oder unsicher ist
     */
    public static String stripLeadingPrefixes(String rawPath, String... prefixes) {
        String normalized = normalizeRelativePath(rawPath);

        if (prefixes == null || prefixes.length == 0) {
            return normalized;
        }

        String[] segments = normalized.split("/");
        int index = 0;

        for (String prefix : prefixes) {
            if (prefix == null || prefix.isBlank()) {
                continue;
            }
            if (index < segments.length && segments[index].equals(prefix.trim())) {
                index++;
            } else {
                break;
            }
        }

        if (index >= segments.length) {
            throw new IllegalArgumentException("Pfad darf nach dem Entfernen von Praefixen nicht leer sein.");
        }

        return String.join("/", java.util.Arrays.copyOfRange(segments, index, segments.length));
    }

    /**
     * Entfernt genau einen führenden Slash, falls vorhanden.
     *
     * <p>Diese Methode ist bewusst tolerant und liefert bei {@code null} oder
     * leerer Eingabe einen leeren String zurück. Sie ist für einfache
     * Darstellungs- oder Legacy-Fälle gedacht, nicht für Sicherheitsprüfungen.</p>
     *
     * @param path ein beliebiger Pfad
     * @return der Pfad ohne führenden Slash
     */
    public static String stripLeadingSlash(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }

        String trimmed = path.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    /**
     * Prüft, ob ein relativer Pfad gemäß der gemeinsamen Regeln sicher und gültig ist.
     *
     * @param rawPath der zu prüfende Pfad
     * @return {@code true}, wenn der Pfad gültig ist, sonst {@code false}
     */
    public static boolean isSafeRelativePath(String rawPath) {
        try {
            normalizeRelativePath(rawPath);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Prüft, ob ein Pfad nach den gemeinsamen Regeln unsicher oder ungültig ist.
     *
     * @param rawPath der zu prüfende Pfad
     * @return {@code true}, wenn der Pfad unsicher oder ungültig ist, sonst {@code false}
     */
    public static boolean isUnsafePath(String rawPath) {
        return !isSafeRelativePath(rawPath);
    }
}
