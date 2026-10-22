package com.orodent.ecos.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.File;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseDiscoveryIntegrationTest {
    @TempDir Path temporary;

    @Test
    void secondProcessUsesHostAndItsShutdownDoesNotStopHost() throws Exception {
        int tcp = tcpPort();
        int udp = udpPort();
        try (var host = start(temporary.resolve("host"), tcp, udp)) {
            assertEquals("READY HOST", host.line());
            assertTrue(Files.isDirectory(temporary.resolve("host/OrodentEcoS")));
            try (var client = start(temporary.resolve("client"), tcp, udp)) {
                assertEquals("READY CLIENT", client.line());
                assertFalse(Files.exists(temporary.resolve("client/OrodentEcoS")));
                assertEquals("INSERTED", client.command("INSERT"));
                assertEquals("COUNT 1", host.command("COUNT"));
                assertEquals("INDEPENDENT OK", client.command("INDEPENDENT"));
                assertEquals("STOPPED", client.command("STOP"));
                assertTrue(client.process.waitFor(5, TimeUnit.SECONDS));
                assertEquals(0, client.process.exitValue());
            }
            assertEquals("COUNT 1", host.command("COUNT"));
            assertEquals("STOPPED", host.command("STOP"));
        }
    }

    @Test
    void simultaneousStartupChoosesExactlyOneHost() throws Exception {
        int tcp = tcpPort();
        int udp = udpPort();
        var home = temporary.resolve("shared");
        try (var first = start(home, tcp, udp); var second = start(home, tcp, udp)) {
            String role1 = first.line();
            String role2 = second.line();
            assertTrue((role1.equals("READY HOST") && role2.equals("READY CLIENT"))
                    || (role2.equals("READY HOST") && role1.equals("READY CLIENT")), role1 + " / " + role2);
            assertEquals("INSERTED", first.command("INSERT"));
            assertEquals("COUNT 1", second.command("COUNT"));
            Probe client = role1.endsWith("CLIENT") ? first : second;
            Probe host = client == first ? second : first;
            assertEquals("STOPPED", client.command("STOP"));
            assertEquals("COUNT 1", host.command("COUNT"));
            assertEquals("STOPPED", host.command("STOP"));
        }
    }

    @Test
    void occupiedPortWithoutCompatibleHostFailsWithoutCreatingDatabase() throws Exception {
        try (var occupied = new ServerSocket(0);
             var instance = start(temporary.resolve("blocked"), occupied.getLocalPort(), udpPort())) {
            assertTrue(instance.line().startsWith("FAILED Database o porte occupati"));
            assertFalse(Files.exists(temporary.resolve("blocked/OrodentEcoS")));
            assertFalse(occupied.isClosed());
        }
    }

    @Test
    void occupiedEmbeddedDatabaseWithoutDiscoveryDoesNotShutDownItsOwner() throws Exception {
        int tcp = tcpPort();
        int udp = udpPort();
        Path home = temporary.resolve("legacy");
        try (var owner = start(home, tcp, udp, "legacy")) {
            assertEquals("READY LEGACY", owner.line());
            try (var instance = start(home, tcp, udp)) {
                assertTrue(instance.line().startsWith("FAILED Database o porte occupati"));
            }
            assertEquals("COUNT 1", owner.command("COUNT"));
        }
    }

    @Test
    void filesystemFailureIsNotReportedAsAnOccupiedDatabase() throws Exception {
        Path home = temporary.resolve("not-a-directory");
        Files.writeString(home, "test");
        try (var instance = start(home, tcpPort(), udpPort())) {
            assertEquals("FAILED Impossibile avviare il database.", instance.line());
        }
    }

    private Probe start(Path home, int tcp, int udp, String... mode) throws Exception {
        String separator = File.pathSeparator;
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"))
                + separator + System.getProperty("jdk.module.path", "");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, DatabaseProcessProbe.class.getName(), home.toString(), "" + tcp, "" + udp, mode.length == 0 ? "normal" : mode[0])
                .redirectError(temporary.resolve("stderr-" + home.getFileName() + "-" + System.nanoTime() + ".log").toFile()).start();
        return new Probe(process);
    }

    private static int tcpPort() throws Exception {
        try (var socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }

    private static int udpPort() throws Exception {
        try (var socket = new DatagramSocket(0)) { return socket.getLocalPort(); }
    }

    private static final class Probe implements AutoCloseable {
        final Process process;
        final BufferedReader output;
        final PrintWriter input;
        Probe(Process process) {
            this.process = process;
            output = new BufferedReader(new InputStreamReader(process.getInputStream()));
            input = new PrintWriter(process.getOutputStream(), true);
        }
        String line() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < deadline) {
                if (output.ready() || !process.isAlive()) {
                    String line = output.readLine();
                    if (line == null) fail("Database process exited: " + process.exitValue());
                    if (line.startsWith("READY ") || line.startsWith("FAILED ") || line.startsWith("COUNT ")
                            || line.equals("INSERTED") || line.equals("STOPPED") || line.equals("INDEPENDENT OK")) return line;
                }
                Thread.sleep(20);
            }
            throw new AssertionError("Database process response timeout");
        }
        String command(String command) throws Exception { input.println(command); return line(); }
        public void close() throws Exception {
            input.close();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            output.close();
        }
    }
}
