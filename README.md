# DNS Cache Resolver

A complete, standalone Java 17 application demonstrating **Domain Name System (DNS)** resolution over raw **UDP sockets (RFC 1035)** with an **in-memory TTL cache**, high-precision performance benchmarking, an embedded caching UDP server, and a modern **Swing GUI**.

---

## Table of Contents
1. [Core Features](#core-features)
2. [DNS Concepts & Theory](#dns-concepts--theory)
   - [What is DNS?](#what-is-dns)
   - [Why UDP for DNS?](#why-udp-for-dns)
   - [RFC 1035 Packet Structure](#rfc-1035-packet-structure)
   - [DNS Caching & TTL Expiration](#dns-caching--ttl-expiration)
3. [Architecture & Project Structure](#architecture--project-structure)
4. [Prerequisites & Setup](#prerequisites--setup)
5. [Running the Application](#running-the-application)
6. [GUI Walkthrough & Features](#gui-walkthrough--features)
7. [Step-by-Step Verification Guide](#step-by-step-verification-guide)
8. [Automated Testing](#automated-testing)
9. [Logging](#logging)

---

## Core Features

- **Raw UDP Sockets**: Direct binary packet construction and transmission using `java.net.DatagramSocket` and `java.net.DatagramPacket` without third-party networking frameworks.
- **RFC 1035 Wire Protocol**: Full binary encoding, label pointer decompression (`0xC0xx`), header flag packing, and resource record parsing.
- **Multiple Record Types**: Supports `A` (IPv4), `AAAA` (IPv6), `CNAME` (Canonical Name / Alias), `MX`, `TXT`, and `ANY`.
- **In-Memory Caching Engine**:
  - Thread-safe storage using concurrent collections.
  - Active TTL tracking with real-time countdown.
  - Automatic background sweeper for expired records.
  - Metrics tracking: Cache Hits, Cache Misses, Hit Ratio (%), Active Records, Expired Records.
- **Performance Benchmarking**:
  - Microsecond-precision timing measuring RAM cache lookup vs. remote network UDP round-trip latency.
  - Comparative speedup metric (e.g., *150x faster / 99% latency eliminated*).
- **Embedded UDP DNS Server**:
  - Local server listening on UDP port `5354` (or configurable).
  - Inspects local cache for answers before forwarding to upstream DNS (Cloudflare `1.1.1.1` or Google `8.8.8.8`).
- **Modern Swing GUI**:
  - Sleek dark theme styled via FlatLaf.
  - Live metric cards, active cache table with countdowns, lookup history, and live log console.
- **JUnit 5 Test Suite**: Comprehensive tests for packet parsing, label decompression, domain validation, TTL expiration, cache hits/misses, and live UDP socket integration.

---

## DNS Concepts & Theory

### What is DNS?
The **Domain Name System (DNS)** is the hierarchical, decentralized naming system that translates human-friendly hostnames (such as `example.com`) into machine-routable IP addresses (such as `93.184.216.34` or `2606:2800:220:1:248:1893:25c8:1946`).

### Why UDP for DNS?
DNS traditionally operates on **UDP port 53**:
1. **Low Latency**: Unlike TCP, UDP requires no three-way handshake (`SYN`, `SYN-ACK`, `ACK`) and no teardown connection overhead.
2. **Stateless Efficiency**: A standard DNS query and response easily fit within a single 512-byte UDP datagram.
3. **Reduced Server Resource Overhead**: DNS root and authoritative nameservers handle billions of requests per second; maintaining TCP connection states for every query would exhaust operating system sockets and memory.

### RFC 1035 Packet Structure

DNS messages share a unified format across queries and responses:

```
+---------------------+
|        Header       | (12 bytes fixed)
+---------------------+
|       Question      | (the query name, type, and class)
+---------------------+
|        Answer       | (Resource Records answering the question)
+---------------------+
|      Authority      | (Resource Records pointing towards authorities)
+---------------------+
|      Additional     | (Resource Records containing additional info)
+---------------------+
```

#### 1. Header (12 Bytes)
```
                                1  1  1  1  1  1
  0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5
+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
|                      ID                       |
+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
|QR|   Opcode  |AA|TC|RD|RA|   Z    |   RCODE   |
+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
|                    QDCOUNT                    |
+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
|                    ANCOUNT                    |
+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
|                    NSCOUNT                    |
+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
|                    ARCOUNT                    |
+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
```
- **ID (16 bits)**: Transaction identifier matching requests to replies.
- **QR (1 bit)**: `0` = Query, `1` = Response.
- **Opcode (4 bits)**: Standard query is `0`.
- **AA (1 bit)**: Authoritative Answer flag.
- **TC (1 bit)**: TrunCation flag.
- **RD (1 bit)**: Recursion Desired (set by client).
- **RA (1 bit)**: Recursion Available (set by server).
- **RCODE (4 bits)**: Return Code (`0` = NOERROR, `3` = NXDOMAIN).
- **QDCOUNT / ANCOUNT / NSCOUNT / ARCOUNT**: Number of entries in each respective section.

#### 2. Label Compression
To keep datagram sizes minimal, RFC 1035 specifies **label compression**. If a domain name or part of it appeared earlier in the packet, it is replaced with a 2-byte pointer:
- Top 2 bits are `11` (`0xC0`).
- Remaining 14 bits represent the offset from the beginning of the DNS header.
- This application fully implements recursive pointer jumping with cycle guards to safely decompress names.

### DNS Caching & TTL Expiration
- Every Resource Record returned by an authoritative server includes a **Time to Live (TTL)** in seconds.
- The resolver stores the resolved record in RAM with `expirationTimestamp = currentTime + (TTL * 1000)`.
- When subsequent lookups arrive before expiration, the client returns the cached entry immediately in **sub-millisecond** time (~0.05 ms) without querying remote DNS nameservers.
- Once the remaining TTL drops to `0`, the record is marked `EXPIRED`, evicted, and a new network query is initiated.

---

## Architecture & Project Structure

```
dns-cache-resolver/
├── pom.xml
├── README.md
├── logs/
│   └── dns.log
├── src/main/java/com/dns/
│   ├── server/
│   │   ├── DnsServer.java          # UDP listener on port 5354 with thread pool
│   │   └── DnsRequestHandler.java  # Processes UDP packets, checks cache, forwards upstream
│   ├── client/
│   │   ├── DnsClient.java          # Raw UDP query client, caching resolver, and timer
│   │   └── DnsGUI.java             # Swing Desktop UI with live stats and dashboard
│   ├── protocol/
│   │   ├── DnsPacket.java          # Message builder, parser, wire serializer, domain validator
│   │   ├── DnsHeader.java          # 12-byte header bit-field encoder/decoder
│   │   ├── DnsQuestion.java        # RFC 1035 question model
│   │   └── DnsRecord.java          # RFC 1035 resource record parser and formatter
│   ├── cache/
│   │   ├── DnsCache.java           # Thread-safe cache with auto-sweeper and statistics
│   │   └── CacheEntry.java         # Cached item with TTL countdown and expiration logic
│   └── util/
│       └── Logger.java             # Thread-safe multi-target logger (Console, File, GUI)
└── src/test/java/com/dns/
    ├── protocol/
    │   └── DnsPacketTest.java      # Packet parsing, label decompression, domain validation tests
    ├── cache/
    │   └── DnsCacheTest.java       # Cache hits, misses, TTL expiration, and sweep tests
    └── server/
        └── DnsServerIntegrationTest.java # End-to-end loopback UDP server test
```

---

## Prerequisites & Setup

- **Java Development Kit (JDK)**: Version 17 or higher
- **Apache Maven**: Version 3.8 or higher

Verify your installation:
```bash
java -version
mvn -version
```

---

## Running the Application

### 1. Build and Package
To compile and generate the executable JAR:
```bash
mvn clean package
```

### 2. Run the Application
Launch the GUI:
```bash
java -jar target/dns-cache-resolver-1.0.0.jar
```
Or run directly through Maven:
```bash
mvn exec:java -Dexec.mainClass="com.dns.client.DnsGUI"
```

---

## GUI Walkthrough & Features

1. **Query Bar**:
   - **Domain Input**: Input field supporting standard domains (e.g. `example.com`, `google.com`, `wikipedia.org`).
   - **Type Selector**: Choose `A (IPv4)`, `AAAA (IPv6)`, `CNAME`, `MX`, `TXT`, or `ANY`.
   - **Upstream DNS Selector**: Quickly switch between Cloudflare (`1.1.1.1`), Google (`8.8.8.8`), Quad9 (`9.9.9.9`), OpenDNS (`208.67.222.222`), or the Local Server (`127.0.0.1:5354`).
   - **Use Cache Checkbox**: Toggle cache-assisted lookup vs. raw direct network resolution.
   - **Presets**: One-click pills for popular domains.
2. **Metric Cards**:
   - **Cache Hits**: Number of requests served instantly from RAM.
   - **Cache Misses**: Number of requests that went over the UDP network.
   - **Hit Ratio (%)**: Efficiency percentage `(Hits / Total) * 100`.
   - **Active Records**: Current count of valid non-expired entries in cache.
   - **Expired Records**: Cumulative count of records that elapsed past their TTL.
   - **Actions**: "Clear Cache" and "Evict Expired Now" buttons.
3. **Performance Banner**:
   - Shows source badge (`CACHE HIT [Local]` vs `REMOTE UPSTREAM`), exact response time, and network latency savings.
4. **Speed Benchmark ("Compare Cache Speed")**:
   - Executes an uncached network query followed by a cached memory query, displaying exact comparison metrics.
5. **Tabs**:
   - **Query Results**: Formatted table of resolved addresses, record types, original TTL, and class.
   - **Active Cache Explorer**: Live updating table displaying domain, record type, value, original TTL, remaining TTL (live countdown every second), and expiration status.
   - **Lookup History**: Log of all queries, timestamps, response times, and results.
   - **Live Log Console**: Real-time log monitor streaming from the logging subsystem.
6. **Embedded UDP DNS Server Control**:
   - Click "Start Local DNS Server" in the header to spin up an RFC 1035 UDP server on port `5354`.
   - Test it with external tools like `nslookup -port=5354 example.com 127.0.0.1`.

---

## Step-by-Step Verification Guide

Follow these steps to verify all requirements:

### Step 1: Initial Query (Cache Miss)
1. Launch the GUI (`mvn exec:java -Dexec.mainClass="com.dns.client.DnsGUI"`).
2. Enter `example.com`, select type `A (IPv4)`, and click **Resolve DNS**.
3. **Observation**:
   - The query goes over the network to upstream DNS (e.g., `1.1.1.1:53`).
   - Response time is around `20 - 60 ms`.
   - Performance banner indicates `Source: REMOTE UPSTREAM`.
   - Cache Misses increments by 1.
   - Active Records increments by the number of resolved answers.
   - The resolved IP `93.184.216.34` appears in the Query Results table.

### Step 2: Repeat Same Query (Cache Hit)
1. Without changing the domain, click **Resolve DNS** again.
2. **Observation**:
   - Response time drops to **`< 1 ms` (typically `0.05 - 0.20 ms`)**.
   - Performance banner indicates `Source: CACHE HIT (Local Memory)`.
   - Cache Hits increments by 1.
   - Hit Ratio increases.

### Step 3: Performance Comparison
1. Click the purple **Compare Cache Speed** button.
2. **Observation**:
   - A dialog displays the side-by-side benchmark:
     ```
     Benchmark Results for 'example.com':
     • Network (UDP Remote): 38.45 ms
     • Cache (RAM Instant): 0.120 ms
     • Performance Boost: 320.4x FASTER (99.7% latency eliminated)
     ```

### Step 4: Active Cache Explorer & Real-Time TTL Countdown
1. Switch to the **Active Cache Explorer** tab.
2. **Observation**:
   - The table lists the cached record with `Domain`, `Type`, `Resolved Value`, `Orig TTL`, and `Remaining TTL`.
   - The `Remaining TTL` column actively counts down second by second.

### Step 5: TTL Expiration Verification
1. Wait until the `Remaining TTL` counts down to `0s` (or test with a short-TTL domain, or observe an entry becoming `EXPIRED`).
2. Alternatively, perform a lookup for a domain with short TTL.
3. Once the record expires, perform the lookup again:
   - Cache registers a MISS (expired entry).
   - Expired Records counter increments.
   - A fresh UDP request is sent upstream and the cache is repopulated with a new TTL.

### Step 6: Query Multiple Domains & Record Types
1. Enter `google.com` and select `AAAA (IPv6)`. Click **Resolve DNS** -> Resolves IPv6 address.
2. Enter `github.com` and select `A (IPv4)`. Click **Resolve DNS** -> Resolves multiple IPv4 addresses.
3. Enter `mail.google.com` and select `CNAME`. Click **Resolve DNS** -> Resolves canonical alias target.

---

## Automated Testing

Run the full automated JUnit 5 test suite:
```bash
mvn clean test
```

### Test Coverage Highlights:
- **`DnsPacketTest`**:
  - Serialization of query packets to RFC 1035 wire format.
  - Parsing multi-record response packets (A and AAAA).
  - RFC 1035 label decompression (`0xC0` pointer offsets and chained pointers).
  - CNAME parsing and RDATA decoding.
  - Comprehensive domain validation (alphanumeric rules, hyphen position, length limits, localhost).
- **`DnsCacheTest`**:
  - Record insertion and lookup.
  - Cache hit and miss accounting.
  - Hit ratio accuracy calculation.
  - TTL expiration timing and automatic transition from valid to expired.
  - Bulk expired eviction sweep (`evictExpired`).
  - Multi-record support (multiple A records per host).
  - Cache clearing and state reset.
- **`DnsServerIntegrationTest`**:
  - Starts the embedded `DnsServer` on an ephemeral UDP socket.
  - Queries the server via loopback using `DnsClient`.
  - Verifies UDP request reception, cache lookups, and response delivery.

---

## Logging

All queries, cache hits, cache misses, upstream UDP communications, and errors are logged to:
- **Standard Output / Console**: Color-friendly formatted logs with millisecond timestamps and thread names.
- **Log File**: `logs/dns.log` (automatically created and rotated/appended).
- **GUI Live Console**: Tab 4 ("Live Log Console") displays real-time activity with auto-scrolling.

Log sample:
```
[2026-10-05 14:54:01.340] [INFO ] [main] Sending UDP query for 'example.com' (A) to 1.1.1.1:53
[2026-10-05 14:54:01.351] [INFO ] [main] DNS query completed via UDP: 'example.com' (A) in 28.66 ms -> 1 answer(s) [NOERROR (Success)]
[2026-10-05 14:54:01.352] [DEBUG] [main] Cache PUT: [example.com A -> 93.184.216.34] (TTL: 300s)
[2026-10-05 14:54:05.110] [INFO ] [main] Cache HIT for 'example.com' (Type: A) -> 1 record(s) returned
[2026-10-05 14:54:05.111] [INFO ] [main] DNS query resolved from CACHE: 'example.com' (A) in 0.114 ms
```
