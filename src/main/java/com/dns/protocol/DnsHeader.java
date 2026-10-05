package com.dns.protocol;

import java.nio.ByteBuffer;

/**
 * Represents the 12-byte standard DNS packet header as defined in RFC 1035 Section 4.1.1.
 */
public class DnsHeader {

    public static final int HEADER_LENGTH = 12;

    // Response codes (RCODE)
    public static final int RCODE_NO_ERROR = 0;
    public static final int RCODE_FORMAT_ERROR = 1;
    public static final int RCODE_SERVER_FAILURE = 2;
    public static final int RCODE_NAME_ERROR = 3; // NXDOMAIN
    public static final int RCODE_NOT_IMPLEMENTED = 4;
    public static final int RCODE_REFUSED = 5;

    private int id;                 // 16 bits: Identifier
    private boolean qr;             // 1 bit: 0=Query, 1=Response
    private int opcode;             // 4 bits: 0=Standard Query
    private boolean aa;             // 1 bit: Authoritative Answer
    private boolean tc;             // 1 bit: Truncated
    private boolean rd;             // 1 bit: Recursion Desired
    private boolean ra;             // 1 bit: Recursion Available
    private int z;                  // 3 bits: Reserved (0)
    private int rcode;              // 4 bits: Response code
    private int qdCount;            // 16 bits: Number of questions
    private int anCount;            // 16 bits: Number of answers
    private int nsCount;            // 16 bits: Number of authority records
    private int arCount;            // 16 bits: Number of additional records

    public DnsHeader() {
    }

    public DnsHeader(int id, boolean qr, int opcode, boolean aa, boolean tc,
                     boolean rd, boolean ra, int z, int rcode,
                     int qdCount, int anCount, int nsCount, int arCount) {
        this.id = id & 0xFFFF;
        this.qr = qr;
        this.opcode = opcode & 0x0F;
        this.aa = aa;
        this.tc = tc;
        this.rd = rd;
        this.ra = ra;
        this.z = z & 0x07;
        this.rcode = rcode & 0x0F;
        this.qdCount = qdCount & 0xFFFF;
        this.anCount = anCount & 0xFFFF;
        this.nsCount = nsCount & 0xFFFF;
        this.arCount = arCount & 0xFFFF;
    }

    public static DnsHeader createQueryHeader(int id, boolean recursionDesired) {
        DnsHeader header = new DnsHeader();
        header.setId(id);
        header.setQr(false); // Query
        header.setOpcode(0); // Standard Query
        header.setAa(false);
        header.setTc(false);
        header.setRd(recursionDesired);
        header.setRa(false);
        header.setZ(0);
        header.setRcode(RCODE_NO_ERROR);
        header.setQdCount(1);
        header.setAnCount(0);
        header.setNsCount(0);
        header.setArCount(0);
        return header;
    }

    public static DnsHeader parse(ByteBuffer buffer) {
        if (buffer.remaining() < HEADER_LENGTH) {
            throw new IllegalArgumentException("Buffer underflow: DNS header requires 12 bytes");
        }
        int id = buffer.getShort() & 0xFFFF;
        int flags = buffer.getShort() & 0xFFFF;

        boolean qr = ((flags >> 15) & 0x01) == 1;
        int opcode = (flags >> 11) & 0x0F;
        boolean aa = ((flags >> 10) & 0x01) == 1;
        boolean tc = ((flags >> 9) & 0x01) == 1;
        boolean rd = ((flags >> 8) & 0x01) == 1;
        boolean ra = ((flags >> 7) & 0x01) == 1;
        int z = (flags >> 4) & 0x07;
        int rcode = flags & 0x0F;

        int qdCount = buffer.getShort() & 0xFFFF;
        int anCount = buffer.getShort() & 0xFFFF;
        int nsCount = buffer.getShort() & 0xFFFF;
        int arCount = buffer.getShort() & 0xFFFF;

        return new DnsHeader(id, qr, opcode, aa, tc, rd, ra, z, rcode, qdCount, anCount, nsCount, arCount);
    }

    public void writeTo(ByteBuffer buffer) {
        buffer.putShort((short) (id & 0xFFFF));

        int flags = 0;
        if (qr) flags |= (1 << 15);
        flags |= ((opcode & 0x0F) << 11);
        if (aa) flags |= (1 << 10);
        if (tc) flags |= (1 << 9);
        if (rd) flags |= (1 << 8);
        if (ra) flags |= (1 << 7);
        flags |= ((z & 0x07) << 4);
        flags |= (rcode & 0x0F);

        buffer.putShort((short) (flags & 0xFFFF));
        buffer.putShort((short) (qdCount & 0xFFFF));
        buffer.putShort((short) (anCount & 0xFFFF));
        buffer.putShort((short) (nsCount & 0xFFFF));
        buffer.putShort((short) (arCount & 0xFFFF));
    }

    public String getRcodeDescription() {
        return switch (rcode) {
            case RCODE_NO_ERROR -> "NOERROR (Success)";
            case RCODE_FORMAT_ERROR -> "FORMERR (Format Error)";
            case RCODE_SERVER_FAILURE -> "SERVFAIL (Server Failure)";
            case RCODE_NAME_ERROR -> "NXDOMAIN (Non-Existent Domain)";
            case RCODE_NOT_IMPLEMENTED -> "NOTIMP (Not Implemented)";
            case RCODE_REFUSED -> "REFUSED (Query Refused)";
            default -> "RCODE_" + rcode;
        };
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id & 0xFFFF; }

    public boolean isQr() { return qr; }
    public void setQr(boolean qr) { this.qr = qr; }

    public int getOpcode() { return opcode; }
    public void setOpcode(int opcode) { this.opcode = opcode & 0x0F; }

    public boolean isAa() { return aa; }
    public void setAa(boolean aa) { this.aa = aa; }

    public boolean isTc() { return tc; }
    public void setTc(boolean tc) { this.tc = tc; }

    public boolean isRd() { return rd; }
    public void setRd(boolean rd) { this.rd = rd; }

    public boolean isRa() { return ra; }
    public void setRa(boolean ra) { this.ra = ra; }

    public int getZ() { return z; }
    public void setZ(int z) { this.z = z & 0x07; }

    public int getRcode() { return rcode; }
    public void setRcode(int rcode) { this.rcode = rcode & 0x0F; }

    public int getQdCount() { return qdCount; }
    public void setQdCount(int qdCount) { this.qdCount = qdCount & 0xFFFF; }

    public int getAnCount() { return anCount; }
    public void setAnCount(int anCount) { this.anCount = anCount & 0xFFFF; }

    public int getNsCount() { return nsCount; }
    public void setNsCount(int nsCount) { this.nsCount = nsCount & 0xFFFF; }

    public int getArCount() { return arCount; }
    public void setArCount(int arCount) { this.arCount = arCount & 0xFFFF; }

    @Override
    public String toString() {
        return "DnsHeader{" +
                "id=" + id +
                ", qr=" + (qr ? "RESP" : "QUERY") +
                ", opcode=" + opcode +
                ", aa=" + aa +
                ", tc=" + tc +
                ", rd=" + rd +
                ", ra=" + ra +
                ", rcode=" + getRcodeDescription() +
                ", QD=" + qdCount +
                ", AN=" + anCount +
                ", NS=" + nsCount +
                ", AR=" + arCount +
                '}';
    }
}
