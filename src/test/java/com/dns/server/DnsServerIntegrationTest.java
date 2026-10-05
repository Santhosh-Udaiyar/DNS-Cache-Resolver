package com.dns.server;

import com.dns.cache.DnsCache;
import com.dns.client.DnsClient;
import com.dns.protocol.DnsRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DNS Server and Client UDP Integration Tests")
class DnsServerIntegrationTest {

    private DnsCache serverCache;
    private DnsServer server;
    private int testPort;

    @BeforeEach
    void setUp() throws Exception {
        serverCache = new DnsCache(false);
        // Find a free port
        try (DatagramSocket socket = new DatagramSocket(0)) {
            testPort = socket.getLocalPort();
        }
        server = new DnsServer(testPort, serverCache, "1.1.1.1", 53);
        server.start();
        assertTrue(server.isRunning());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
            assertFalse(server.isRunning());
        }
    }

    @Test
    @DisplayName("Should serve query from server cache over UDP socket")
    void testServerCacheHitOverUdp() {
        // Pre-populate server cache
        serverCache.put("localtest.org", DnsRecord.TYPE_A, "192.168.1.100", 300);

        // Client queries the local test server via UDP
        DnsCache clientCache = new DnsCache(false);
        DnsClient client = new DnsClient(clientCache, "127.0.0.1", testPort, 3000);

        DnsClient.LookupResult result = client.resolve("localtest.org", DnsRecord.TYPE_A, false);

        assertTrue(result.isSuccess(), "Lookup should succeed");
        assertFalse(result.getRecords().isEmpty(), "Should have received records");
        assertEquals("192.168.1.100", result.getRecords().get(0).getFormattedValue());
        assertEquals("localtest.org", result.getRecords().get(0).getName());
    }
}
