package com.orodent.tonv3.core.database.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/** Reserves the discovery port immediately; start advertising only after the database is ready. */
public final class DiscoveryServer implements Runnable, AutoCloseable {
    private static final System.Logger LOG = System.getLogger(DiscoveryServer.class.getName());
    private final DatagramSocket socket;
    private final int dbPort;
    private Thread thread;

    public DiscoveryServer(int discoveryPort, int dbPort) throws IOException {
        if (dbPort < 1 || dbPort > 65535) throw new IllegalArgumentException("Porta database non valida");
        this.dbPort = dbPort;
        socket = new DatagramSocket(discoveryPort, InetAddress.getByName("0.0.0.0"));
    }

    public synchronized void start() {
        if (socket.isClosed()) throw new IllegalStateException("Discovery già chiuso");
        if (thread != null) return;
        thread = new Thread(this, "ton-discovery-server");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void run() {
        while (!socket.isClosed()) {
            try {
                DatagramPacket request = new DatagramPacket(new byte[512], 512);
                socket.receive(request);
                String message = new String(request.getData(), request.getOffset(), request.getLength(), StandardCharsets.UTF_8);
                var response = DiscoveryProtocol.response(message, dbPort);
                if (response.isPresent()) {
                    byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
                    socket.send(new DatagramPacket(bytes, bytes.length, request.getAddress(), request.getPort()));
                }
            } catch (SocketException exception) {
                if (!socket.isClosed()) LOG.log(System.Logger.Level.WARNING, "Errore socket discovery", exception);
                break;
            } catch (IOException exception) {
                LOG.log(System.Logger.Level.WARNING, "Errore durante il discovery", exception);
            }
        }
    }

    @Override
    public void close() {
        socket.close(); // Also unblocks receive; safe even before start().
    }
}
