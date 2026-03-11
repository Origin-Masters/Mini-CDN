package de.htwsaar.minicdn.cli.service.system;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Default-Adapter zum Starten von Services über {@code java -jar}.
 */
public final class JavaJarServiceLauncher implements ServiceLauncher {

    private final String adminToken;
    private final URI routerBaseUrl;

    public JavaJarServiceLauncher() {
        this(null, null);
    }

    public JavaJarServiceLauncher(String adminToken, URI routerBaseUrl) {
        this.adminToken = adminToken;
        this.routerBaseUrl = routerBaseUrl;
    }

    @Override
    public Process start(Path jarPath, String springProfile, Path logPath) {
        Objects.requireNonNull(jarPath, "jarPath");
        Objects.requireNonNull(springProfile, "springProfile");
        Objects.requireNonNull(logPath, "logPath");

        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();

        ProcessBuilder builder = new ProcessBuilder(
                javaBin, "-jar", jarPath.toAbsolutePath().toString(), "--spring.profiles.active=" + springProfile);

        // Run each service from its module directory, not from the CLI process cwd.
        builder.directory(resolveModuleDir(jarPath).toFile());

        if (hasText(adminToken)) {
            builder.environment().put("MINICDN_ADMIN_TOKEN", adminToken);
        }
        if (routerBaseUrl != null) {
            builder.environment().put("MINICDN_ROUTER_URL", routerBaseUrl.toString());
        }

        builder.redirectErrorStream(true);
        builder.redirectOutput(logPath.toFile());

        try {
            return builder.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Service konnte nicht gestartet werden: " + jarPath, e);
        }
    }

    private Path resolveModuleDir(Path jarPath) {
        Path absJar = jarPath.toAbsolutePath().normalize();
        Path targetDir = absJar.getParent();
        if (targetDir == null || targetDir.getParent() == null) {
            throw new IllegalArgumentException("Ungültiger JAR-Pfad: " + jarPath);
        }
        return targetDir.getParent();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
