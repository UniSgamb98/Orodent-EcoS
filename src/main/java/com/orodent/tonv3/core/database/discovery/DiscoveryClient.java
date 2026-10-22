package com.orodent.tonv3.core.database.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class DiscoveryClient {
    private final int discoveryPort;
    private final int timeoutMs;

    public DiscoveryClient(int discoveryPort, int timeoutMs) {
        if (discoveryPort < 1 || discoveryPort > 65535 || timeoutMs < 1)
            throw new IllegalArgumentException("Configurazione discovery non valida");
        this.discoveryPort = discoveryPort;
        this.timeoutMs = timeoutMs;
    }

    public Optional<HostInfo> findHost() throws IOException {
        return findHost(host -> true);
    }

    /** Continue receiving if a response is malformed or its database is not usable. */
    public Optional<HostInfo> findHost(Predicate<HostInfo> accept) throws IOException {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            sendRequests(socket, DiscoveryProtocol.request(nonce).getBytes(StandardCharsets.UTF_8));
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            Set<HostInfo> checked = new HashSet<>();
            while (!Thread.currentThread().isInterrupted()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return Optional.empty();
                socket.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
                DatagramPacket packet = new DatagramPacket(new byte[512], 512);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException timeout) {
                    return Optional.empty();
                }
                String response = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                Optional<HostInfo> host = DiscoveryProtocol.host(response, nonce, packet.getAddress());
                if (host.isPresent() && checked.add(host.get()) && accept.test(host.get())) return host;
            }
            throw new java.io.InterruptedIOException("Discovery interrotto");
        }
    }

    private void sendRequests(DatagramSocket socket, byte[] request) throws IOException {
        Set<InetAddress> addresses = new LinkedHashSet<>();
        addresses.add(InetAddress.getByName("127.0.0.1"));
        addresses.add(InetAddress.getByName("255.255.255.255"));
        var interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface network = interfaces.nextElement();
            if (!network.isUp() || network.isLoopback()) continue;
            network.getInterfaceAddresses().stream().map(address -> address.getBroadcast())
                    .filter(java.util.Objects::nonNull).forEach(addresses::add);
        }
        boolean sent = false;
        IOException lastFailure = null;
        for (InetAddress address : addresses) {
            try {
                socket.send(new DatagramPacket(request, request.length, address, discoveryPort));
                sent = true;
            } catch (IOException failure) {
                lastFailure = failure; // One unavailable interface must not prevent local discovery.
            }
        }
        if (!sent) throw lastFailure;
    }
}
