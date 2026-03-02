package de.htwsaar.minicdn.edge.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.htwsaar.minicdn.common.util.Sha256Util;
import de.htwsaar.minicdn.edge.cache.EdgeCacheStateStore;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class EdgeFileServiceTest {

    @Test
    void shouldWorkAgainstPortAndNotAgainstHttpImplementation() throws Exception {
        byte[] body = "hello edge".getBytes(StandardCharsets.UTF_8);
        String sha256 = Sha256Util.sha256Hex(body);

        FakeOriginClient fakeOrigin = new FakeOriginClient(
                new OriginContent(body, "text/plain", sha256), new OriginMetadata("text/plain", sha256));

        EdgeConfigService configService =
                new EdgeConfigService(new EdgeRuntimeConfig("eu-west", 60_000, 100, ReplacementStrategy.LRU));

        EdgeCacheStateStore stateStore = new EdgeCacheStateStore(
                Files.createTempFile("edge-cache-state-test", ".properties").toString());

        EdgeFileService service = new EdgeFileService(
                fakeOrigin,
                configService,
                new TtlPolicyService(),
                stateStore,
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

    @Test
    void shouldRestoreCachedEntryAfterRestart() throws Exception {

        // adding extra test to verify that the cache recovery mechanism works correctly
        // not only test persistence but also that the restored cache

        // cache body in bytes
        byte[] body = "cache recovery payload".getBytes(StandardCharsets.UTF_8);
        // hash over body
        String sha256 = Sha256Util.sha256Hex(body);
        // fixed point in time
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        // create a temporary file for the state store to persist the cache state
        Path stateFile = Files.createTempFile("edge-cache-recovery", ".properties");

        // create the state store with the temporary file path
        EdgeCacheStateStore stateStore = new EdgeCacheStateStore(stateFile.toString());

        // this file survives both service instances
        // and persists the cache state across restart

        EdgeConfigService configService =
                new EdgeConfigService(new EdgeRuntimeConfig("eu-west", 60_000, 100, ReplacementStrategy.LRU));

        FakeOriginClient firstOrigin = new FakeOriginClient(
                new OriginContent(body, "text/plain", sha256), new OriginMetadata("text/plain", sha256));
        EdgeFileService firstService = new EdgeFileService(
                firstOrigin, configService, new TtlPolicyService(), stateStore, new FixedClock(now));

        FilePayload initial = firstService.getFile("docs/recovery.txt");
        assertEquals(CacheDecision.MISS, initial.cache());
        assertEquals(1, firstOrigin.fetchCalls);

        FakeOriginClient restartedOrigin = new FakeOriginClient(
                new OriginContent(body, "text/plain", sha256), new OriginMetadata("text/plain", sha256));
        EdgeFileService restartedService = new EdgeFileService(
                restartedOrigin, configService, new TtlPolicyService(), stateStore, new FixedClock(now));

        restartedService.restoreCacheFromDisk();
        FilePayload recovered = restartedService.getFile("docs/recovery.txt");

        assertEquals(CacheDecision.HIT, recovered.cache());
        assertArrayEquals(body, recovered.body());
        assertEquals(0, restartedOrigin.fetchCalls);
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
