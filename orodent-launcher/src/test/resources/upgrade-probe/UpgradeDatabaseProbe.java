import com.orodent.ecos.database.Database;
import com.orodent.ecos.database.DatabaseConfiguration;
import com.orodent.ecos.database.DatabaseMode;
import com.orodent.ecos.database.DatabaseSchema;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

/** Test probe: execute against the actual JARs downloaded by the frozen launcher. */
class UpgradeDatabaseProbe {
    public static void main(String[] args) throws Exception {
        Path home = Path.of(args[1]);
        Files.createDirectories(home);
        if (args[0].equals("seed")) {
            System.setProperty("derby.system.home", home.toString());
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
            try (var connection = DriverManager.getConnection("jdbc:derby:OrodentEcoS;create=true", "APP", "pw");
                 var input = DatabaseSchema.class.getResourceAsStream("/database/schema.sql");
                 var statement = connection.createStatement()) {
                for (String sql : new String(input.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                    if (!sql.isBlank()) statement.executeUpdate(sql);
                }
                statement.executeUpdate("INSERT INTO APP.CLIENTI(codice_cliente,ragione_sociale) VALUES ('EXISTING','Cliente esistente')");
                statement.executeUpdate("ALTER TABLE APP.VENDITE_DETTAGLIO ALTER COLUMN vendita_id RESTART WITH 42");
                statement.executeUpdate("""
                        INSERT INTO APP.VENDITE_DETTAGLIO(sorgente,documento_id,numero_riga,data_vendita,
                            codice_cliente,codice_prodotto,quantita,importo_netto,importato_il)
                        VALUES ('TEST','OLD',1,DATE('2025-01-01'),'EXISTING','P',1,123.45,TIMESTAMP('2025-01-02 03:04:05'))
                        """);
            }
            try {
                DriverManager.getConnection("jdbc:derby:OrodentEcoS;shutdown=true", "APP", "pw");
                throw new AssertionError("Expected Derby shutdown acknowledgement");
            } catch (java.sql.SQLException stopped) {
                if (!"08006".equals(stopped.getSQLState())) throw stopped;
            }
            System.out.println("SEEDED LEGACY DATABASE");
            return;
        }
        int tcp;
        int udp;
        try (var socket = new ServerSocket(0); var discovery = new DatagramSocket(0)) {
            tcp = socket.getLocalPort();
            udp = discovery.getLocalPort();
        }
        Database database = new Database(new DatabaseConfiguration(home, tcp, udp, 800));
        try {
            database.start();
            check(database.getMode() == DatabaseMode.HOST, "The upgraded application must own the existing database");
            int expected = Integer.parseInt(args[2]);
            check(DatabaseSchema.CURRENT_VERSION == expected, "Catalog target must match the bundled application");
            database.withConnection(connection -> {
                try (var statement = connection.createStatement()) {
                    try (var rows = statement.executeQuery("SELECT ragione_sociale FROM ANAGRAFICHE.CLIENTI WHERE codice_cliente='EXISTING'")) {
                        check(rows.next() && "Cliente esistente".equals(rows.getString(1)), "Existing customer lost");
                    }
                    try (var rows = statement.executeQuery("SELECT vendita_id,importo_netto,importato_il FROM VENDITE.VENDITE_DETTAGLIO WHERE documento_id='OLD'")) {
                        check(rows.next() && rows.getLong(1) == 42, "Existing sale identifier changed");
                        check(rows.getBigDecimal(2).toPlainString().equals("123.45"), "Sale amount changed");
                        check(rows.getTimestamp(3).equals(java.sql.Timestamp.valueOf("2025-01-02 03:04:05")), "Import timestamp changed");
                    }
                    try (var rows = statement.executeQuery("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY")) {
                        check(rows.next() && rows.getInt(1) == expected, "Application did not migrate to its declared schema");
                    }
                    statement.executeUpdate("""
                            INSERT INTO VENDITE.VENDITE_DETTAGLIO(sorgente,documento_id,numero_riga,data_vendita,
                                codice_cliente,codice_prodotto,quantita) VALUES ('TEST','NEW',1,DATE('2025-01-02'),'EXISTING','P',1)
                            """);
                    try (var rows = statement.executeQuery("SELECT vendita_id FROM VENDITE.VENDITE_DETTAGLIO WHERE documento_id='NEW'")) {
                        check(rows.next(), "New sale missing");
                        check(rows.getLong(1) == 43, "Sales identity did not continue from the previous value: " + rows.getLong(1));
                    }
                }
                return null;
            });
            System.out.println("UPGRADED " + expected + " PRESERVED CUSTOMER AND SALE 42 NEXT 43");
        } finally {
            database.stop();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
