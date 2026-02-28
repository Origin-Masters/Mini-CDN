package de.htwsaar.minicdn.origin.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.htwsaar.minicdn.common.util.Sha256Util;
import de.htwsaar.minicdn.origin.domain.OriginFileContent;
import de.htwsaar.minicdn.origin.domain.OriginFileMeta;
import de.htwsaar.minicdn.origin.domain.OriginFileMetadata;
import de.htwsaar.minicdn.origin.domain.OriginStorage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OriginFileServiceTest {

    @Test
    void shouldUseStoragePortWithoutKnowingFilesystemDetails() {
        byte[] body = "hello origin".getBytes(StandardCharsets.UTF_8);
        OriginStorage storage = new FakeOriginStorage(body, "text/plain");

        OriginFileService service = new OriginFileService(storage);

        Optional<OriginFileContent> content = service.get("docs/readme.txt");
        Optional<OriginFileMetadata> metadata = service.getMetadata("docs/readme.txt");

        assertTrue(content.isPresent());
        assertTrue(metadata.isPresent());
        assertArrayEquals(body, content.get().body());
        assertEquals("text/plain", content.get().contentType());
        assertEquals(Sha256Util.sha256Hex(body), content.get().sha256Hex());
        assertEquals(body.length, metadata.get().lengthBytes());
        assertEquals("text/plain", metadata.get().contentType());
        assertEquals(Sha256Util.sha256Hex(body), metadata.get().sha256Hex());
    }

    private static final class FakeOriginStorage implements OriginStorage {
        private final byte[] body;
        private final String contentType;

        private FakeOriginStorage(byte[] body, String contentType) {
            this.body = body;
            this.contentType = contentType;
        }

        @Override
        public Optional<byte[]> read(String path) {
            return Optional.of(body);
        }

        @Override
        public Optional<OriginFileMeta> meta(String path) {
            return Optional.of(new OriginFileMeta(path, body.length, "2026-01-01T00:00:00Z", contentType));
        }

        @Override
        public List<OriginFileMeta> listAll() {
            return List.of(new OriginFileMeta("docs/readme.txt", body.length, "2026-01-01T00:00:00Z", contentType));
        }

        @Override
        public boolean write(String path, byte[] body) {
            return true;
        }

        @Override
        public boolean delete(String path) {
            return true;
        }
    }
}
