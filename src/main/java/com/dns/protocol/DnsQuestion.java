package com.dns.protocol;

import java.util.Objects;

/**
 * Represents a DNS Question Section entry as defined in RFC 1035 Section 4.1.2.
 */
public class DnsQuestion {

    private final String name;
    private final int type;
    private final int questionClass;

    public DnsQuestion(String name, int type, int questionClass) {
        this.name = name != null ? name.trim().replaceAll("\\.$", "") : "";
        this.type = type;
        this.questionClass = questionClass;
    }

    public DnsQuestion(String name, int type) {
        this(name, type, DnsRecord.CLASS_IN);
    }

    public String getName() {
        return name;
    }

    public int getType() {
        return type;
    }

    public String getTypeName() {
        return DnsRecord.getTypeName(type);
    }

    public int getQuestionClass() {
        return questionClass;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DnsQuestion that)) return false;
        return type == that.type &&
                questionClass == that.questionClass &&
                Objects.equals(name.toLowerCase(), that.name.toLowerCase());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name.toLowerCase(), type, questionClass);
    }

    @Override
    public String toString() {
        return String.format("%s\tIN\t%s", name, getTypeName());
    }
}
