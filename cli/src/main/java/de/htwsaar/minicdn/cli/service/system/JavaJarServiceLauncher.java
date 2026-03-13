package de.htwsaar.minicdn.cli.service.system;

import de.htwsaar.minicdn.common.util.UriUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Technischer Adapter zum Starten lokaler Dienste über {@code java -jar}.
 *
 * <p>Die Klasse kapselt ausschließlich den Prozessstart und die dafür nötige
 * Environment-Konfiguration. Fachliche Entscheidungen, welche Dienste wann
 * gestartet werden, liegen außerhalb dieses Adapters.</p>
 */
public final class JavaJarServiceLauncher implements ServiceLauncher {

    private final String adminToken;
    private final URI routerBaseUrl;

    /**
     * Erzeugt einen Launcher mit optionaler Environment-Konfiguration.
     *
     * @param adminToken optionales Admin-Token für gestartete Prozesse
     * @param routerBaseUrl optionale Router-Basis-URL für gestartete Prozesse
     */
    public JavaJarServiceLauncher(String adminToken, URI routerBaseUrl) {
        this.adminToken = normalizeOptionalText(adminToken);
        this.routerBaseUrl = routerBaseUrl == null ? null : UriUtils.ensureTrailingSlash(routerBaseUrl);
    }

    /**
     * Startet einen lokalen Dienstprozess über {@code java -jar}.
     *
     * @param jarPath Pfad zur ausführbaren JAR
     * @param springProfile aktives Spring-Profil
     * @param logPath Zieldatei für kombinierte stdout/stderr-Ausgabe
     * @return gestarteter Prozess
     * @throws IllegalArgumentException falls Eingaben ungültig sind
     * @throws UncheckedIOException falls der Prozess nicht gestartet werden kann
     */
    @Override
    public Process start(Path jarPath, String springProfile, Path logPath) {
        Path executableJar =
                Objects.requireNonNull(jarPath, "jarPath").toAbsolutePath().normalize();
        String activeProfile = requireText(springProfile, "springProfile");
        Path targetLog =
                Objects.requireNonNull(logPath, "logPath").toAbsolutePath().normalize();

        ensureLogParentExists(targetLog);

        ProcessBuilder builder = new ProcessBuilder(buildCommand(executableJar, activeProfile));
        builder.directory(resolveModuleDir(executableJar).toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(targetLog.toFile());

        applyEnvironment(builder);

        try {
            return builder.start();
        } catch (IOException ex) {
            throw new UncheckedIOException("Service konnte nicht gestartet werden: " + executableJar, ex);
        }
    }

    /**
     * Baut das Kommando für den Java-Prozess.
     *
     * @param executableJar normalisierter Pfad zur JAR
     * @param activeProfile aktives Spring-Profil
     * @return vollständige Kommandozeile
     */
    private static List<String> buildCommand(Path executableJar, String activeProfile) {
        return List.of(javaBinary(), "-jar", executableJar.toString(), "--spring.profiles.active=" + activeProfile);
    }

    /**
     * Liefert den Pfad zur aktuell verwendeten Java-Binary.
     *
     * @return absoluter Pfad zur Java-Binary
     */
    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    /**
     * Überträgt optionale Umgebungsvariablen auf den Kindprozess.
     *
     * @param builder ProcessBuilder des Kindprozesses
     */
    private void applyEnvironment(ProcessBuilder builder) {
        if (hasText(adminToken)) {
            builder.environment().put("MINICDN_ADMIN_TOKEN", adminToken);
        }
        if (routerBaseUrl != null) {
            builder.environment().put("MINICDN_ROUTER_URL", routerBaseUrl.toString());
        }
    }

    /**
     * Ermittelt das Modulverzeichnis auf Basis des JAR-Pfads.
     *
     * <p>Erwartet einen Pfad der Form {@code <module>/target/<jar>}.</p>
     *
     * @param jarPath normalisierter JAR-Pfad
     * @return Modulverzeichnis als Working Directory
     * @throws IllegalArgumentException falls der Pfad nicht dem erwarteten Layout entspricht
     */
    private static Path resolveModuleDir(Path jarPath) {
        Path targetDir = jarPath.getParent();
        if (targetDir == null) {
            throw new IllegalArgumentException("Ungültiger JAR-Pfad ohne target-Verzeichnis: " + jarPath);
        }

        Path moduleDir = targetDir.getParent();
        if (moduleDir == null) {
            throw new IllegalArgumentException("Ungültiger JAR-Pfad ohne Modulverzeichnis: " + jarPath);
        }

        if (!"target".equals(targetDir.getFileName().toString())) {
            throw new IllegalArgumentException("JAR-Pfad muss in einem target-Verzeichnis liegen: " + jarPath);
        }

        return moduleDir;
    }

    /**
     * Stellt sicher, dass das Elternverzeichnis der Logdatei existiert.
     *
     * @param logPath absolute Zieldatei für Logs
     * @throws UncheckedIOException falls das Verzeichnis nicht angelegt werden kann
     */
    private static void ensureLogParentExists(Path logPath) {
        Path parent = logPath.getParent();
        if (parent == null) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException ex) {
            throw new UncheckedIOException("Log-Verzeichnis konnte nicht angelegt werden: " + parent, ex);
        }
    }

    /**
     * Validiert einen Pflichttext und liefert die getrimmte Form zurück.
     *
     * @param value Eingabewert
     * @param fieldName Feldname für Fehlermeldungen
     * @return getrimmter Pflichttext
     */
    private static String requireText(String value, String fieldName) {
        String trimmed = Objects.toString(value, "").trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    /**
     * Normalisiert einen optionalen Text.
     *
     * @param value Eingabewert
     * @return getrimmter Wert oder {@code null}, wenn leer
     */
    private static String normalizeOptionalText(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    /**
     * Prüft, ob ein Text gesetzt ist.
     *
     * @param value zu prüfender Wert
     * @return {@code true}, wenn der Wert nicht leer ist
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
