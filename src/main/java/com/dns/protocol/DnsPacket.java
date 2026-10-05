package com.dns.protocol;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Encapsulates a complete DNS message (RFC 1035), supporting serialization,
 * parsing, name decompression, and domain validation.
 */
public class DnsPacket {

    private static final Random RANDOM = new Random();
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^(?=.{1,253}$)(?!-)[A-Za-z0-9-_]{1,63}(?<!-)(\\.(?!-)[A-Za-z0-9-_]{1,63}(?<!-))*\\.?$"
    );

    private DnsHeader header;
    private final List<DnsQuestion> questions = new ArrayList<>();
    private final List<DnsRecord> answers = new ArrayList<>();
    private final List<DnsRecord> authorities = new ArrayList<>();
    private final List<DnsRecord> additionals = new ArrayList<>();

    public DnsPacket() {
        this.header = new DnsHeader();
    }

    public DnsPacket(DnsHeader header) {
        this.header = header;
    }

    /**
     * Validates whether a domain name adheres to DNS specifications.
     */
    public static boolean isValidDomain(String domain) {
        if (domain == null) return false;
        String trimmed = domain.trim();
        if (trimmed.isEmpty() || trimmed.length() > 253) return false;
        // Allow localhost and standard domain names
        if ("localhost".equalsIgnoreCase(trimmed) || "localhost.".equalsIgnoreCase(trimmed)) {
            return true;
        }
        return DOMAIN_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Creates a standard DNS query packet for the specified domain and record type.
     */
    public static DnsPacket createBasicQuery(String domain, int recordType) {
        int id = RANDOM.nextInt(0xFFFF);
        return createBasicQuery(id, domain, recordType, true);
    }

    /**
     * Creates a DNS query packet with a specific ID and recursion flag.
     */
    public static DnsPacket createBasicQuery(int id, String domain, int recordType, boolean recursionDesired) {
        DnsHeader header = DnsHeader.createQueryHeader(id, recursionDesired);
        DnsPacket packet = new DnsPacket(header);
        packet.addQuestion(new DnsQuestion(domain, recordType));
        return packet;
    }

    /**
     * Creates a DNS response packet for a given query and set of answers.
     */
    public static DnsPacket createResponse(DnsPacket query, List<DnsRecord> answers, int rcode) {
        DnsHeader respHeader = new DnsHeader();
        respHeader.setId(query.getHeader().getId());
        respHeader.setQr(true); // Response
        respHeader.setOpcode(query.getHeader().getOpcode());
        respHeader.setAa(false);
        respHeader.setTc(false);
        respHeader.setRd(query.getHeader().isRd());
        respHeader.setRa(true); // Recursion available
        respHeader.setZ(0);
        respHeader.setRcode(rcode);
        respHeader.setQdCount(query.getQuestions().size());
        respHeader.setAnCount(answers != null ? answers.size() : 0);
        respHeader.setNsCount(0);
        respHeader.setArCount(0);

        DnsPacket respPacket = new DnsPacket(respHeader);
        for (DnsQuestion q : query.getQuestions()) {
            respPacket.addQuestion(q);
        }
        if (answers != null) {
            for (DnsRecord r : answers) {
                respPacket.addAnswer(r);
            }
        }
        return respPacket;
    }

    /**
     * Parses raw UDP bytes into a DnsPacket object.
     */
    public static DnsPacket parse(byte[] data, int length) {
        if (data == null || length < DnsHeader.HEADER_LENGTH) {
            throw new IllegalArgumentException("Invalid DNS packet data or insufficient length");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data, 0, length);
        DnsHeader header = DnsHeader.parse(buffer);
        DnsPacket packet = new DnsPacket(header);

        // Parse Question section
        for (int i = 0; i < header.getQdCount(); i++) {
            String qName = readDomainName(buffer, data);
            int qType = buffer.getShort() & 0xFFFF;
            int qClass = buffer.getShort() & 0xFFFF;
            packet.addQuestion(new DnsQuestion(qName, qType, qClass));
        }

        // Parse Answer section
        for (int i = 0; i < header.getAnCount(); i++) {
            packet.addAnswer(parseRecord(buffer, data));
        }

        // Parse Authority section
        for (int i = 0; i < header.getNsCount(); i++) {
            packet.addAuthority(parseRecord(buffer, data));
        }

        // Parse Additional section
        for (int i = 0; i < header.getArCount(); i++) {
            packet.addAdditional(parseRecord(buffer, data));
        }

        return packet;
    }

    private static DnsRecord parseRecord(ByteBuffer buffer, byte[] fullPacket) {
        String name = readDomainName(buffer, fullPacket);
        int type = buffer.getShort() & 0xFFFF;
        int recordClass = buffer.getShort() & 0xFFFF;
        long ttl = buffer.getInt() & 0xFFFFFFFFL;
        int rdLength = buffer.getShort() & 0xFFFF;

        int rdataStart = buffer.position();
        byte[] rdata = new byte[rdLength];
        buffer.get(rdata);

        String domainTarget = null;
        if (type == DnsRecord.TYPE_CNAME || type == DnsRecord.TYPE_NS || type == DnsRecord.TYPE_PTR) {
            // Read compressed domain name from the rdata offset
            ByteBuffer rdataBuffer = ByteBuffer.wrap(fullPacket);
            rdataBuffer.position(rdataStart);
            domainTarget = readDomainName(rdataBuffer, fullPacket);
        }

        String formattedValue = DnsRecord.formatRData(type, rdata, domainTarget);
        return new DnsRecord(name, type, recordClass, ttl, rdata, formattedValue);
    }

    /**
     * Reads a domain name from the buffer with full RFC 1035 label decompression.
     */
    public static String readDomainName(ByteBuffer buffer, byte[] fullPacket) {
        StringBuilder name = new StringBuilder();
        int initialPosition = buffer.position();
        boolean jumped = false;
        int jumps = 0;
        int maxJumps = 20;

        while (buffer.hasRemaining()) {
            int len = buffer.get() & 0xFF;
            if (len == 0) {
                break; // Root label ends name
            }

            // Check if pointer (top 2 bits set: 11xxxxxx)
            if ((len & 0xC0) == 0xC0) {
                if (!buffer.hasRemaining()) {
                    break;
                }
                int b2 = buffer.get() & 0xFF;
                int pointerOffset = ((len & 0x3F) << 8) | b2;

                if (!jumped) {
                    initialPosition = buffer.position();
                    jumped = true;
                }

                if (++jumps > maxJumps || pointerOffset >= fullPacket.length) {
                    throw new IllegalStateException("DNS compression pointer loop or out of bounds: " + pointerOffset);
                }

                buffer.position(pointerOffset);
            } else {
                // Regular label
                if (buffer.remaining() < len) {
                    break;
                }
                byte[] labelBytes = new byte[len];
                buffer.get(labelBytes);
                if (name.length() > 0) {
                    name.append('.');
                }
                name.append(new String(labelBytes));
            }
        }

        if (jumped) {
            buffer.position(initialPosition);
        }

        return name.toString();
    }

    /**
     * Serializes this DNS message into a raw byte array for UDP transmission.
     */
    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
        DataOutputStream dos = new DataOutputStream(baos);

        try {
            // Prepare header with current section counts
            header.setQdCount(questions.size());
            header.setAnCount(answers.size());
            header.setNsCount(authorities.size());
            header.setArCount(additionals.size());

            ByteBuffer headerBuf = ByteBuffer.allocate(DnsHeader.HEADER_LENGTH);
            header.writeTo(headerBuf);
            dos.write(headerBuf.array());

            // Write questions
            for (DnsQuestion q : questions) {
                writeDomainName(dos, q.getName());
                dos.writeShort((short) q.getType());
                dos.writeShort((short) q.getQuestionClass());
            }

            // Write answers
            for (DnsRecord r : answers) {
                writeRecord(dos, r);
            }

            // Write authorities
            for (DnsRecord r : authorities) {
                writeRecord(dos, r);
            }

            // Write additionals
            for (DnsRecord r : additionals) {
                writeRecord(dos, r);
            }

            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize DNS packet", e);
        }
    }

    private static void writeRecord(DataOutputStream dos, DnsRecord r) throws IOException {
        writeDomainName(dos, r.getName());
        dos.writeShort((short) r.getType());
        dos.writeShort((short) r.getRecordClass());
        dos.writeInt((int) (r.getTtl() & 0xFFFFFFFFL));
        byte[] rdata = r.getRdata();
        dos.writeShort((short) (rdata.length & 0xFFFF));
        dos.write(rdata);
    }

    /**
     * Encodes a domain name as sequence of labels (e.g. 7example3com0).
     */
    public static void writeDomainName(DataOutputStream dos, String domain) throws IOException {
        if (domain == null || domain.isEmpty() || ".".equals(domain)) {
            dos.writeByte(0);
            return;
        }

        String[] labels = domain.split("\\.");
        for (String label : labels) {
            if (label.isEmpty()) continue;
            byte[] bytes = label.getBytes();
            dos.writeByte(bytes.length);
            dos.write(bytes);
        }
        dos.writeByte(0); // Terminator
    }

    // Accessors
    public DnsHeader getHeader() {
        return header;
    }

    public void setHeader(DnsHeader header) {
        this.header = header;
    }

    public void addQuestion(DnsQuestion question) {
        this.questions.add(question);
    }

    public List<DnsQuestion> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    public void addAnswer(DnsRecord record) {
        this.answers.add(record);
    }

    public List<DnsRecord> getAnswers() {
        return Collections.unmodifiableList(answers);
    }

    public void addAuthority(DnsRecord record) {
        this.authorities.add(record);
    }

    public List<DnsRecord> getAuthorities() {
        return Collections.unmodifiableList(authorities);
    }

    public void addAdditional(DnsRecord record) {
        this.additionals.add(record);
    }

    public List<DnsRecord> getAdditionals() {
        return Collections.unmodifiableList(additionals);
    }

    @Override
    public String toString() {
        return "DnsPacket{" +
                "header=" + header +
                ", questions=" + questions +
                ", answers=" + answers.size() +
                ", authorities=" + authorities.size() +
                ", additionals=" + additionals.size() +
                '}';
    }
}
