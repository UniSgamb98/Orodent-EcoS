package com.orodent.ecos.app;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("javafx")
class ApplicationFailoverSmokeTest {
    @TempDir Path temporary;

    @Test
    void showsConnectionLossAndNewHostRoleWithoutReopeningWindow() throws Exception {
        int udp;
        int hostTcp;
        int clientTcp;
        try (var socket = new DatagramSocket(0)) { udp = socket.getLocalPort(); }
        try (var first = new ServerSocket(0); var second = new ServerSocket(0)) {
            hostTcp = first.getLocalPort(); clientTcp = second.getLocalPort();
        }
        try (Peer host = start("host", hostTcp, udp)) {
            assertEquals("READY HOST", host.line());
            try (Peer client = start("client", clientTcp, udp)) {
                assertEquals("READY CLIENT", client.line());
                assertEquals("NETWORK STOPPED", host.command("STOP_NETWORK"));
                assertEquals("UI OFFLINE DISABLED", client.command("WAIT_OFFLINE"));
                assertEquals("NETWORK STARTED", host.command("START_NETWORK"));
                assertEquals("UI ONLINE CLIENT", client.command("WAIT_CLIENT"));
                host.close();
                assertEquals("UI ONLINE HOST", client.command("WAIT_HOST"));
            }
        }
    }

    private Peer start(String mode, int tcp, int udp) throws Exception {
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"))
                + java.io.File.pathSeparator + System.getProperty("jdk.module.path", "");
        Path errors = temporary.resolve(mode + ".log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, ApplicationFailoverProbe.class.getName(), mode,
                temporary.resolve("shared").toString(), "" + tcp, "" + udp).redirectError(errors.toFile()).start();
        return new Peer(process, errors);
    }

    private static final class Peer implements AutoCloseable {
        private final Process process;
        private final Path errors;
        private final BufferedReader output;
        private final PrintWriter input;

        Peer(Process process, Path errors) {
            this.process = process; this.errors = errors;
            output = new BufferedReader(new InputStreamReader(process.getInputStream()));
            input = new PrintWriter(process.getOutputStream(), true);
        }

        String command(String command) throws Exception { input.println(command); return line(); }

        String line() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
            while (System.nanoTime() < deadline) {
                if (output.ready() || !process.isAlive()) {
                    String line = output.readLine();
                    if (line == null) throw new AssertionError("Probe exited; log: " + java.nio.file.Files.readString(errors));
                    if (line.startsWith("READY ") || line.startsWith("UI ") || line.startsWith("NETWORK ")) return line;
                }
                Thread.sleep(20);
            }
            throw new AssertionError("Probe timed out; log: " + java.nio.file.Files.readString(errors));
        }

        @Override public void close() throws Exception {
            if (process.isAlive()) {
                input.println("STOP");
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS);
                }
            }
            input.close(); output.close();
        }
    }
}
