package de.htwsaar.minicdn.edge.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.htwsaar.minicdn.common.util.Sha256Util;
import de.htwsaar.minicdn.edge.cache.ReplacementStrategy;
import de.htwsaar.minicdn.edge.config.EdgeConfigService;
import de.htwsaar.minicdn.edge.config.EdgeRuntimeConfig;
import de.htwsaar.minicdn.edge.config.TtlPolicyService;
import de.htwsaar.minicdn.edge.domain.CacheDecision;
import de.htwsaar.minicdn.edge.domain.FilePayload;
import de.htwsaar.minicdn.edge.domain.OriginClient;
import de.htwsaar.minicdn.edge.domain.OriginContent;
import de.htwsaar.minicdn.edge.domain.OriginMetadata;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class EdgeFileServiceTest {

    @Test
    void shouldWorkAgainstPortAndNotAgainstHttpImplementation() {
        byte[] body = "hello edge".getBytes(StandardCharsets.UTF_8);
        String sha256 = Sha256Util.sha256Hex(body);

        FakeOriginClient fakeOrigin = new FakeOriginClient(
                new OriginContent(body, "text/plain", sha256), new OriginMetadata("text/plain", sha256));

        EdgeConfigService configService =
                new EdgeConfigService(new EdgeRuntimeConfig("eu-west", 60_000, 100, ReplacementStrategy.LRU));

        EdgeFileService service = new EdgeFileService(
                fakeOrigin,
                configService,
                new TtlPolicyService(),
                new FixedClock(Instant.parse("2026-01-01T00:00:00Z")));

        FilePayload first = service.getFile("/docs/readme.txt");
        FilePayload second = service.getFile("docs/readme.txt");

        assertEquals(CacheDecision.MISS, first.cache());
        assertEquals(CacheDecision.HIT, second.cache());
        assertEquals(1, fakeOrigin.fetchCalls);
        assertArrayEquals(body, first.body());
        assertArrayEquals(body, second.body());
        assertEquals("text/plain", first.contentType());
        assertEquals(sha256, first.sha256());
    }

    private static final class FakeOriginClient implements OriginClient {
        private final OriginContent content;
        private final OriginMetadata metadata;
        private int fetchCalls;

        private FakeOriginClient(OriginContent content, OriginMetadata metadata) {
            this.content = content;
            this.metadata = metadata;
        }

        @Override
        public OriginContent fetchFile(String path) {
            fetchCalls++;
            return content;
        }

        @Override
        public OriginMetadata fetchMetadata(String path) {
            return metadata;
        }
    }

    private static final class FixedClock extends Clock {
        private final Instant now;

        private FixedClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
