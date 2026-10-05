package com.dns.protocol;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * Represents a DNS Resource Record (RR) as defined in RFC 1035 Section 4.1.3.
 */
public class DnsRecord {

    // Common DNS Types
    public static final int TYPE_A = 1;
    public static final int TYPE_NS = 2;
    public static final int TYPE_CNAME = 5;
    public static final int TYPE_SOA = 6;
    public static final int TYPE_PTR = 12;
    public static final int TYPE_MX = 15;
    public static final int TYPE_TXT = 16;
    public static final int TYPE_AAAA = 28;
    public static final int TYPE_ANY = 255;

    // Common DNS Classes
    public static final int CLASS_IN = 1;

    private final String name;
    private final int type;
    private final int recordClass;
    private final long ttl;
    private final byte[] rdata;
    private final String formattedValue;

    public DnsRecord(String name, int type, int recordClass, long ttl, byte[] rdata, String formattedValue) {
        this.name = name;
        this.type = type;
        this.recordClass = recordClass;
        this.ttl = ttl;
        this.rdata = rdata != null ? rdata.clone() : new byte[0];
        this.formattedValue = formattedValue;
    }

    public static String getTypeName(int type) {
        return switch (type) {
            case TYPE_A -> "A";
            case TYPE_NS -> "NS";
            case TYPE_CNAME -> "CNAME";
            case TYPE_SOA -> "SOA";
            case TYPE_PTR -> "PTR";
            case TYPE_MX -> "MX";
            case TYPE_TXT -> "TXT";
            case TYPE_AAAA -> "AAAA";
            case TYPE_ANY -> "ANY";
            default -> "TYPE" + type;
        };
    }

    public static int parseType(String typeStr) {
        if (typeStr == null) {
            return TYPE_A;
        }
        return switch (typeStr.trim().toUpperCase(Locale.ROOT)) {
            case "A" -> TYPE_A;
            case "AAAA" -> TYPE_AAAA;
            case "CNAME" -> TYPE_CNAME;
            case "NS" -> TYPE_NS;
            case "MX" -> TYPE_MX;
            case "TXT" -> TYPE_TXT;
            case "SOA" -> TYPE_SOA;
            case "PTR" -> TYPE_PTR;
            case "ANY" -> TYPE_ANY;
            default -> {
                try {
                    yield Integer.parseInt(typeStr.trim());
                } catch (NumberFormatException e) {
                    yield TYPE_A;
                }
            }
        };
    }

    public static String formatRData(int type, byte[] rdata, String domainTarget) {
        if (rdata == null || rdata.length == 0) {
            return domainTarget != null ? domainTarget : "";
        }
        try {
            if (type == TYPE_A && rdata.length == 4) {
                return InetAddress.getByAddress(rdata).getHostAddress();
            } else if (type == TYPE_AAAA && rdata.length == 16) {
                return InetAddress.getByAddress(rdata).getHostAddress();
            } else if (type == TYPE_CNAME || type == TYPE_NS || type == TYPE_PTR) {
                return domainTarget != null ? domainTarget : new String(rdata);
            } else if (type == TYPE_TXT) {
                if (rdata.length > 0) {
                    int len = rdata[0] & 0xFF;
                    if (len + 1 <= rdata.length) {
                        return new String(rdata, 1, len);
                    }
                }
                return new String(rdata);
            }
        } catch (UnknownHostException ignored) {
        }
        if (domainTarget != null && !domainTarget.isEmpty()) {
            return domainTarget;
        }
        // Fallback hex representation
        StringBuilder sb = new StringBuilder();
        for (byte b : rdata) {
            sb.append(String.format("%02x ", b));
        }
        return sb.toString().trim();
    }

    public String getName() {
        return name;
    }

    public int getType() {
        return type;
    }

    public String getTypeName() {
        return getTypeName(type);
    }

    public int getRecordClass() {
        return recordClass;
    }

    public long getTtl() {
        return ttl;
    }

    public byte[] getRdata() {
        return rdata.clone();
    }

    public String getFormattedValue() {
        return formattedValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DnsRecord that)) return false;
        return type == that.type &&
                recordClass == that.recordClass &&
                ttl == that.ttl &&
                Objects.equals(name, that.name) &&
                Objects.equals(formattedValue, that.formattedValue) &&
                Arrays.equals(rdata, that.rdata);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, type, recordClass, ttl, formattedValue);
        result = 31 * result + Arrays.hashCode(rdata);
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s\t%d\tIN\t%s\t%s", name, ttl, getTypeName(), formattedValue);
    }
}
