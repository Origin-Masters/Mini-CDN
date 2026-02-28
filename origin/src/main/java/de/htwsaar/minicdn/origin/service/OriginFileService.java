package de.htwsaar.minicdn.origin.service;

import de.htwsaar.minicdn.common.util.Sha256Util;
import de.htwsaar.minicdn.origin.domain.OriginFileContent;
import de.htwsaar.minicdn.origin.domain.OriginFileList;
import de.htwsaar.minicdn.origin.domain.OriginFileMeta;
import de.htwsaar.minicdn.origin.domain.OriginFileMetadata;
import de.htwsaar.minicdn.origin.domain.OriginFiles;
import de.htwsaar.minicdn.origin.domain.OriginPutResult;
import de.htwsaar.minicdn.origin.domain.OriginStorage;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Fachliche Implementierung der Origin-Use-Cases.
 *
 * <p>Kein Spring-Web, keine ResponseEntity, keine Headernamen – nur Domänenobjekte.</p>
 */
@Service
public class OriginFileService implements OriginFiles {

    private final OriginStorage storage;

    public OriginFileService(OriginStorage storage) {
        this.storage = storage;
    }

    @Override
    public OriginFileList list(int page, int size) {
        if (page < 1 || size <= 0) {
            throw new IllegalArgumentException("page must be >= 1 and size must be > 0");
        }

        List<OriginFileMeta> all = storage.listAll();
        int total = all.size();
        int from = Math.min((page - 1) * size, total);
        int to = Math.min(from + size, total);

        return new OriginFileList(page, size, total, all.subList(from, to));
    }

    @Override
    public Optional<OriginFileContent> get(String path) {
        Optional<byte[]> bodyOpt = storage.read(path);
        Optional<OriginFileMeta> metaOpt = storage.meta(path);

        if (bodyOpt.isEmpty() || metaOpt.isEmpty()) {
            return Optional.empty();
        }

        byte[] body = bodyOpt.get();
        OriginFileMeta meta = metaOpt.get();
        String sha = Sha256Util.sha256Hex(body);

        return Optional.of(new OriginFileContent(body, meta.contentType(), sha));
    }

    @Override
    public Optional<OriginFileMetadata> getMetadata(String path) {
        return get(path)
                .map(content ->
                        new OriginFileMetadata(content.contentType(), content.body().length, content.sha256Hex()));
    }

    @Override
    public OriginPutResult put(String path, byte[] body) {
        boolean created = storage.write(path, body);
        return new OriginPutResult(created);
    }

    @Override
    public boolean delete(String path) {
        return storage.delete(path);
    }
}
