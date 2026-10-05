package com.dns.cache;

import com.dns.protocol.DnsRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DNS Cache and TTL Tests")
class DnsCacheTest {

    private DnsCache cache;

    @BeforeEach
    void setUp() {
        cache = new DnsCache(false); // disable background thread during deterministic unit tests
    }

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.shutdown();
        }
    }

    @Test
    @DisplayName("Should insert and retrieve record from cache successfully (Cache Hit)")
    void testCacheInsertionAndHit() {
        cache.put("example.com", DnsRecord.TYPE_A, "93.184.216.34", 300);

        assertEquals(1, cache.getActiveEntryCount());
        assertEquals(0, cache.getHits());
        assertEquals(0, cache.getMisses());

        List<CacheEntry> results = cache.get("example.com", DnsRecord.TYPE_A);
        assertFalse(results.isEmpty());
        assertEquals(1, results.size());
        assertEquals("93.184.216.34", results.get(0).getValue());
        assertEquals(1, cache.getHits());
        assertEquals(0, cache.getMisses());
        assertEquals(100.0, cache.getHitRatio(), 0.01);
    }

    @Test
    @DisplayName("Should record cache miss on non-existent domain or type")
    void testCacheMiss() {
        List<CacheEntry> results = cache.get("unknown-domain.org", DnsRecord.TYPE_A);
        assertTrue(results.isEmpty());
        assertEquals(0, cache.getHits());
        assertEquals(1, cache.getMisses());
        assertEquals(0.0, cache.getHitRatio(), 0.01);
    }

    @Test
    @DisplayName("Should calculate hit ratio accurately across multiple queries")
    void testHitRatioCalculation() {
        cache.put("google.com", DnsRecord.TYPE_A, "142.250.190.46", 300);

        // 3 hits
        cache.get("google.com", DnsRecord.TYPE_A);
        cache.get("google.com", DnsRecord.TYPE_A);
        cache.get("google.com", DnsRecord.TYPE_A);

        // 1 miss
        cache.get("missing.com", DnsRecord.TYPE_A);

        assertEquals(3, cache.getHits());
        assertEquals(1, cache.getMisses());
        assertEquals(75.0, cache.getHitRatio(), 0.01);
    }

    @Test
    @DisplayName("Should expire cache entries after TTL elapsed")
    void testTtlExpiration() throws InterruptedException {
        // Insert entry with 1 second TTL
        cache.put("fast-expire.com", DnsRecord.TYPE_A, "10.0.0.1", 1);

        // Immediately should be valid
        List<CacheEntry> immediate = cache.get("fast-expire.com", DnsRecord.TYPE_A);
        assertEquals(1, immediate.size());
        assertEquals(1, cache.getHits());

        // Wait 1.2 seconds for expiration
        Thread.sleep(1200);

        // Querying expired domain should result in miss and increment expired count
        List<CacheEntry> expired = cache.get("fast-expire.com", DnsRecord.TYPE_A);
        assertTrue(expired.isEmpty(), "Expired entry should not be returned");
        assertEquals(1, cache.getMisses());
        assertEquals(1, cache.getExpiredCount());
        assertEquals(0, cache.getActiveEntryCount());
    }

    @Test
    @DisplayName("Should evict expired entries during eviction sweep")
    void testEvictExpiredSweep() {
        long now = System.currentTimeMillis();
        // Insert entry that was created 10 seconds ago with 5 second TTL (already expired)
        CacheEntry expiredEntry = new CacheEntry("old.com", DnsRecord.TYPE_A, "1.2.3.4", 5, now - 10_000);
        // Insert valid entry created now with 300s TTL
        CacheEntry validEntry = new CacheEntry("fresh.com", DnsRecord.TYPE_A, "5.6.7.8", 300, now);

        cache.put(expiredEntry);
        cache.put(validEntry);

        assertTrue(expiredEntry.isExpired());
        assertFalse(validEntry.isExpired());

        int evicted = cache.evictExpired();
        assertEquals(1, evicted);
        assertEquals(1, cache.getExpiredCount());
        assertEquals(1, cache.getActiveEntryCount());

        // Valid entry still exists
        List<CacheEntry> validResults = cache.get("fresh.com", DnsRecord.TYPE_A);
        assertEquals(1, validResults.size());
    }

    @Test
    @DisplayName("Should support storing multiple IPs for the same domain")
    void testMultipleRecordsPerDomain() {
        cache.put("multi.com", DnsRecord.TYPE_A, "1.1.1.1", 300);
        cache.put("multi.com", DnsRecord.TYPE_A, "1.0.0.1", 300);

        List<CacheEntry> list = cache.get("multi.com", DnsRecord.TYPE_A);
        assertEquals(2, list.size());
    }

    @Test
    @DisplayName("Should clear all entries and reset active record count")
    void testClearCache() {
        cache.put("site1.com", DnsRecord.TYPE_A, "1.1.1.1", 300);
        cache.put("site2.com", DnsRecord.TYPE_A, "2.2.2.2", 300);
        assertEquals(2, cache.getActiveEntryCount());

        cache.clear();
        assertEquals(0, cache.getActiveEntryCount());
        assertTrue(cache.getAllEntries().isEmpty());
    }
}
