package com.dns.server;

import com.dns.cache.DnsCache;
import com.dns.util.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * High-performance standalone UDP DNS Server that listens for DNS requests,
 * checks the shared in-memory DNS cache, and delegates unresolved queries upstream.
 */
public class DnsServer implements Runnable {

    public static final int DEFAULT_SERVER_PORT = 5354;
    public static final String DEFAULT_UPSTREAM_SERVER = "1.1.1.1";
    public static final int DEFAULT_UPSTREAM_PORT = 53;
    private static final int BUFFER_SIZE = 512;

    private final int port;
    private final String upstreamServer;
    private final int upstreamPort;
    private final DnsCache cache;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private DatagramSocket socket;
    private ExecutorService threadPool;
    private Thread serverThread;
    private Consumer<Boolean> statusListener;

    public DnsServer(DnsCache cache) {
        this(DEFAULT_SERVER_PORT, cache, DEFAULT_UPSTREAM_SERVER, DEFAULT_UPSTREAM_PORT);
    }

    public DnsServer(int port, DnsCache cache, String upstreamServer, int upstreamPort) {
        this.port = port;
        this.cache = cache;
        this.upstreamServer = upstreamServer;
        this.upstreamPort = upstreamPort;
    }

    public void setStatusListener(Consumer<Boolean> listener) {
        this.statusListener = listener;
    }

    public synchronized void start() throws SocketException {
        if (running.get()) {
            Logger.warn("DnsServer is already running on port " + port);
            return;
        }

        socket = new DatagramSocket(port);
        threadPool = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "DnsWorker");
            t.setDaemon(true);
            return t;
        });

        running.set(true);
        serverThread = new Thread(this, "DnsServer-Listener-" + port);
        serverThread.setDaemon(true);
        serverThread.start();

        Logger.info(String.format("DnsServer STARTED on UDP port %d (Upstream: %s:%d)", port, upstreamServer, upstreamPort));
        if (statusListener != null) {
            statusListener.accept(true);
        }
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }

        running.set(false);

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        if (threadPool != null) {
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(2, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
            }
        }

        Logger.info("DnsServer STOPPED on UDP port " + port);
        if (statusListener != null) {
            statusListener.accept(false);
        }
    }

    @Override
    public void run() {
        byte[] buffer = new byte[BUFFER_SIZE];

        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] packetData = Arrays.copyOf(packet.getData(), packet.getLength());
                threadPool.submit(new DnsRequestHandler(
                        socket,
                        packetData,
                        packet.getLength(),
                        packet.getSocketAddress(),
                        cache,
                        upstreamServer,
                        upstreamPort
                ));

            } catch (SocketException e) {
                if (!running.get()) {
                    // Socket closed intentionally during stop()
                    break;
                }
                Logger.error("Socket error on DNS Server: " + e.getMessage(), e);
            } catch (IOException e) {
                if (running.get()) {
                    Logger.error("I/O error on DNS Server: " + e.getMessage(), e);
                }
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getPort() {
        return port;
    }

    public String getUpstreamServer() {
        return upstreamServer;
    }

    public int getUpstreamPort() {
        return upstreamPort;
    }

    public DnsCache getCache() {
        return cache;
    }
}
