package com.orodent.ecos.misurazioni.devices;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.misurazioni.config.MeasurementSettings;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

class MeasurementDevicesTest {
    @FunctionalInterface
    interface Conversation {
        void run(Socket socket) throws Exception;
    }

    static final class InstrumentServer implements AutoCloseable {
        final ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        final CompletableFuture<Void> finished = new CompletableFuture<>();

        InstrumentServer(Conversation conversation) throws IOException {
            Thread.ofVirtual()
                    .start(
                            () -> {
                                try (Socket socket = server.accept()) {
                                    socket.setSoTimeout(4000);
                                    conversation.run(socket);
                                    finished.complete(null);
                                } catch (Throwable failure) {
                                    finished.completeExceptionally(failure);
                                }
                            });
        }

        MeasurementSettings.Endpoint endpoint() {
            return new MeasurementSettings.Endpoint("127.0.0.1", server.getLocalPort());
        }

        void verify() throws Exception {
            finished.get(5, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws IOException {
            server.close();
        }
    }

    @Test
    void keyenceUsesT1CarriageReturnAndPreservesAllColumnsForExcel() throws Exception {
        try (var server =
                new InstrumentServer(
                        socket -> {
                            var in = socket.getInputStream();
                            var out = socket.getOutputStream();
                            assertArrayEquals(new byte[] {'T', '1', '\r'}, in.readNBytes(3));
                            out.write("98.123,12.456\r\n".getBytes(StandardCharsets.US_ASCII));
                            out.flush();
                            assertArrayEquals(new byte[] {'T', '1', '\r'}, in.readNBytes(3));
                            out.write("98.200,12.500\r\n".getBytes(StandardCharsets.US_ASCII));
                            out.flush();
                            assertEquals(-1, in.read());
                        })) {
            try (var device = new KeyenceDevice(server.endpoint(), 1000)) {
                assertEquals("98,123\t12,456", device.measure());
                assertEquals("98,200\t12,500", device.measure());
            }
            server.verify();
        }
    }

    @Test
    void aMissingNewKeyenceResponseTimesOutInsteadOfReusingPreviousReading() throws Exception {
        try (var server =
                new InstrumentServer(
                        socket -> {
                            var in = socket.getInputStream();
                            assertEquals(3, in.readNBytes(3).length);
                            socket.getOutputStream()
                                    .write("98.123,12.456\r\n".getBytes(StandardCharsets.US_ASCII));
                            socket.getOutputStream().flush();
                            assertEquals(3, in.readNBytes(3).length);
                            assertEquals(-1, in.read());
                        })) {
            try (var device = new KeyenceDevice(server.endpoint(), 200)) {
                assertEquals("98,123\t12,456", device.measure());
                assertThrows(SocketTimeoutException.class, device::measure);
            }
            server.verify();
        }
    }

    @Test
    void scaleUsesTheOriginalModbusRegistersUnitAndGramConversion() throws Exception {
        try (var server = scaleServer(12345, 4)) {
            try (var device = new ScaleDevice(server.endpoint(), 1000)) {
                assertEquals("123,45", device.measure());
            }
            server.verify();
        }
        try (var server = scaleServer(-125, 3)) {
            try (var device = new ScaleDevice(server.endpoint(), 1000)) {
                assertEquals("-12,5", device.measure());
            }
            server.verify();
        }
    }

    @Test
    void invalidScalePrecisionIsAnErrorRatherThanAZeroWeight() throws Exception {
        try (var server = scaleServer(12345, 65535)) {
            try (var device = new ScaleDevice(server.endpoint(), 1000)) {
                assertThrows(IOException.class, device::measure);
            }
            server.verify();
        }
    }

    private static InstrumentServer scaleServer(int raw, int decimals) throws IOException {
        return new InstrumentServer(
                socket -> {
                    var in = new DataInputStream(socket.getInputStream());
                    var out = new DataOutputStream(socket.getOutputStream());
                    for (int address : new int[] {3, 1102}) {
                        int transaction = in.readUnsignedShort();
                        assertEquals(0, in.readUnsignedShort());
                        assertEquals(6, in.readUnsignedShort());
                        assertEquals(255, in.readUnsignedByte());
                        assertEquals(3, in.readUnsignedByte());
                        assertEquals(address, in.readUnsignedShort());
                        int count = in.readUnsignedShort();
                        assertEquals(address == 3 ? 2 : 1, count);
                        out.writeShort(transaction);
                        out.writeShort(0);
                        out.writeShort(3 + count * 2);
                        out.writeByte(255);
                        out.writeByte(3);
                        out.writeByte(count * 2);
                        if (address == 3) {
                            out.writeShort(raw >>> 16);
                            out.writeShort(raw);
                        } else out.writeShort(decimals);
                        out.flush();
                    }
                    assertEquals(-1, in.read());
                });
    }
}
