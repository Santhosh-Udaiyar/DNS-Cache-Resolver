package com.dns.cache;

import com.dns.protocol.DnsRecord;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Represents a single cached DNS record with TTL tracking and expiration calculation.
 */
public class CacheEntry {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final String domain;
    private final int recordType;
    private final String value;
    private final long ttlSeconds;
    private final long creationTimestamp;
    private final long expirationTimestamp;

    public CacheEntry(String domain, int recordType, String value, long ttlSeconds) {
        this(domain, recordType, value, ttlSeconds, System.currentTimeMillis());
    }

    public CacheEntry(String domain, int recordType, String value, long ttlSeconds, long creationTimestamp) {
        this.domain = domain != null ? domain.trim().toLowerCase() : "";
        this.recordType = recordType;
        this.value = value != null ? value.trim() : "";
        this.ttlSeconds = Math.max(0, ttlSeconds);
        this.creationTimestamp = creationTimestamp;
        this.expirationTimestamp = creationTimestamp + (this.ttlSeconds * 1000L);
    }

    public static CacheEntry fromRecord(DnsRecord record) {
        return new CacheEntry(
                record.getName(),
                record.getType(),
                record.getFormattedValue(),
                record.getTtl(),
                System.currentTimeMillis()
        );
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expirationTimestamp;
    }

    public long getRemainingTtlSeconds() {
        long remainingMs = expirationTimestamp - System.currentTimeMillis();
        return remainingMs > 0 ? (remainingMs + 999) / 1000 : 0;
    }

    public long getAgeSeconds() {
        long ageMs = System.currentTimeMillis() - creationTimestamp;
        return Math.max(0, ageMs / 1000);
    }

    public String getDomain() {
        return domain;
    }

    public int getRecordType() {
        return recordType;
    }

    public String getRecordTypeName() {
        return DnsRecord.getTypeName(recordType);
    }

    public String getValue() {
        return value;
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public long getCreationTimestamp() {
        return creationTimestamp;
    }

    public long getExpirationTimestamp() {
        return expirationTimestamp;
    }

    public String getFormattedCreationTime() {
        return FORMATTER.format(Instant.ofEpochMilli(creationTimestamp));
    }

    public String getFormattedExpirationTime() {
        return FORMATTER.format(Instant.ofEpochMilli(expirationTimestamp));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CacheEntry that)) return false;
        return recordType == that.recordType &&
                Objects.equals(domain, that.domain) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(domain, recordType, value);
    }

    @Override
    public String toString() {
        return "CacheEntry{" +
                "domain='" + domain + '\'' +
                ", type=" + getRecordTypeName() +
                ", value='" + value + '\'' +
                ", ttl=" + ttlSeconds +
                "s, remaining=" + getRemainingTtlSeconds() + "s" +
                ", expired=" + isExpired() +
                '}';
    }
}
