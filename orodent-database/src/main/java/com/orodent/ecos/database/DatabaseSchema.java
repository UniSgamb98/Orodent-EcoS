package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Applies the shared schema once on the HOST; CLIENT checks never modify it. */
public final class DatabaseSchema {
    public static final int CURRENT_VERSION = 1;
    private static final String HISTORY = "ECOS_SCHEMA_HISTORY";
    private static final String SCHEMA_RESOURCE = "/database/schema.sql";
    private static final String OBJECT_ALREADY_EXISTS = "X0Y32";
    static final String INCOMPATIBLE_SCHEMA = "XEC01";
    private static final List<Column> CUSTOMERS = List.of(
            varchar("CODICE_CLIENTE", 50), varchar("CODICE_ISO", 10), varchar("CATEGORIA", 100),
            varchar("LISTINO", 100), varchar("AGENTE", 150), varchar("TIPO_CLIENTE", 50),
            varchar("RAGIONE_SOCIALE", 500), timestamp("IMPORTATO_IL"));
    private static final List<Column> SALES = List.of(
            identity("VENDITA_ID"), varchar("SORGENTE", 30), varchar("DOCUMENTO_ID", 100),
            varchar("NUMERO_DOCUMENTO", 50), column("NUMERO_RIGA", Types.INTEGER), column("DATA_VENDITA", Types.DATE),
            varchar("CODICE_CLIENTE", 50), varchar("CODICE_PRODOTTO", 50), varchar("DESCRIZIONE_PRODOTTO", 250),
            varchar("CATEGORIA_PRODOTTO", 100), decimal("QUANTITA", 15, 3), decimal("PREZZO_UNITARIO", 15, 4),
            decimal("SCONTO_PERCENTUALE", 7, 4), decimal("SCONTO_IMPORTO", 15, 2), decimal("IMPORTO_NETTO", 15, 2),
            varchar("TIPO_OPERAZIONE", 20), varchar("CANALE", 50), varchar("AGENTE", 150),
            timestamp("IMPORTATO_IL"));

    private final ConnectionProvider connectionProvider;

    public DatabaseSchema(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    /** Baselines pre-existing APP tables without deleting or replacing their data. */
    public void initialize() {
        String script = loadSchema();
        String checksum = checksum(script);
        connectionProvider.withTransaction(connection -> {
            boolean historyExists = tableExists(connection, HISTORY);
            boolean baselineRecorded = historyExists && verifyHistory(connection, checksum);
            if (!baselineRecorded) {
                applyBaseline(connection, script);
                verifyTables(connection);
                if (!historyExists) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("""
                                CREATE TABLE APP.ECOS_SCHEMA_HISTORY (
                                    version INTEGER PRIMARY KEY,
                                    description VARCHAR(200) NOT NULL,
                                    checksum CHAR(64) NOT NULL,
                                    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                )
                                """);
                    }
                }
                try (var statement = connection.prepareStatement(
                        "INSERT INTO APP.ECOS_SCHEMA_HISTORY(version, description, checksum) VALUES (?, ?, ?)")) {
                    statement.setInt(1, CURRENT_VERSION);
                    statement.setString(2, "Shared customers and sales baseline");
                    statement.setString(3, checksum);
                    statement.executeUpdate();
                }
            }
            verifyTables(connection);
            return null;
        });
    }

    /** Read-only compatibility probe. Legacy hosts remain readable until they register the baseline. */
    public static void verifyCompatible(Connection connection) throws SQLException {
        if (tableExists(connection, HISTORY) && !verifyHistory(connection, checksum(loadSchema())))
            throw incompatible("Schema database non inizializzato: manca la migrazione di base.");
        verifyTables(connection);
    }

    private static boolean verifyHistory(Connection connection, String expectedChecksum) throws SQLException {
        boolean baseline = false;
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(3);
            try (var rows = statement.executeQuery("SELECT version, checksum FROM APP.ECOS_SCHEMA_HISTORY ORDER BY version")) {
                while (rows.next()) {
                    if (rows.getInt("version") != CURRENT_VERSION)
                        throw incompatible("Versione schema database non supportata: " + rows.getInt("version"));
                    if (!expectedChecksum.equals(rows.getString("checksum")))
                        throw incompatible("La migrazione registrata non corrisponde alla versione dell'applicazione.");
                    baseline = true;
                }
            }
        }
        return baseline;
    }

    private static void applyBaseline(Connection connection, String script) throws SQLException {
        for (String sql : Arrays.stream(script.split(";"))
                .map(String::trim).filter(statement -> !statement.isEmpty()).toList()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            } catch (SQLException failure) {
                if (!OBJECT_ALREADY_EXISTS.equals(failure.getSQLState())) throw failure;
            }
        }
    }

    private static void verifyTables(Connection connection) throws SQLException {
        verifyColumns(connection, "CLIENTI", CUSTOMERS);
        verifyColumns(connection, "VENDITE_DETTAGLIO", SALES);
    }

    private static void verifyColumns(Connection connection, String table, List<Column> expected) throws SQLException {
        Map<String, Column> actual = new java.util.HashMap<>();
        try (var columns = connection.getMetaData().getColumns(null, "APP", table, null)) {
            while (columns.next()) {
                actual.put(columns.getString("COLUMN_NAME"), new Column(columns.getString("COLUMN_NAME"),
                        columns.getInt("DATA_TYPE"), columns.getInt("COLUMN_SIZE"), columns.getInt("DECIMAL_DIGITS"),
                        "YES".equals(columns.getString("IS_AUTOINCREMENT")), columns.getString("COLUMN_DEF")));
            }
        }
        for (Column column : expected) {
            Column existing = actual.get(column.name());
            if (existing == null || existing.type() != column.type()
                    || column.precision() > 0 && existing.precision() < column.precision()
                    || column.type() == Types.DECIMAL && existing.scale() != column.scale()
                    || column.autoIncrement() && !existing.autoIncrement()
                    || column.defaultExpression() != null
                        && !column.defaultExpression().equals(normalizeExpression(existing.defaultExpression()))) {
                throw incompatible("Schema incompatibile: APP." + table + "." + column.name());
            }
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var tables = connection.getMetaData().getTables(null, "APP", table, new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private static SQLException incompatible(String message) { return new SQLException(message, INCOMPATIBLE_SCHEMA); }
    private static Column column(String name, int type) { return new Column(name, type, 0, 0, false, null); }
    private static Column varchar(String name, int length) { return new Column(name, Types.VARCHAR, length, 0, false, null); }
    private static Column decimal(String name, int precision, int scale) {
        return new Column(name, Types.DECIMAL, precision, scale, false, null);
    }
    private static Column identity(String name) { return new Column(name, Types.BIGINT, 0, 0, true, null); }
    private static Column timestamp(String name) {
        return new Column(name, Types.TIMESTAMP, 0, 0, false, "CURRENT_TIMESTAMP");
    }
    private static String normalizeExpression(String expression) {
        return expression == null ? null : expression.trim().toUpperCase(Locale.ROOT);
    }
    private record Column(String name, int type, int precision, int scale,
                          boolean autoIncrement, String defaultExpression) { }

    private static String checksum(String script) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(script.replace("\r\n", "\n").replace('\r', '\n').getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static String loadSchema() {
        try (InputStream input = DatabaseSchema.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) throw new IllegalStateException("Risorsa non trovata: " + SCHEMA_RESOURCE);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Impossibile leggere lo schema database", failure);
        }
    }
}
