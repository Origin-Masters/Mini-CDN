package de.htwsaar.minicdn.origin.adapter;

import de.htwsaar.minicdn.origin.domain.OriginFileMeta;
import de.htwsaar.minicdn.origin.domain.OriginStorage;
import de.htwsaar.minicdn.origin.domain.OriginStorageException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

/**
 * Filesystem-Adapter für den OriginStorage-Port.
 *
 * <p>Enthält bewusst alle NIO/Filesystem-Details.</p>
 */
@Repository
public class FileSystemOriginStorage implements OriginStorage {

    private final Path baseDir;

    public FileSystemOriginStorage(@Value("${origin.data-dir:data}") String baseDir) {
        this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    }

    @Override
    public Optional<byte[]> read(String path) {
        Path file = resolveSafe(path);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new OriginStorageException("Failed to read origin file: " + path, e);
        }
    }

    @Override
    public Optional<OriginFileMeta> meta(String path) {
        Path file = resolveSafe(path);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        try {
            String rel = baseDir.relativize(file).toString().replace("\\", "/");
            long size = Files.size(file);
            String lm = Files.getLastModifiedTime(file).toInstant().toString();
            String ct = Files.probeContentType(file);
            if (ct == null || ct.isBlank()) {
                ct = "application/octet-stream";
            }
            return Optional.of(new OriginFileMeta(rel, size, lm, ct));
        } catch (IOException e) {
            throw new OriginStorageException("Failed to read origin metadata: " + path, e);
        }
    }

    @Override
    public List<OriginFileMeta> listAll() {
        if (!Files.exists(baseDir)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.walk(baseDir)) {
            return stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(
                            path -> baseDir.relativize(path).toString()))
                    .map(path -> baseDir.relativize(path).toString().replace("\\", "/"))
                    .map(this::meta)
                    .flatMap(Optional::stream)
                    .toList();
        } catch (IOException e) {
            throw new OriginStorageException("Failed to list origin files", e);
        }
    }

    @Override
    public boolean write(String path, byte[] body) {
        Path file = resolveSafe(path);
        boolean created = !Files.exists(file);

        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(file, body);
            return created;
        } catch (IOException e) {
            throw new OriginStorageException("Failed to write origin file: " + path, e);
        }
    }

    @Override
    public boolean delete(String path) {
        Path file = resolveSafe(path);
        if (!Files.exists(file)) {
            return false;
        }

        try {
            Files.delete(file);
            return true;
        } catch (IOException e) {
            throw new OriginStorageException("Failed to delete origin file: " + path, e);
        }
    }

    private Path resolveSafe(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }

        Path resolved = baseDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("path traversal is not allowed: " + relativePath);
        }

        return resolved;
    }
}
