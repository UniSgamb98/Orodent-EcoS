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
        var database = new Database(new DatabaseConfiguration(Path.of(args[0]),
                Integer.parseInt(args[1]), Integer.parseInt(args[2]), 800));
        try {
            database.start();
            System.out.println("READY " + database.getMode());
            try (var input = new BufferedReader(new InputStreamReader(System.in))) {
                for (String command; (command = input.readLine()) != null;) {
                    if (command.equals("STOP")) break;
                    if (command.equals("INSERT")) {
                        database.withConnection(connection -> {
                            try (var statement = connection.createStatement()) {
                                statement.executeUpdate("INSERT INTO APP.clienti (codice_cliente, ragione_sociale) VALUES ('TEST', 'Test')");
                            }
                            return null;
                        });
                        System.out.println("INSERTED");
                    } else if (command.equals("COUNT")) {
                        int count = database.withConnection(connection -> {
                            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT COUNT(*) FROM APP.clienti")) {
                                result.next();
                                return result.getInt(1);
                            }
                        });
                        System.out.println("COUNT " + count);
                    } else if (command.equals("INDEPENDENT")) {
                        try (var first = database.openConnection(); var second = database.openConnection()) {
                            if (first == second) throw new AssertionError("Shared connection");
                            first.setAutoCommit(false);
                            try (var statement = first.createStatement()) {
                                statement.executeUpdate("INSERT INTO APP.clienti (codice_cliente, ragione_sociale) VALUES ('ROLLBACK', 'Test')");
                            }
                            second.close();
                            first.rollback();
                        }
                        System.out.println("INDEPENDENT OK");
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
