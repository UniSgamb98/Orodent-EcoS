package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;

import com.orodent.ecos.database.DatabaseConfiguration;
import java.nio.file.Path;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Runs Derby in a separate JVM, matching independent application instances. */
public final class DatabaseProcessProbe {
    public static void main(String[] args) throws Exception {
        if (args.length > 3 && args[3].equals("legacy")) {
            java.nio.file.Files.createDirectories(Path.of(args[0]));
            System.setProperty("derby.system.home", args[0]);
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getConstructor().newInstance();
            try (var connection = java.sql.DriverManager.getConnection("jdbc:derby:OrodentEcoS;create=true", "APP", "pw");
                 var input = new BufferedReader(new InputStreamReader(System.in))) {
                System.out.println("READY LEGACY");
                while (input.readLine() != null) {
                    try (var statement = connection.createStatement(); var result = statement.executeQuery("VALUES 1")) {
                        result.next();
                        System.out.println("COUNT " + result.getInt(1));
                    }
                }
            }
            return;
        }
        if (args.length > 3 && args[3].equals("warming")) {
            // Model a cold host that has reserved ownership but cannot advertise yet.
            java.nio.file.Files.createDirectories(Path.of(args[0]));
            try (var channel = java.nio.channels.FileChannel.open(Path.of(args[0]).resolve(".ton-host.lock"),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                System.out.println("STARTING HOST");
                Thread.sleep(4500);
            }
        }
        var database = new Database(new DatabaseConfiguration(Path.of(args[0]),
                Integer.parseInt(args[1]), Integer.parseInt(args[2]), 800));
        try {
            database.start();
            var sawLoss = new java.util.concurrent.atomic.AtomicBoolean();
            var recoveries = new java.util.concurrent.atomic.AtomicInteger();
            database.observeStatus(status -> {
                if (status.state() == Database.State.RECONNECTING) sawLoss.set(true);
                if (status.state() == Database.State.READY && sawLoss.compareAndSet(true, false)) recoveries.incrementAndGet();
            });
            int consumedRecoveries = 0;
            var releaseWrite = new java.util.concurrent.CountDownLatch(1);
            var writeStarted = new java.util.concurrent.CountDownLatch(1);
            var writeAttempts = new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.CompletableFuture<Void> write = null;
            System.out.println("READY " + database.getMode());
            try (var input = new BufferedReader(new InputStreamReader(System.in))) {
                for (String command; (command = input.readLine()) != null;) {
                    if (command.equals("STOP")) break;
                    if (command.equals("WAIT_RECOVERY")) {
                        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(25);
                        while (recoveries.get() <= consumedRecoveries && System.nanoTime() < deadline) Thread.sleep(30);
                        if (recoveries.get() <= consumedRecoveries) throw new AssertionError("Recovery timeout: " + database.status());
                        consumedRecoveries = recoveries.get();
                        System.out.println("RECOVERED " + database.getMode());
                    } else if (command.equals("WAIT_DISCONNECTED")) {
                        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
                        while (database.state() != Database.State.RECONNECTING && System.nanoTime() < deadline) Thread.sleep(30);
                        if (database.state() != Database.State.RECONNECTING) throw new AssertionError("Disconnection timeout: " + database.status());
                        System.out.println("DISCONNECTED");
                    } else if (command.equals("STATUS")) {
                        System.out.println("STATUS " + database.status());
                    } else if (command.equals("WAIT_IDENTITY_MISMATCH")) {
                        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
                        while (!database.status().message().contains("diverso") && System.nanoTime() < deadline) Thread.sleep(30);
                        if (!database.status().message().contains("diverso")) throw new AssertionError("Identity mismatch not detected: " + database.status());
                        System.out.println("WRONG ARCHIVE REJECTED");
                    } else if (command.equals("STOP_NETWORK")) {
                        new org.apache.derby.drda.NetworkServerControl(java.net.InetAddress.getLoopbackAddress(), Integer.parseInt(args[1])).shutdown();
                        System.out.println("NETWORK STOPPED");
                    } else if (command.equals("START_NETWORK")) {
                        new org.apache.derby.drda.NetworkServerControl(java.net.InetAddress.getByName("0.0.0.0"), Integer.parseInt(args[1])).start(null);
                        System.out.println("NETWORK STARTED");
                    } else if (command.equals("BEGIN_WRITE")) {
                        write = java.util.concurrent.CompletableFuture.runAsync(() -> database.withTransaction(connection -> {
                            writeAttempts.incrementAndGet();
                            try (var statement = connection.createStatement()) {
                                statement.executeUpdate("INSERT INTO ANAGRAFICHE.CLIENTI (codice_cliente, ragione_sociale) VALUES ('PENDING', 'Pending')");
                            }
                            writeStarted.countDown();
                            try {
                                if (!releaseWrite.await(35, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Write timeout");
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(interrupted);
                            }
                            return null;
                        }));
                        if (!writeStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Write did not start");
                        System.out.println("WRITE STARTED");
                    } else if (command.equals("FINISH_WRITE")) {
                        releaseWrite.countDown();
                        try {
                            write.get(10, java.util.concurrent.TimeUnit.SECONDS);
                            throw new AssertionError("Broken transaction unexpectedly committed");
                        } catch (java.util.concurrent.ExecutionException expected) {
                            if (!(expected.getCause() instanceof DatabaseDisconnectedException)) throw expected;
                            System.out.println("WRITE FAILED ATTEMPTS " + writeAttempts.get());
                        }
                    } else if (command.equals("INSERT") || command.equals("INSERT_SECOND")) {
                        String code = command.equals("INSERT") ? "TEST" : "SECOND";
                        database.withConnection(connection -> {
                            try (var statement = connection.prepareStatement("INSERT INTO ANAGRAFICHE.CLIENTI (codice_cliente, ragione_sociale) VALUES (?, 'Test')")) {
                                statement.setString(1, code);
                                statement.executeUpdate();
                            }
                            return null;
                        });
                        System.out.println("INSERTED");
                    } else if (command.equals("COUNT")) {
                        int count = database.withConnection(connection -> {
                            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT COUNT(*) FROM ANAGRAFICHE.CLIENTI")) {
                                result.next();
                                return result.getInt(1);
                            }
                        });
                        System.out.println("COUNT " + count);
                    } else if (command.equals("FUTURE_SCHEMA")) {
                        database.withConnection(connection -> {
                            try (var statement = connection.createStatement()) {
                                statement.executeUpdate("UPDATE ECOS.ECOS_SCHEMA_HISTORY SET version = "
                                        + (DatabaseSchema.CURRENT_VERSION + 1) + " WHERE version = " + DatabaseSchema.CURRENT_VERSION);
                            }
                            return null;
                        });
                        System.out.println("SCHEMA FUTURE");
                    } else if (command.equals("INDEPENDENT")) {
                        try (var first = database.openConnection(); var second = database.openConnection()) {
                            if (first == second) throw new AssertionError("Shared connection");
                            first.setAutoCommit(false);
                            try (var statement = first.createStatement()) {
                                statement.executeUpdate("INSERT INTO ANAGRAFICHE.CLIENTI (codice_cliente, ragione_sociale) VALUES ('ROLLBACK', 'Test')");
                            }
                            second.close();
                            first.rollback();
                        }
                        System.out.println("INDEPENDENT OK");
                    } else if (command.equals("VALIDATION_FAILURE")) {
                        try {
                            database.withTransaction(connection -> { throw new IllegalArgumentException("Invalid input"); });
                            throw new AssertionError("Validation should fail");
                        } catch (IllegalArgumentException expected) {
                            if (database.state() != Database.State.READY) throw new AssertionError("Validation disconnected database");
                            System.out.println("VALIDATION FAILED DATABASE READY");
                        }
                    }
                }
            }
            database.stop();
            System.out.println("STOPPED");
        } catch (Exception failure) {
            database.stop();
            failure.printStackTrace(System.err);
            System.out.println("FAILED " + failure.getMessage());
            System.exit(1);
        }
    }
}
