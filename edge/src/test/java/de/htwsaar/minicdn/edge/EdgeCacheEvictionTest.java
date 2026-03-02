package de.htwsaar.minicdn.edge;

import static org.junit.jupiter.api.Assertions.*;

import de.htwsaar.minicdn.edge.cache.CachedFile;
import de.htwsaar.minicdn.edge.cache.LfuCacheStore;
import de.htwsaar.minicdn.edge.cache.LruCacheStore;
import org.junit.jupiter.api.Test;

class EdgeCacheEvictionTest {
    @Test
    void lru_shouldEvictLeastRecentlyUsed_onOverflow() {
        LruCacheStore cache = new LruCacheStore();
        long now = System.currentTimeMillis();
        long future = now + 60_000;

        cache.put("a", file("a", future), 2, now);
        cache.put("b", file("b", future), 2, now);
        cache.getFresh("a", now);

        cache.put("c", file("c", future), 2, now);

        assertNotNull(cache.getFresh("a", now), "a muss noch im Cache sein");
        assertNull(cache.getFresh("b", now), "b muss evicted worden sein");
        assertNotNull(cache.getFresh("c", now), "c muss im Cache sein");
    }

    @Test
    void lfu_shouldEvictLeastFrequentlyUsed_onOverflow() {
        LfuCacheStore cache = new LfuCacheStore();
        long now = System.currentTimeMillis();
        long future = now + 60_000;

        cache.put("a", file("a", future), 2, now);
        cache.put("b", file("b", future), 2, now);

        cache.getFresh("a", now);
        cache.getFresh("a", now);

        cache.put("c", file("c", future), 2, now);

        assertNotNull(cache.getFresh("a", now), "a muss noch im Cache sein");
        assertNull(cache.getFresh("b", now), "b muss evicted worden sein");
        assertNotNull(cache.getFresh("c", now), "c muss im Cache sein");
    }

    @Test
    void expiredEntry_shouldBeRemovedAndReturnNull_onNextAccess() {
        LruCacheStore cache = new LruCacheStore();
        long now = 1_000_000L;

        cache.put("x", file("x", now + 1_000), 10, now);

        assertNotNull(cache.getFresh("x", now), "vor Ablauf: Eintrag vorhanden");
        assertNull(cache.getFresh("x", now + 2_000), "nach Ablauf: Eintrag muss null sein");
        assertEquals(0, cache.size(), "Cache muss leer sein nach Ablauf");
    }

    @Test
    void invalidateFile_shouldRemoveSingleEntry() {
        LruCacheStore cache = new LruCacheStore();
        long future = System.currentTimeMillis() + 60_000;
        cache.put("img/logo.png", file("logo", future), 10, System.currentTimeMillis());

        assertTrue(cache.remove("img/logo.png"));
        assertNull(cache.getFresh("img/logo.png", System.currentTimeMillis()));
    }

    @Test
    void invalidatePrefix_shouldRemoveAllMatchingEntries() {
        LruCacheStore cache = new LruCacheStore();
        long now = System.currentTimeMillis();
        long future = now + 60_000;

        cache.put("videos/a.mp4", file("a", future), 10, now);
        cache.put("videos/b.mp4", file("b", future), 10, now);
        cache.put("images/c.png", file("c", future), 10, now);

        int removed = cache.removeByPrefix("videos/");

        assertEquals(2, removed, "2 Videos müssen invalidiert sein");
        assertEquals(1, cache.size(), "images/c.png muss übrig bleiben");
    }

    private static CachedFile file(String tag, long expiresAtMs) {
        return new CachedFile(tag.getBytes(), "text/plain", "sha-" + tag, expiresAtMs);
    }
}
