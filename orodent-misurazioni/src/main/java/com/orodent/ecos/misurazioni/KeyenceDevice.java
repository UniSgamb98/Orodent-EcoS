package com.orodent.ecos.misurazioni;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** The same T1/CR TCP protocol as Marzia, waiting for a fresh response. */
final class KeyenceDevice implements MeasurementDevice {
    private final Socket socket = new Socket();
    private final Object readings = new Object();
    private final BufferedWriter writer;
    private final int timeoutMs;
    private long sequence;
    private String latest;
    private IOException failure;

    KeyenceDevice(MeasurementSettings.Endpoint endpoint, int timeoutMs) throws IOException {
        this.timeoutMs = timeoutMs;
        try {
            socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), timeoutMs);
            writer =
                    new BufferedWriter(
                            new OutputStreamWriter(
                                    socket.getOutputStream(), StandardCharsets.US_ASCII));
            var reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    socket.getInputStream(), StandardCharsets.US_ASCII));
            Thread.ofVirtual().name("misurazioni-keyence-reader").start(() -> receive(reader));
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    private void receive(BufferedReader reader) {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                synchronized (readings) {
                    latest = line;
                    sequence++;
                    readings.notifyAll();
                }
            }
            throw new EOFException("Micrometro disconnesso.");
        } catch (IOException e) {
            synchronized (readings) {
                failure = e;
                readings.notifyAll();
            }
        }
    }

    @Override
    public synchronized String measure() throws IOException, InterruptedException {
        synchronized (readings) {
            if (failure != null) throw failure;
            long previous = sequence;
            writer.write("T1\r");
            writer.flush();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            while (sequence == previous && failure == null) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0)
                    throw new SocketTimeoutException(
                            "Il micrometro non ha inviato una nuova misura.");
                TimeUnit.NANOSECONDS.timedWait(readings, remaining);
            }
            if (failure != null) throw failure;
            return latest.strip().replace(",", "\t").replace(".", ",");
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
