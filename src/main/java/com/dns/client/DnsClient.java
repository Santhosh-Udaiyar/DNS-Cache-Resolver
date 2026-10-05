package com.dns.client;

import com.dns.cache.CacheEntry;
import com.dns.cache.DnsCache;
import com.dns.protocol.DnsHeader;
import com.dns.protocol.DnsPacket;
import com.dns.protocol.DnsRecord;
import com.dns.util.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Raw UDP DNS Client that handles queries, caching, upstream DNS communication,
 * and high-precision performance timing.
 */
public class DnsClient {

    public static final String DEFAULT_UPSTREAM = "1.1.1.1"; // Cloudflare DNS
    public static final int DNS_PORT = 53;
    public static final int DEFAULT_TIMEOUT_MS = 3000;
    public static final int BUFFER_SIZE = 512; // Standard UDP DNS buffer size

    private final DnsCache cache;
    private String upstreamDnsServer;
    private int upstreamPort;
    private int timeoutMs;

    public DnsClient(DnsCache cache) {
        this(cache, DEFAULT_UPSTREAM, DNS_PORT, DEFAULT_TIMEOUT_MS);
    }

    public DnsClient(DnsCache cache, String upstreamDnsServer, int upstreamPort, int timeoutMs) {
        this.cache = cache;
        this.upstreamDnsServer = upstreamDnsServer;
        this.upstreamPort = upstreamPort;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Resolves a domain name for a given record type.
     * Checks local cache first; if missing or expired, performs raw UDP lookup.
     */
    public LookupResult resolve(String domain, int recordType) {
        return resolve(domain, recordType, true);
    }

    /**
     * Resolves a domain name with option to bypass cache.
     */
    public LookupResult resolve(String domain, int recordType, boolean useCache) {
        if (!DnsPacket.isValidDomain(domain)) {
            String error = "Invalid domain name format: " + domain;
            Logger.warn(error);
            return new LookupResult(domain, recordType, Collections.emptyList(), 0, false, 0, 0,
                    upstreamDnsServer, DnsHeader.RCODE_FORMAT_ERROR, error, false);
        }

        String normalizedDomain = domain.trim().toLowerCase().replaceAll("\\.$", "");
        long cacheLookupNanos = 0;

        // 1. Check Cache
        if (useCache && cache != null) {
            long cacheStart = System.nanoTime();
            List<CacheEntry> cached = cache.get(normalizedDomain, recordType);
            cacheLookupNanos = System.nanoTime() - cacheStart;

            if (!cached.isEmpty()) {
                List<DnsRecord> records = new ArrayList<>();
                for (CacheEntry entry : cached) {
                    records.add(new DnsRecord(
                            entry.getDomain(),
                            entry.getRecordType(),
                            DnsRecord.CLASS_IN,
                            entry.getRemainingTtlSeconds(),
                            null,
                            entry.getValue()
                    ));
                }

                double totalMs = cacheLookupNanos / 1_000_000.0;
                Logger.info(String.format("DNS query resolved from CACHE: '%s' (%s) in %.3f ms",
                        normalizedDomain, DnsRecord.getTypeName(recordType), totalMs));

                return new LookupResult(normalizedDomain, recordType, records, totalMs, true,
                        cacheLookupNanos, 0, "Local Cache", DnsHeader.RCODE_NO_ERROR, null, true);
            }
        }

        // 2. Perform raw UDP lookup over the network
        Logger.info(String.format("Sending UDP query for '%s' (%s) to %s:%d",
                normalizedDomain, DnsRecord.getTypeName(recordType), upstreamDnsServer, upstreamPort));

        long networkStart = System.nanoTime();
        try {
            DnsPacket queryPacket = DnsPacket.createBasicQuery(normalizedDomain, recordType);
            byte[] queryBytes = queryPacket.toByteArray();

            byte[] responseBytes = sendUdpQuery(queryBytes, upstreamDnsServer, upstreamPort, timeoutMs);
            long networkLookupNanos = System.nanoTime() - networkStart;
            double networkMs = networkLookupNanos / 1_000_000.0;

            DnsPacket responsePacket = DnsPacket.parse(responseBytes, responseBytes.length);

            int rcode = responsePacket.getHeader().getRcode();
            String rcodeDesc = responsePacket.getHeader().getRcodeDescription();

            List<DnsRecord> answers = responsePacket.getAnswers();
            List<DnsRecord> filteredAnswers = new ArrayList<>();

            for (DnsRecord rec : answers) {
                // Keep matching records or CNAME records in the chain
                if (rec.getType() == recordType || rec.getType() == DnsRecord.TYPE_CNAME) {
                    filteredAnswers.add(rec);
                }
            }

            if (filteredAnswers.isEmpty() && !answers.isEmpty()) {
                filteredAnswers.addAll(answers);
            }

            // 3. Store valid answers into cache
            if (cache != null && rcode == DnsHeader.RCODE_NO_ERROR && !filteredAnswers.isEmpty()) {
                cache.putAll(filteredAnswers);
            }

            boolean isSuccess = (rcode == DnsHeader.RCODE_NO_ERROR);
            Logger.info(String.format("DNS query completed via UDP: '%s' (%s) in %.2f ms -> %d answer(s) [%s]",
                    normalizedDomain, DnsRecord.getTypeName(recordType), networkMs,
                    filteredAnswers.size(), rcodeDesc));

            return new LookupResult(normalizedDomain, recordType, filteredAnswers, networkMs, false,
                    cacheLookupNanos, networkLookupNanos, upstreamDnsServer + ":" + upstreamPort,
                    rcode, isSuccess ? null : rcodeDesc, isSuccess);

        } catch (SocketTimeoutException e) {
            long networkLookupNanos = System.nanoTime() - networkStart;
            String error = "UDP DNS request timed out (" + timeoutMs + "ms) from " + upstreamDnsServer;
            Logger.error(error);
            return new LookupResult(normalizedDomain, recordType, Collections.emptyList(),
                    networkLookupNanos / 1_000_000.0, false, cacheLookupNanos, networkLookupNanos,
                    upstreamDnsServer, DnsHeader.RCODE_SERVER_FAILURE, error, false);
        } catch (Exception e) {
            long networkLookupNanos = System.nanoTime() - networkStart;
            String error = "DNS Query Error: " + e.getMessage();
            Logger.error(error, e);
            return new LookupResult(normalizedDomain, recordType, Collections.emptyList(),
                    networkLookupNanos / 1_000_000.0, false, cacheLookupNanos, networkLookupNanos,
                    upstreamDnsServer, DnsHeader.RCODE_SERVER_FAILURE, error, false);
        }
    }

    /**
     * Executes a raw UDP socket transmission and listens for a response.
     */
    public static byte[] sendUdpQuery(byte[] requestData, String serverHost, int port, int timeoutMs) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);

            InetAddress serverAddress = InetAddress.getByName(serverHost);
            DatagramPacket sendPacket = new DatagramPacket(requestData, requestData.length, serverAddress, port);
            socket.send(sendPacket);

            byte[] buffer = new byte[BUFFER_SIZE];
            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(receivePacket);

            byte[] responseData = new byte[receivePacket.getLength()];
            System.arraycopy(receivePacket.getData(), 0, responseData, 0, receivePacket.getLength());
            return responseData;
        }
    }

    // Getters and Setters
    public DnsCache getCache() {
        return cache;
    }

    public String getUpstreamDnsServer() {
        return upstreamDnsServer;
    }

    public void setUpstreamDnsServer(String upstreamDnsServer) {
        this.upstreamDnsServer = upstreamDnsServer;
    }

    public int getUpstreamPort() {
        return upstreamPort;
    }

    public void setUpstreamPort(int upstreamPort) {
        this.upstreamPort = upstreamPort;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * Immutable result of a DNS lookup.
     */
    public static class LookupResult {
        private final String domain;
        private final int recordType;
        private final List<DnsRecord> records;
        private final double responseTimeMs;
        private final boolean cacheHit;
        private final long cacheLookupNanos;
        private final long networkLookupNanos;
        private final String serverUsed;
        private final int rcode;
        private final String errorMessage;
        private final boolean success;

        public LookupResult(String domain, int recordType, List<DnsRecord> records, double responseTimeMs,
                            boolean cacheHit, long cacheLookupNanos, long networkLookupNanos,
                            String serverUsed, int rcode, String errorMessage, boolean success) {
            this.domain = domain;
            this.recordType = recordType;
            this.records = records != null ? records : Collections.emptyList();
            this.responseTimeMs = responseTimeMs;
            this.cacheHit = cacheHit;
            this.cacheLookupNanos = cacheLookupNanos;
            this.networkLookupNanos = networkLookupNanos;
            this.serverUsed = serverUsed;
            this.rcode = rcode;
            this.errorMessage = errorMessage;
            this.success = success;
        }

        public String getDomain() { return domain; }
        public int getRecordType() { return recordType; }
        public String getRecordTypeName() { return DnsRecord.getTypeName(recordType); }
        public List<DnsRecord> getRecords() { return records; }
        public double getResponseTimeMs() { return responseTimeMs; }
        public boolean isCacheHit() { return cacheHit; }
        public long getCacheLookupNanos() { return cacheLookupNanos; }
        public long getNetworkLookupNanos() { return networkLookupNanos; }
        public String getServerUsed() { return serverUsed; }
        public int getRcode() { return rcode; }
        public String getErrorMessage() { return errorMessage; }
        public boolean isSuccess() { return success; }

        public List<String> getIpv4Addresses() {
            List<String> list = new ArrayList<>();
            for (DnsRecord r : records) {
                if (r.getType() == DnsRecord.TYPE_A) {
                    list.add(r.getFormattedValue());
                }
            }
            return list;
        }

        public List<String> getIpv6Addresses() {
            List<String> list = new ArrayList<>();
            for (DnsRecord r : records) {
                if (r.getType() == DnsRecord.TYPE_AAAA) {
                    list.add(r.getFormattedValue());
                }
            }
            return list;
        }

        public List<String> getCnames() {
            List<String> list = new ArrayList<>();
            for (DnsRecord r : records) {
                if (r.getType() == DnsRecord.TYPE_CNAME) {
                    list.add(r.getFormattedValue());
                }
            }
            return list;
        }
    }
}
