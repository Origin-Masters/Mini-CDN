package de.htwsaar.minicdn.router.adapter;

import de.htwsaar.minicdn.router.util.UrlUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Persistiert den Origin-Cluster-Zustand (aktiver Knoten + Hot-Spares) in einer Properties-Datei.
 */
@Component
public class RouterOriginClusterStateStore {

    private static final String KEY_ACTIVE = "origin.active";
    private static final String KEY_SPARES = "origin.spares";

    private final Path stateFile;

    public RouterOriginClusterStateStore(
            @Value("${cdn.origin.cluster.state-file:data/origin-cluster-state.properties}") String stateFile) {
        this.stateFile = Path.of(stateFile);
    }

    public synchronized void save(String activeOrigin, List<String> spareOrigins) {
        Properties props = new Properties();
        if (activeOrigin != null && !activeOrigin.isBlank()) {
            props.setProperty(KEY_ACTIVE, UrlUtil.ensureTrailingSlash(activeOrigin));
        }
        if (spareOrigins != null && !spareOrigins.isEmpty()) {
            props.setProperty(KEY_SPARES, String.join(",", spareOrigins));
        }

        try {
            Path parent = stateFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                props.store(out, "router origin cluster state");
            }
            Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist origin cluster state", ex);
        }
    }

    public synchronized OriginClusterState load() {
        if (!Files.exists(stateFile)) {
            return new OriginClusterState(null, List.of());
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(stateFile)) {
            props.load(in);
        } catch (IOException ex) {
            return new OriginClusterState(null, List.of());
        }

        String active = normalizeUrl(props.getProperty(KEY_ACTIVE));
        List<String> spares = parseUrls(props.getProperty(KEY_SPARES));
        return new OriginClusterState(active, spares);
    }

    private static List<String> parseUrls(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (String part : value.split(",")) {
            String url = normalizeUrl(part);
            if (url != null && !urls.contains(url)) {
                urls.add(url);
            }
        }
        return List.copyOf(urls);
    }

    private static String normalizeUrl(String value) {
        if (value == null) {
            return null;
        }
        String clean = value.trim();
        if (clean.isBlank()) {
            return null;
        }
        return UrlUtil.ensureTrailingSlash(clean);
    }

    public record OriginClusterState(String activeOrigin, List<String> spareOrigins) {}
}
