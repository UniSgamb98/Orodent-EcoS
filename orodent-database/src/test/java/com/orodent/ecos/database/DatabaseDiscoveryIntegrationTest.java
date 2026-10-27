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
    void failoverRefusesAnotherExistingDatabaseAndPreservesItsContents() throws Exception {
        Path wrong = temporary.resolve("other-archive");
        int separateTcp = tcpPort();
        int separateUdp = udpPort();
        try (var seed = start(wrong, separateTcp, separateUdp)) {
            assertEquals("READY HOST", seed.line());
            assertEquals("STOPPED", seed.command("STOP"));
        }
        int udp = udpPort();
        Path correct = temporary.resolve("correct-archive");
        try (var host = start(correct, tcpPort(), udp)) {
            assertEquals("READY HOST", host.line());
            assertEquals("INSERTED", host.command("INSERT"));
            try (var client = start(wrong, tcpPort(), udp)) {
                assertEquals("READY CLIENT", client.line());
                assertEquals("VALIDATION FAILED DATABASE READY", client.command("VALIDATION_FAILURE"));
                assertEquals("STOPPED", host.command("STOP"));
                assertEquals("WRONG ARCHIVE REJECTED", client.command("WAIT_IDENTITY_MISMATCH"));
                assertEquals("STOPPED", client.command("STOP"));
            }
        }
        try (var inspect = start(wrong, separateTcp, separateUdp)) {
            assertEquals("READY HOST", inspect.line());
            assertEquals("COUNT 0", inspect.command("COUNT"));
            assertEquals("STOPPED", inspect.command("STOP"));
        }
    }

    @Test
    void idleClientTakesOverSameArchiveAfterHostShutdown() throws Exception {
        int udp = udpPort();
        Path shared = temporary.resolve("graceful-failover");
        try (var host = start(shared, tcpPort(), udp)) {
            assertEquals("READY HOST", host.line());
            assertEquals("INSERTED", host.command("INSERT"));
            try (var client = start(shared, tcpPort(), udp)) {
                assertEquals("READY CLIENT", client.line());
                assertEquals("STOPPED", host.command("STOP"));
                assertEquals("RECOVERED HOST", client.command("WAIT_RECOVERY"));
                assertEquals("COUNT 1", client.command("COUNT"));
                assertEquals("INSERTED", client.command("INSERT_SECOND"));
                assertEquals("COUNT 2", client.command("COUNT"));
                assertEquals("STOPPED", client.command("STOP"));
            }
        }
    }

    @Test
    void hostCrashElectsOneSuccessorAndReconnectsOtherClientWithoutReplayingWrite() throws Exception {
        int udp = udpPort();
        Path shared = temporary.resolve("crash-failover");
        try (var original = start(shared, tcpPort(), udp)) {
            assertEquals("READY HOST", original.line());
            assertEquals("INSERTED", original.command("INSERT"));
            try (var first = start(shared, tcpPort(), udp); var second = start(shared, tcpPort(), udp)) {
                assertEquals("READY CLIENT", first.line());
                assertEquals("READY CLIENT", second.line());
                assertEquals("WRITE STARTED", first.command("BEGIN_WRITE"));
                original.process.destroyForcibly();
                assertTrue(original.process.waitFor(5, TimeUnit.SECONDS));
                String role1 = first.command("WAIT_RECOVERY");
                String role2 = second.command("WAIT_RECOVERY");
                assertTrue((role1.equals("RECOVERED HOST") && role2.equals("RECOVERED CLIENT"))
                        || (role2.equals("RECOVERED HOST") && role1.equals("RECOVERED CLIENT")), role1 + " / " + role2);
                assertEquals("WRITE FAILED ATTEMPTS 1", first.command("FINISH_WRITE"));
                assertEquals("COUNT 1", first.command("COUNT"));
                assertEquals("COUNT 1", second.command("COUNT"));
                Probe successor = role1.endsWith("HOST") ? first : second;
                Probe client = successor == first ? second : first;
                assertEquals("STOPPED", client.command("STOP"));
                assertEquals("STOPPED", successor.command("STOP"));
            }
        }
    }

    @Test
    void networkLossDoesNotStealArchiveFromLiveOwnerAndReconnectsWhenNetworkReturns() throws Exception {
        int udp = udpPort();
        Path shared = temporary.resolve("network-loss");
        try (var host = start(shared, tcpPort(), udp)) {
            assertEquals("READY HOST", host.line());
            assertEquals("INSERTED", host.command("INSERT"));
            try (var client = start(shared, tcpPort(), udp)) {
                assertEquals("READY CLIENT", client.line());
                assertEquals("NETWORK STOPPED", host.command("STOP_NETWORK"));
                assertEquals("DISCONNECTED", client.command("WAIT_DISCONNECTED"));
                assertEquals("COUNT 1", host.command("COUNT"));
                assertEquals("NETWORK STARTED", host.command("START_NETWORK"));
                assertEquals("RECOVERED CLIENT", client.command("WAIT_RECOVERY"));
                assertEquals("COUNT 1", client.command("COUNT"));
                assertEquals("STOPPED", client.command("STOP"));
            }
            assertEquals("STOPPED", host.command("STOP"));
        }
    }

    @Test
    void recoveryNeverCreatesAnArchiveInAnUnavailableOrDifferentHome() throws Exception {
        int tcp = tcpPort();
        int udp = udpPort();
        Path shared = temporary.resolve("real-shared");
        Path unavailable = temporary.resolve("unavailable-share");
        try (var host = start(shared, tcp, udp)) {
            assertEquals("READY HOST", host.line());
            assertEquals("INSERTED", host.command("INSERT"));
            try (var client = start(unavailable, tcpPort(), udp)) {
                assertEquals("READY CLIENT", client.line());
                assertEquals("STOPPED", host.command("STOP"));
                assertEquals("DISCONNECTED", client.command("WAIT_DISCONNECTED"));
                assertFalse(Files.exists(unavailable));
                try (var restarted = start(shared, tcp, udp)) {
                    assertEquals("READY HOST", restarted.line());
                    assertEquals("RECOVERED CLIENT", client.command("WAIT_RECOVERY"));
                    assertEquals("COUNT 1", client.command("COUNT"));
                    assertEquals("STOPPED", client.command("STOP"));
                    assertEquals("STOPPED", restarted.command("STOP"));
                }
            }
        }
    }

    @Test
    void waitsForAColdHostBeyondTheOldThreeAttemptWindow() throws Exception {
        int tcp = tcpPort();
        int udp = udpPort();
        Path shared = temporary.resolve("cold-host");
        try (var host = start(shared, tcp, udp, "warming")) {
            assertEquals("STARTING HOST", host.line());
            try (var client = start(shared, tcp, udp)) {
                assertEquals("READY HOST", host.line());
                assertEquals("READY CLIENT", client.line());
                assertEquals("INSERTED", client.command("INSERT"));
                assertEquals("COUNT 1", host.command("COUNT"));
                assertEquals("STOPPED", client.command("STOP"));
            }
            assertEquals("STOPPED", host.command("STOP"));
        }
    }

    @Test
    void separateDevelopmentAndProductionPortsKeepTheirDataIndependent() throws Exception {
        try (var development = start(temporary.resolve("development"), tcpPort(), udpPort())) {
            assertEquals("READY HOST", development.line());
            assertEquals("INSERTED", development.command("INSERT"));
            try (var production = start(temporary.resolve("production"), tcpPort(), udpPort())) {
                assertEquals("READY HOST", production.line());
                assertEquals("COUNT 0", production.command("COUNT"));
                assertEquals("COUNT 1", development.command("COUNT"));
                assertEquals("STOPPED", production.command("STOP"));
            }
            assertEquals("STOPPED", development.command("STOP"));
        }
    }

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
    void incompatibleDiscoveredHostFailsWithoutAttemptingALocalDatabase() throws Exception {
        int hostTcp = tcpPort();
        int clientTcp = tcpPort();
        while (clientTcp == hostTcp) clientTcp = tcpPort();
        int udp = udpPort();
        Path clientHome = temporary.resolve("incompatible-client");
        try (var host = start(temporary.resolve("future-host"), hostTcp, udp)) {
            assertEquals("READY HOST", host.line());
            assertEquals("SCHEMA FUTURE", host.command("FUTURE_SCHEMA"));
            try (var client = start(clientHome, clientTcp, udp)) {
                assertEquals("FAILED Database host incompatibile: Versione schema database non supportata: "
                        + (DatabaseSchema.CURRENT_VERSION + 1), client.line());
                // DerbyHost creates its home before claiming any ports, so this also forbids a local fallback attempt.
                assertFalse(Files.exists(clientHome));
                assertTrue(client.process.waitFor(5, TimeUnit.SECONDS));
                assertEquals(1, client.process.exitValue());
            }
            assertEquals("COUNT 0", host.command("COUNT"));
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
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
            while (System.nanoTime() < deadline) {
                if (output.ready() || !process.isAlive()) {
                    String line = output.readLine();
                    if (line == null) fail("Database process exited: " + process.exitValue());
                    if (line.equals("STARTING HOST") || line.startsWith("READY ") || line.startsWith("FAILED ") || line.startsWith("COUNT ")
                            || line.equals("INSERTED") || line.equals("STOPPED") || line.equals("INDEPENDENT OK")
                            || line.equals("SCHEMA FUTURE")) return line;
                    if (line.startsWith("RECOVERED ") || line.startsWith("STATUS ") || line.equals("DISCONNECTED")
                            || line.startsWith("NETWORK ") || line.equals("WRITE STARTED") || line.startsWith("WRITE FAILED")) return line;
                    if (line.equals("WRONG ARCHIVE REJECTED") || line.equals("VALIDATION FAILED DATABASE READY")) return line;
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
