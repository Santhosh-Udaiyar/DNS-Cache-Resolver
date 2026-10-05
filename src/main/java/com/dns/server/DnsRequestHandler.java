package com.dns.server;

import com.dns.cache.CacheEntry;
import com.dns.cache.DnsCache;
import com.dns.client.DnsClient;
import com.dns.protocol.DnsHeader;
import com.dns.protocol.DnsPacket;
import com.dns.protocol.DnsQuestion;
import com.dns.protocol.DnsRecord;
import com.dns.util.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles incoming raw UDP DNS query packets, serves cached records when available,
 * or forwards upstream to resolve and cache authoritative responses.
 */
public class DnsRequestHandler implements Runnable {

    private final DatagramSocket serverSocket;
    private final byte[] packetData;
    private final int packetLength;
    private final SocketAddress clientAddress;
    private final DnsCache cache;
    private final String upstreamServer;
    private final int upstreamPort;

    public DnsRequestHandler(DatagramSocket serverSocket, byte[] packetData, int packetLength,
                              SocketAddress clientAddress, DnsCache cache,
                              String upstreamServer, int upstreamPort) {
        this.serverSocket = serverSocket;
        this.packetData = packetData;
        this.packetLength = packetLength;
        this.clientAddress = clientAddress;
        this.cache = cache;
        this.upstreamServer = upstreamServer;
        this.upstreamPort = upstreamPort;
    }

    @Override
    public void run() {
        try {
            DnsPacket query = DnsPacket.parse(packetData, packetLength);

            if (query.getHeader().isQr()) {
                // Ignore unexpected responses arriving at server query socket
                return;
            }

            if (query.getQuestions().isEmpty()) {
                sendErrorResponse(query, DnsHeader.RCODE_FORMAT_ERROR);
                return;
            }

            DnsQuestion question = query.getQuestions().get(0);
            String domain = question.getName();
            int type = question.getType();

            Logger.info(String.format("DnsServer received query for '%s' (%s) from %s",
                    domain, DnsRecord.getTypeName(type), clientAddress));

            // 1. Check local cache
            List<CacheEntry> cachedEntries = cache != null ? cache.get(domain, type) : null;
            if (cachedEntries != null && !cachedEntries.isEmpty()) {
                Logger.info(String.format("DnsServer responding from CACHE for '%s' (%s)",
                        domain, DnsRecord.getTypeName(type)));

                List<DnsRecord> answers = new ArrayList<>();
                for (CacheEntry entry : cachedEntries) {
                    byte[] rdata = createRDataForCached(entry);
                    answers.add(new DnsRecord(
                            entry.getDomain(),
                            entry.getRecordType(),
                            DnsRecord.CLASS_IN,
                            entry.getRemainingTtlSeconds(),
                            rdata,
                            entry.getValue()
                    ));
                }

                DnsPacket response = DnsPacket.createResponse(query, answers, DnsHeader.RCODE_NO_ERROR);
                byte[] responseBytes = response.toByteArray();
                sendResponse(responseBytes);
                return;
            }

            // 2. Cache miss -> Forward upstream
            Logger.info(String.format("DnsServer cache MISS for '%s' -> Forwarding to upstream %s:%d",
                    domain, upstreamServer, upstreamPort));

            byte[] upstreamResponse = DnsClient.sendUdpQuery(
                    java.util.Arrays.copyOf(packetData, packetLength),
                    upstreamServer,
                    upstreamPort,
                    3000
            );

            // Parse response to store records in cache
            try {
                DnsPacket parsedUpstream = DnsPacket.parse(upstreamResponse, upstreamResponse.length);
                if (cache != null && parsedUpstream.getHeader().getRcode() == DnsHeader.RCODE_NO_ERROR) {
                    cache.putAll(parsedUpstream.getAnswers());
                }
            } catch (Exception parseEx) {
                Logger.warn("Failed to parse upstream response for caching: " + parseEx.getMessage());
            }

            // Relay upstream response back to client
            sendResponse(upstreamResponse);

        } catch (Exception e) {
            Logger.error("Error processing DNS request from " + clientAddress + ": " + e.getMessage(), e);
        }
    }

    private static byte[] createRDataForCached(CacheEntry entry) {
        try {
            if (entry.getRecordType() == DnsRecord.TYPE_A || entry.getRecordType() == DnsRecord.TYPE_AAAA) {
                return InetAddress.getByName(entry.getValue()).getAddress();
            } else if (entry.getRecordType() == DnsRecord.TYPE_CNAME) {
                // Serialized target domain name
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);
                DnsPacket.writeDomainName(dos, entry.getValue());
                return baos.toByteArray();
            }
        } catch (Exception ignored) {
        }
        return entry.getValue().getBytes();
    }

    private void sendResponse(byte[] responseData) throws IOException {
        DatagramPacket sendPacket = new DatagramPacket(responseData, responseData.length, clientAddress);
        serverSocket.send(sendPacket);
    }

    private void sendErrorResponse(DnsPacket query, int rcode) {
        try {
            DnsPacket response = DnsPacket.createResponse(query, null, rcode);
            byte[] responseBytes = response.toByteArray();
            sendResponse(responseBytes);
        } catch (Exception ignored) {
        }
    }
}
