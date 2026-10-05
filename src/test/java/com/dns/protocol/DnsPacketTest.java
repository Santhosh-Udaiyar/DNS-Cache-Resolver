package com.dns.protocol;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DNS Packet Protocol Tests")
class DnsPacketTest {

    @Test
    @DisplayName("Should create valid query packet and serialize to RFC 1035 bytes")
    void testCreateBasicQueryPacket() {
        DnsPacket packet = DnsPacket.createBasicQuery(0x1234, "example.com", DnsRecord.TYPE_A, true);

        assertNotNull(packet.getHeader());
        assertEquals(0x1234, packet.getHeader().getId());
        assertFalse(packet.getHeader().isQr()); // Query
        assertTrue(packet.getHeader().isRd());  // Recursion Desired
        assertEquals(1, packet.getQuestions().size());

        DnsQuestion question = packet.getQuestions().get(0);
        assertEquals("example.com", question.getName());
        assertEquals(DnsRecord.TYPE_A, question.getType());
        assertEquals(DnsRecord.CLASS_IN, question.getQuestionClass());

        byte[] rawBytes = packet.toByteArray();
        assertTrue(rawBytes.length > 12); // At least header (12 bytes) + question

        // Parse back the generated raw bytes
        DnsPacket parsed = DnsPacket.parse(rawBytes, rawBytes.length);
        assertEquals(0x1234, parsed.getHeader().getId());
        assertFalse(parsed.getHeader().isQr());
        assertEquals(1, parsed.getQuestions().size());
        assertEquals("example.com", parsed.getQuestions().get(0).getName());
        assertEquals(DnsRecord.TYPE_A, parsed.getQuestions().get(0).getType());
    }

    @Test
    @DisplayName("Should parse DNS response packet with A and AAAA records and compression pointers")
    void testParseDnsResponseWithCompression() throws IOException {
        // Build a synthetic RFC 1035 DNS response
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Header: ID=0xABCD, QR=1 (response), AA=1, RD=1, RA=1, QD=1, AN=2, NS=0, AR=0
        DnsHeader header = new DnsHeader(0xABCD, true, 0, true, false, true, true, 0, 0, 1, 2, 0, 0);
        ByteBuffer headerBuf = ByteBuffer.allocate(12);
        header.writeTo(headerBuf);
        dos.write(headerBuf.array());

        // Question: example.com, TYPE A, CLASS IN
        // Offset of "example.com" starts at byte 12
        int exampleComOffset = 12;
        DnsPacket.writeDomainName(dos, "example.com");
        dos.writeShort(DnsRecord.TYPE_A);
        dos.writeShort(DnsRecord.CLASS_IN);

        // Answer 1: name points to example.com (0xC00C), Type A, TTL 300, 93.184.216.34
        dos.writeByte(0xC0);
        dos.writeByte(exampleComOffset); // Pointer to example.com at byte 12
        dos.writeShort(DnsRecord.TYPE_A);
        dos.writeShort(DnsRecord.CLASS_IN);
        dos.writeInt(300); // TTL
        dos.writeShort(4);   // rdlength
        dos.write(InetAddress.getByName("93.184.216.34").getAddress());

        // Answer 2: name points to example.com (0xC00C), Type AAAA, TTL 300, 2606:2800:220:1:248:1893:25c8:1946
        dos.writeByte(0xC0);
        dos.writeByte(exampleComOffset);
        dos.writeShort(DnsRecord.TYPE_AAAA);
        dos.writeShort(DnsRecord.CLASS_IN);
        dos.writeInt(300);
        byte[] ipv6Bytes = InetAddress.getByName("2606:2800:220:1:248:1893:25c8:1946").getAddress();
        dos.writeShort(ipv6Bytes.length);
        dos.write(ipv6Bytes);

        dos.flush();
        byte[] packetData = baos.toByteArray();

        // Parse
        DnsPacket response = DnsPacket.parse(packetData, packetData.length);
        assertNotNull(response);
        assertEquals(0xABCD, response.getHeader().getId());
        assertTrue(response.getHeader().isQr());
        assertEquals(1, response.getQuestions().size());
        assertEquals("example.com", response.getQuestions().get(0).getName());

        List<DnsRecord> answers = response.getAnswers();
        assertEquals(2, answers.size());

        DnsRecord aRec = answers.get(0);
        assertEquals("example.com", aRec.getName());
        assertEquals(DnsRecord.TYPE_A, aRec.getType());
        assertEquals("93.184.216.34", aRec.getFormattedValue());
        assertEquals(300, aRec.getTtl());

        DnsRecord aaaaRec = answers.get(1);
        assertEquals("example.com", aaaaRec.getName());
        assertEquals(DnsRecord.TYPE_AAAA, aaaaRec.getType());
        assertTrue(aaaaRec.getFormattedValue().contains("2606:2800:220:1"));
    }

    @Test
    @DisplayName("Should parse and format CNAME records correctly")
    void testParseCnameRecord() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        DnsHeader header = new DnsHeader(0x5678, true, 0, false, false, true, true, 0, 0, 1, 1, 0, 0);
        ByteBuffer headerBuf = ByteBuffer.allocate(12);
        header.writeTo(headerBuf);
        dos.write(headerBuf.array());

        // Question: mail.example.com
        DnsPacket.writeDomainName(dos, "mail.example.com");
        dos.writeShort(DnsRecord.TYPE_CNAME);
        dos.writeShort(DnsRecord.CLASS_IN);

        // Answer: mail.example.com -> target.example.com
        DnsPacket.writeDomainName(dos, "mail.example.com");
        dos.writeShort(DnsRecord.TYPE_CNAME);
        dos.writeShort(DnsRecord.CLASS_IN);
        dos.writeInt(600);

        ByteArrayOutputStream rdataStream = new ByteArrayOutputStream();
        DataOutputStream rdataDos = new DataOutputStream(rdataStream);
        DnsPacket.writeDomainName(rdataDos, "target.example.com");
        byte[] rdata = rdataStream.toByteArray();

        dos.writeShort(rdata.length);
        dos.write(rdata);

        dos.flush();
        byte[] raw = baos.toByteArray();

        DnsPacket packet = DnsPacket.parse(raw, raw.length);
        assertEquals(1, packet.getAnswers().size());
        DnsRecord rec = packet.getAnswers().get(0);
        assertEquals(DnsRecord.TYPE_CNAME, rec.getType());
        assertEquals("target.example.com", rec.getFormattedValue());
        assertEquals(600, rec.getTtl());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "example.com",
            "google.com",
            "sub.domain.co.uk",
            "my-domain.org",
            "a.b.c.d.com",
            "localhost"
    })
    @DisplayName("Should validate valid domain names")
    void testValidDomains(String domain) {
        assertTrue(DnsPacket.isValidDomain(domain), "Domain should be valid: " + domain);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "-leading-hyphen.com",
            "trailing-hyphen-.com",
            "invalid..double-dots.com",
            "spaces in domain.com",
            "special$char.com"
    })
    @DisplayName("Should reject invalid domain names")
    void testInvalidDomains(String domain) {
        assertFalse(DnsPacket.isValidDomain(domain), "Domain should be invalid: " + domain);
    }

    @Test
    @DisplayName("Should reject null domain name")
    void testNullDomain() {
        assertFalse(DnsPacket.isValidDomain(null));
    }
}
