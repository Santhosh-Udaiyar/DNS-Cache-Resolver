package com.dns.cache;

import com.dns.protocol.DnsRecord;
import com.dns.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe in-memory DNS Cache with automatic TTL expiration,
 * statistics tracking (hits, misses, ratio, expired), and real-time listeners.
 */
public class DnsCache {

    private final Map<String, List<CacheEntry>> cache = new ConcurrentHashMap<>();
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong expiredCount = new AtomicLong(0);

    private final ScheduledExecutorService cleanerService;
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    public DnsCache() {
        this(true);
    }

    public DnsCache(boolean enableBackgroundCleaner) {
        if (enableBackgroundCleaner) {
            cleanerService = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "DnsCache-Cleaner");
                t.setDaemon(true);
                return t;
            });
            cleanerService.scheduleAtFixedRate(this::evictExpired, 2, 2, TimeUnit.SECONDS);
        } else {
            cleanerService = null;
        }
    }

    public void addChangeListener(Runnable listener) {
        if (listener != null && !changeListeners.contains(listener)) {
            changeListeners.add(listener);
        }
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (Exception ignored) {
            }
        }
    }

    private static String buildKey(String domain, int recordType) {
        String normalizedDomain = domain != null ? domain.trim().toLowerCase().replaceAll("\\.$", "") : "";
        return normalizedDomain + "#" + recordType;
    }

    /**
     * Stores a single entry in the cache.
     */
    public void put(String domain, int recordType, String value, long ttl) {
        put(new CacheEntry(domain, recordType, value, ttl));
    }

    /**
     * Stores a CacheEntry in the cache.
     */
    public synchronized void put(CacheEntry entry) {
        if (entry == null || entry.getTtlSeconds() <= 0) {
            return;
        }
        String key = buildKey(entry.getDomain(), entry.getRecordType());
        List<CacheEntry> entries = cache.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());

        // Remove any existing entry matching same domain, type, and value
        entries.removeIf(e -> e.getValue().equalsIgnoreCase(entry.getValue()));
        entries.add(entry);

        Logger.debug(String.format("Cache PUT: [%s %s -> %s] (TTL: %ds)",
                entry.getDomain(), entry.getRecordTypeName(), entry.getValue(), entry.getTtlSeconds()));
        notifyListeners();
    }

    /**
     * Stores all records from a list into the cache.
     */
    public void putAll(List<DnsRecord> records) {
        if (records == null || records.isEmpty()) return;
        for (DnsRecord record : records) {
            if (record.getTtl() > 0 && !record.getFormattedValue().isEmpty()) {
                put(CacheEntry.fromRecord(record));
            }
        }
    }

    /**
     * Retrieves valid cached entries for a domain and record type.
     * Automatically purges expired entries and updates hit/miss/expired statistics.
     */
    public List<CacheEntry> get(String domain, int recordType) {
        String key = buildKey(domain, recordType);
        List<CacheEntry> entries = cache.get(key);

        if (entries == null || entries.isEmpty()) {
            misses.incrementAndGet();
            Logger.info(String.format("Cache MISS for '%s' (Type: %s)", domain, DnsRecord.getTypeName(recordType)));
            notifyListeners();
            return Collections.emptyList();
        }

        List<CacheEntry> validEntries = new ArrayList<>();
        List<CacheEntry> expiredEntries = new ArrayList<>();

        for (CacheEntry entry : entries) {
            if (entry.isExpired()) {
                expiredEntries.add(entry);
            } else {
                validEntries.add(entry);
            }
        }

        if (!expiredEntries.isEmpty()) {
            entries.removeAll(expiredEntries);
            long newlyExpired = expiredCount.addAndGet(expiredEntries.size());
            Logger.info(String.format("Expired %d entries during query for '%s'. Total expired: %d",
                    expiredEntries.size(), domain, newlyExpired));
            if (entries.isEmpty()) {
                cache.remove(key);
            }
        }

        if (validEntries.isEmpty()) {
            misses.incrementAndGet();
            Logger.info(String.format("Cache MISS (all entries expired) for '%s' (Type: %s)",
                    domain, DnsRecord.getTypeName(recordType)));
            notifyListeners();
            return Collections.emptyList();
        }

        hits.incrementAndGet();
        Logger.info(String.format("Cache HIT for '%s' (Type: %s) -> %d record(s) returned",
                domain, DnsRecord.getTypeName(recordType), validEntries.size()));
        notifyListeners();
        return validEntries;
    }

    /**
     * Peeks into the cache without recording hit/miss statistics.
     */
    public List<CacheEntry> peek(String domain, int recordType) {
        String key = buildKey(domain, recordType);
        List<CacheEntry> entries = cache.get(key);
        if (entries == null) return Collections.emptyList();

        List<CacheEntry> valid = new ArrayList<>();
        for (CacheEntry entry : entries) {
            if (!entry.isExpired()) {
                valid.add(entry);
            }
        }
        return valid;
    }

    /**
     * Evicts all expired entries across the entire cache.
     */
    public synchronized int evictExpired() {
        int removedCount = 0;
        for (Map.Entry<String, List<CacheEntry>> entryMap : cache.entrySet()) {
            List<CacheEntry> list = entryMap.getValue();
            List<CacheEntry> toRemove = new ArrayList<>();
            for (CacheEntry ce : list) {
                if (ce.isExpired()) {
                    toRemove.add(ce);
                }
            }
            if (!toRemove.isEmpty()) {
                list.removeAll(toRemove);
                removedCount += toRemove.size();
                if (list.isEmpty()) {
                    cache.remove(entryMap.getKey());
                }
            }
        }

        if (removedCount > 0) {
            long total = expiredCount.addAndGet(removedCount);
            Logger.info(String.format("Cache eviction sweep: purged %d expired record(s). Cumulative expired: %d",
                    removedCount, total));
            notifyListeners();
        }
        return removedCount;
    }

    /**
     * Clears all stored cache entries.
     */
    public synchronized void clear() {
        int count = getActiveEntryCount();
        cache.clear();
        Logger.info(String.format("Cache cleared. Removed %d active record(s).", count));
        notifyListeners();
    }

    /**
     * Resets statistics counters (hits, misses, expired count).
     */
    public void resetStatistics() {
        hits.set(0);
        misses.set(0);
        expiredCount.set(0);
        notifyListeners();
    }

    /**
     * Returns a snapshot list of all entries currently stored in cache.
     */
    public List<CacheEntry> getAllEntries() {
        List<CacheEntry> all = new ArrayList<>();
        for (List<CacheEntry> list : cache.values()) {
            all.addAll(list);
        }
        return all;
    }

    public int getActiveEntryCount() {
        int count = 0;
        for (List<CacheEntry> list : cache.values()) {
            for (CacheEntry ce : list) {
                if (!ce.isExpired()) {
                    count++;
                }
            }
        }
        return count;
    }

    public long getHits() {
        return hits.get();
    }

    public long getMisses() {
        return misses.get();
    }

    public long getExpiredCount() {
        return expiredCount.get();
    }

    public double getHitRatio() {
        long h = hits.get();
        long m = misses.get();
        long total = h + m;
        return total == 0 ? 0.0 : ((double) h / total) * 100.0;
    }

    public void shutdown() {
        if (cleanerService != null) {
            cleanerService.shutdownNow();
        }
    }
}
