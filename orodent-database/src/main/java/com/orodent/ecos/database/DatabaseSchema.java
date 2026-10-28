package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Applies the shared schema once on the HOST; CLIENT checks never modify it. */
public final class DatabaseSchema {
    public static final int CURRENT_VERSION = currentVersion();
    private static final String HISTORY = "ECOS_SCHEMA_HISTORY";
    private static final SchemaMigration BASELINE = new SchemaMigration(1,
            "Shared customers and sales baseline", "/database/schema.sql");
    private static final SchemaMigration DOMAINS = new SchemaMigration(2,
            "Separate ecosystem, master data and sales schemas", "/database/migrations/V2__domain_schemas.sql");
    private static final SchemaMigration TON_PRODUCTS = new SchemaMigration(3,
            "TON technical product catalogue", "/database/migrations/V3__ton_products.sql");
    private static final SchemaMigration TON_DOMAIN = new SchemaMigration(4,
            "TON versioned specifications, production, firing and documents", "/database/migrations/V4__ton_domain.sql");
    private static final SchemaMigration TON_POWDER_COLORS = new SchemaMigration(5,
            "TON powder colors", "/database/migrations/V5__ton_powder_colors.sql");
    private static final SchemaMigration TON_CATALOGUES = new SchemaMigration(6,
            "TON catalogue hierarchy and history", "/database/migrations/V6__ton_catalogue_history.sql");
    private static final List<SchemaMigration> MIGRATIONS = List.of(BASELINE, DOMAINS, TON_PRODUCTS, TON_DOMAIN, TON_POWDER_COLORS, TON_CATALOGUES);
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
    private static final List<Column> PRODUCTS = List.of(
            identity("PRODOTTO_ID"), varchar("CODICE", 50), varchar("DESCRIZIONE", 200),
            column("ATTIVO", Types.BOOLEAN), column("VERSIONE_MODIFICA", Types.INTEGER),
            timestamp("CREATO_IL"), timestamp("AGGIORNATO_IL"));

    private final ConnectionProvider connectionProvider;

    private static int currentVersion() {
        try (var input = DatabaseSchema.class.getResourceAsStream("/database/version.properties")) {
            if (input == null) throw new IllegalStateException("Versione database non configurata.");
            Properties properties = new Properties();
            properties.load(input);
            int version = Integer.parseInt(properties.getProperty("current"));
            if (version < 1) throw new IllegalStateException("Versione database non valida.");
            return version;
        } catch (IOException | NumberFormatException failure) {
            throw new IllegalStateException("Impossibile leggere la versione database.", failure);
        }
    }

    public DatabaseSchema(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    /** Applies only pending immutable migrations, in a single transaction. */
    public void initialize() {
        initialize(connection -> null);
    }

    /** The HOST supplies a consistent backup; no schema writes precede this callback. */
    void initialize(ConnectionProvider.ConnectionWork<Void> beforeMigration) {
        connectionProvider.withConnection(connection -> {
            int version = existingVersion(connection);
            if (version < CURRENT_VERSION && (version > 0 || tableExists(connection, "APP", "CLIENTI")
                    || tableExists(connection, "APP", "VENDITE_DETTAGLIO"))) {
                beforeMigration.execute(connection);
            }
            return null;
        });
        connectionProvider.withTransaction(connection -> {
            connection.setSchema("APP");
            int version = existingVersion(connection);
            if (version == CURRENT_VERSION) return null;
            if (!tableExists(connection, "ECOS", HISTORY)) {
                migrateLegacy(connection);
                version = DOMAINS.version();
            }
            for (SchemaMigration migration : MIGRATIONS) {
                if (migration.version() > version) {
                    migration.apply(connection, false);
                    recordMigration(connection, "ECOS", migration);
                }
            }
            verifyCompatible(connection);
            return null;
        });
    }

    private static void migrateLegacy(Connection connection) throws SQLException {
        // A failed transaction rolls its DDL back. Pre-existing target tables need investigation,
        // rather than an implicit merge or replacement of either copy of the user's data.
        if (tableExists(connection, "ANAGRAFICHE", "CLIENTI")
                || tableExists(connection, "VENDITE", "VENDITE_DETTAGLIO")) {
            throw incompatible("Schemi di destinazione già presenti senza uno storico valido: migrazione annullata.");
        }
        if (tableExists(connection, "APP", HISTORY)) {
            verifyHistory(connection, "APP", 1);
            verifyTables(connection, "APP", "APP");
        } else {
            BASELINE.apply(connection, true);
            verifyTables(connection, "APP", "APP");
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
            recordMigration(connection, "APP", BASELINE);
        }
        DOMAINS.apply(connection, false);
        DomainSchemaMigration.copyData(connection);
        recordMigration(connection, "ECOS", DOMAINS);
        verifyTables(connection, "ANAGRAFICHE", "VENDITE");
        DomainSchemaMigration.removeLegacyTables(connection);
    }

    private static int existingVersion(Connection connection) throws SQLException {
        if (tableExists(connection, "ECOS", HISTORY)) {
            int version;
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT MAX(version) FROM ECOS." + HISTORY)) {
                rows.next();
                version = rows.getInt(1);
            }
            if (version < DOMAINS.version()) throw incompatible("Storico migrazioni incompleto o non valido.");
            verifyHistory(connection, "ECOS", version);
            verifyTables(connection, "ANAGRAFICHE", "VENDITE");
            if (version >= TON_PRODUCTS.version()) verifyColumns(connection, "TON", "PRODOTTI", PRODUCTS);
            if (version >= TON_DOMAIN.version()) TonSchema.verify(connection, version);
            return version;
        }
        if (tableExists(connection, "APP", HISTORY)) {
            verifyHistory(connection, "APP", BASELINE.version());
            verifyTables(connection, "APP", "APP");
            return BASELINE.version();
        }
        return 0;
    }

    /** CLIENT probes never baseline or migrate a remote HOST. */
    public static void verifyCompatible(Connection connection) throws SQLException {
        if (!tableExists(connection, "ECOS", HISTORY)) {
            throw incompatible("Schema database " + CURRENT_VERSION + " richiesto: aggiornare e riavviare l'HOST.");
        }
        if (existingVersion(connection) != CURRENT_VERSION)
            throw incompatible("Schema database " + CURRENT_VERSION + " richiesto: aggiornare e riavviare l'HOST.");
    }

    private static void verifyHistory(Connection connection, String schema, int expectedVersion) throws SQLException {
        int expected = 1;
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(3);
            try (var rows = statement.executeQuery("SELECT version, checksum FROM " + schema + "." + HISTORY + " ORDER BY version")) {
                while (rows.next()) {
                    int version = rows.getInt("version");
                    if (version > expectedVersion || version > CURRENT_VERSION) {
                        throw incompatible("Versione schema database non supportata: " + version);
                    }
                    if (version != expected || version < 1) {
                        throw incompatible("Storico migrazioni incompleto o non valido.");
                    }
                    if (!MIGRATIONS.get(version - 1).checksum().equals(rows.getString("checksum"))) {
                        throw incompatible("La migrazione registrata non corrisponde alla versione dell'applicazione.");
                    }
                    expected++;
                }
            }
        } catch (SQLException failure) {
            if (INCOMPATIBLE_SCHEMA.equals(failure.getSQLState())) throw failure;
            if (failure.getSQLState() != null && failure.getSQLState().startsWith("42")) {
                throw new SQLException("Struttura dello storico migrazioni non valida.", INCOMPATIBLE_SCHEMA, failure);
            }
            throw failure;
        }
        if (expected != expectedVersion + 1) throw incompatible("Storico migrazioni incompleto o non valido.");
    }

    private static void recordMigration(Connection connection, String schema, SchemaMigration migration) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO " + schema + "." + HISTORY + "(version, description, checksum) VALUES (?, ?, ?)")) {
            statement.setInt(1, migration.version());
            statement.setString(2, migration.description());
            statement.setString(3, migration.checksum());
            statement.executeUpdate();
        }
    }

    private static void verifyTables(Connection connection, String customersSchema, String salesSchema) throws SQLException {
        verifyColumns(connection, customersSchema, "CLIENTI", CUSTOMERS);
        verifyColumns(connection, salesSchema, "VENDITE_DETTAGLIO", SALES);
    }

    private static void verifyColumns(Connection connection, String schema, String table, List<Column> expected) throws SQLException {
        Map<String, Column> actual = new java.util.HashMap<>();
        try (var columns = connection.getMetaData().getColumns(null, schema, table, null)) {
            while (columns.next()) {
                actual.put(columns.getString("COLUMN_NAME"), new Column(columns.getString("COLUMN_NAME"),
                        columns.getInt("DATA_TYPE"), columns.getInt("COLUMN_SIZE"), columns.getInt("DECIMAL_DIGITS"),
                        "YES".equals(columns.getString("IS_AUTOINCREMENT")), columns.getString("COLUMN_DEF")));
            }
        }
        if ("APP".equals(schema) && actual.size() != expected.size()) {
            throw incompatible("Colonne legacy non previste in APP." + table + ": migrazione annullata per conservarne i dati.");
        }
        for (Column column : expected) {
            Column existing = actual.get(column.name());
            if (existing == null || existing.type() != column.type()
                    || column.precision() > 0 && existing.precision() < column.precision()
                    || column.type() == Types.DECIMAL && existing.scale() != column.scale()
                    || column.autoIncrement() && !existing.autoIncrement()
                    || column.defaultExpression() != null
                        && !column.defaultExpression().equals(normalizeExpression(existing.defaultExpression()))) {
                throw incompatible("Schema incompatibile: " + schema + "." + table + "." + column.name());
            }
        }
    }

    private static boolean tableExists(Connection connection, String schema, String table) throws SQLException {
        try (var tables = connection.getMetaData().getTables(null, schema, table, new String[]{"TABLE"})) {
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

}
