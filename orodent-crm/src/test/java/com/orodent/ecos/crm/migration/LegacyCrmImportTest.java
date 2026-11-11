package com.orodent.ecos.crm.migration;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

class LegacyCrmImportTest {
    @TempDir Path temp;
    String url;
    ConnectionProvider db;
    static final String CLIENT = "10000000-0000-0000-0000-000000000001",
            OP = "20000000-0000-0000-0000-000000000001",
            NOTE = "30000000-0000-0000-0000-000000000001",
            CALL = "40000000-0000-0000-0000-000000000001";

    @BeforeEach
    void open() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:" + temp.resolve("OrodentEcoS").toAbsolutePath();
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
    }

    @AfterEach
    void close() {
        try {
            DriverManager.getConnection(url + ";shutdown=true", "APP", "pw");
        } catch (SQLException e) {
            assertEquals("08006", e.getSQLState());
        }
    }

    Map<String, String> files(boolean broken) {
        var files = new LinkedHashMap<String, String>();
        for (String file : LegacyCrmImport.FILES) files.put(file, "");
        files.put(
                "import_operatori.sql",
                "INSERT INTO ECOS.OPERATORI(ID,USERNAME,ATTIVO) SELECT '"
                        + OP
                        + "','MARIO',1 FROM SYSIBM.SYSDUMMY1 WHERE NOT EXISTS (SELECT 1 FROM"
                        + " ECOS.OPERATORI WHERE UPPER(TRIM(USERNAME))='MARIO');");
        files.put(
                "import_clienti.sql",
                "INSERT INTO CRM.CLIENTI(ID,RAGIONE_SOCIALE,OPERATORE_ID) VALUES ('"
                        + CLIENT
                        + "','Laboratorio D''Angelo',(SELECT ID FROM ECOS.OPERATORI WHERE"
                        + " UPPER(TRIM(USERNAME))='MARIO'));");
        String text = "L''ufficio; è aperto\n-- testo originale, non commento";
        files.put(
                "import_note_interazioni.sql",
                "INSERT INTO CRM.NOTE_CLIENTE(ID,CLIENTE_ID,TESTO,CREATED_AT) VALUES ('"
                        + NOTE
                        + "','"
                        + CLIENT
                        + "',CAST('"
                        + text
                        + "' AS CLOB),TIMESTAMP('2020-01-01 00:00:00'));\n"
                        + "INSERT INTO"
                        + " CRM.INTERAZIONI(ID,CLIENTE_ID,NOTA_ID,DATA_CONTATTO,PROSSIMO_CONTATTO)"
                        + " VALUES ('"
                        + CALL
                        + "','"
                        + (broken ? UUID.randomUUID() : CLIENT)
                        + "','"
                        + NOTE
                        + "',DATE('2020-01-01'),DATE('2026-12-12'));");
        return files;
    }

    LegacyCrmImport.Dataset dataset(boolean broken) throws Exception {
        return dataset(files(broken));
    }

    LegacyCrmImport.Dataset dataset(Map<String, String> files) throws Exception {
        Path dir = Files.createDirectories(temp.resolve("prepared"));
        Properties p = new Properties();
        p.setProperty("format", "1");
        p.setProperty("source", "CRM2");
        p.setProperty("target", "OrodentEcoS");
        p.setProperty("schema.minimum", "13");
        p.setProperty("blocked", "false");
        p.setProperty("clients", "1");
        p.setProperty("operators.count", "1");
        p.setProperty("operator.1.username", "MARIO");
        p.setProperty("files.count", Integer.toString(LegacyCrmImport.FILES.size()));
        int i = 0;
        for (String name : LegacyCrmImport.FILES) {
            i++;
            byte[] content = files.get(name).getBytes(StandardCharsets.UTF_8);
            Files.write(dir.resolve(name), content);
            p.setProperty("file." + i + ".name", name);
            p.setProperty(
                    "file." + i + ".sha256",
                    HexFormat.of().formatHex(LegacyCrmImport.sha256(content)));
            p.setProperty(
                    "file." + i + ".statements",
                    Integer.toString(LegacyCrmImport.splitSql(files.get(name)).size()));
        }
        try (var out =
                Files.newBufferedWriter(
                        dir.resolve("migration.properties"), StandardCharsets.UTF_8)) {
            p.store(out, "");
        }
        return LegacyCrmImport.Dataset.load(dir);
    }

    int count(String table) {
        return db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r = q.executeQuery("SELECT COUNT(*) FROM " + table)) {
                        r.next();
                        return r.getInt(1);
                    }
                });
    }

    @Test
    void importsTheActualPythonGeneratedPackageWithHistoryContactsAndUpcomingCall()
            throws Exception {
        Path fixture =
                Path.of(getClass().getResource("/migration-fixture/migration.properties").toURI())
                        .getParent();
        var data = LegacyCrmImport.Dataset.load(fixture);
        try (Connection c = db.openConnection()) {
            LegacyCrmImport.apply(c, data, temp.resolve("generated-backup"));
        }
        assertEquals(1, count("CRM.CLIENTI"));
        assertEquals(1, count("CRM.CONTATTI_CLIENTE"));
        assertEquals(1, count("CRM.EMAIL_CLIENTE"));
        assertEquals(2, count("CRM.INTERAZIONI"));
        db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT E.CONTATTO_ID,C.ID FROM CRM.EMAIL_CLIENTE E"
                                                    + " JOIN CRM.CONTATTI_CLIENTE C ON"
                                                    + " C.CLIENTE_ID=E.CLIENTE_ID")) {
                        assertTrue(r.next());
                        assertEquals(r.getString(1), r.getString(2));
                    }
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT ESITO,PROSSIMO_CONTATTO,CREATED_AT FROM"
                                                + " CRM.INTERAZIONI ORDER BY CREATED_AT DESC")) {
                        assertTrue(r.next());
                        assertEquals("LISTINO", r.getString(1));
                        assertEquals("2026-12-12", r.getDate(2).toString());
                        assertEquals(
                                "2021-02-03",
                                r.getTimestamp(3).toLocalDateTime().toLocalDate().toString());
                    }
                    return null;
                });
    }

    @Test
    void undatedHistoryKeepsTheLastScheduledCallVisibleInTheCalendar() throws Exception {
        var source = files(false);
        String second = "40000000-0000-0000-0000-000000000002";
        source.put(
                "import_note_interazioni.sql",
                "INSERT INTO CRM.INTERAZIONI(ID,CLIENTE_ID,DATA_CONTATTO,CREATED_AT) VALUES ('"
                        + CALL
                        + "','"
                        + CLIENT
                        + "',NULL,{fn TIMESTAMPADD(SQL_TSI_SECOND, 0, CURRENT_TIMESTAMP)});INSERT"
                        + " INTO"
                        + " CRM.INTERAZIONI(ID,CLIENTE_ID,DATA_CONTATTO,PROSSIMO_CONTATTO,CREATED_AT)"
                        + " VALUES ('"
                        + second
                        + "','"
                        + CLIENT
                        + "',NULL,DATE('2026-12-12'),{fn TIMESTAMPADD(SQL_TSI_SECOND, 1,"
                        + " CURRENT_TIMESTAMP)});");
        try (Connection c = db.openConnection()) {
            LegacyCrmImport.apply(c, dataset(source), temp.resolve("backup"));
        }
        var database =
                new com.orodent.ecos.crm.core.database.Database(
                        db, new com.orodent.ecos.module.OperatorSession());
        var query =
                new com.orodent.ecos.crm.core.database.query.derby.DerbyCalendarioQuery(database);
        var calls =
                database.transaction(
                        () ->
                                query.findProssimeChiamate(
                                        java.time.LocalDate.of(2026, 12, 1),
                                        java.time.LocalDate.of(2026, 12, 31),
                                        null));
        assertEquals(1, calls.size());
        db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r =
                                    q.executeQuery(
                                            "SELECT DATA_CONTATTO,CREATED_AT FROM CRM.INTERAZIONI"
                                                + " ORDER BY CREATED_AT")) {
                        assertTrue(r.next());
                        assertNull(r.getDate(1));
                        var first = r.getTimestamp(2);
                        assertTrue(r.next());
                        assertNull(r.getDate(1));
                        assertTrue(r.getTimestamp(2).after(first));
                    }
                    return null;
                });
    }

    @Test
    void previewDoesNotWriteAndApplyBacksUpThenImportsEverything() throws Exception {
        var data = dataset(false);
        Path backup = temp.resolve("backup");
        try (Connection c = db.openConnection()) {
            var plan = LegacyCrmImport.inspect(c, data);
            assertEquals(1, plan.clients());
            assertEquals(0, plan.existingOperators());
            assertFalse(Files.exists(backup));
            assertEquals(0, count("CRM.CLIENTI"));
            var result = LegacyCrmImport.apply(c, data, backup);
            assertEquals(data.commands().size(), result.executed());
        }
        assertTrue(Files.isRegularFile(backup.resolve("OrodentEcoS/service.properties")));
        assertEquals(1, count("CRM.CLIENTI"));
        assertEquals(1, count("CRM.NOTE_CLIENTE"));
        assertEquals(1, count("CRM.INTERAZIONI"));
        db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r = q.executeQuery("SELECT TESTO FROM CRM.NOTE_CLIENTE")) {
                        r.next();
                        assertEquals(
                                "L'ufficio; è aperto\n-- testo originale, non commento",
                                r.getString(1));
                    }
                    return null;
                });
    }

    @Test
    void lastRowFailureRollsBackOperatorsClientsNotesAndInteractions() throws Exception {
        var data = dataset(true);
        try (Connection c = db.openConnection()) {
            assertThrows(
                    SQLException.class,
                    () -> LegacyCrmImport.apply(c, data, temp.resolve("backup")));
        }
        assertEquals(0, count("CRM.CLIENTI"));
        assertEquals(0, count("CRM.NOTE_CLIENTE"));
        assertEquals(0, count("CRM.INTERAZIONI"));
        assertEquals(1, count("ECOS.OPERATORI"));
    }

    @Test
    void refusesExistingCrmBeforeTakingABackup() throws Exception {
        var data = dataset(false);
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "INSERT INTO CRM.CLIENTI(ID,RAGIONE_SOCIALE) VALUES ('"
                                        + UUID.randomUUID()
                                        + "','Esistente')");
                    }
                    return null;
                });
        Path backup = temp.resolve("must-not-exist");
        try (Connection c = db.openConnection()) {
            assertThrows(SQLException.class, () -> LegacyCrmImport.apply(c, data, backup));
        }
        assertFalse(Files.exists(backup));
        assertEquals(1, count("CRM.CLIENTI"));
    }

    @Test
    void reusesExistingOperatorByNormalizedNameWithoutChangingIdentity() throws Exception {
        String id = UUID.randomUUID().toString();
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "INSERT INTO ECOS.OPERATORI(ID,USERNAME,NOME) VALUES ('"
                                        + id
                                        + "','mario','Mario Rossi')");
                    }
                    return null;
                });
        var data = dataset(false);
        try (Connection c = db.openConnection()) {
            assertEquals(1, LegacyCrmImport.inspect(c, data).existingOperators());
            LegacyCrmImport.apply(c, data, temp.resolve("backup"));
        }
        assertEquals(2, count("ECOS.OPERATORI"));
        db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r = q.executeQuery("SELECT OPERATORE_ID FROM CRM.CLIENTI")) {
                        r.next();
                        assertEquals(id, r.getString(1));
                    }
                    return null;
                });
    }

    @Test
    void backupFailurePreventsEveryInsert() throws Exception {
        var data = dataset(false);
        Path file = temp.resolve("not-a-directory");
        Files.writeString(file, "occupied");
        try (Connection c = db.openConnection()) {
            assertThrows(SQLException.class, () -> LegacyCrmImport.apply(c, data, file));
        }
        assertEquals(0, count("CRM.CLIENTI"));
        assertEquals(1, count("ECOS.OPERATORI"));
    }

    @Test
    void detectsModifiedFilesAndRejectsStatementsOutsideTheImportTables() throws Exception {
        var data = dataset(false);
        Files.writeString(data.directory().resolve("import_clienti.sql"), "changed");
        assertThrows(IOException.class, () -> LegacyCrmImport.Dataset.load(data.directory()));
        var files = files(false);
        files.put("import_note_interazioni.sql", "DELETE FROM VENDITE.VENDITE_DETTAGLIO;");
        assertThrows(IOException.class, () -> dataset(files));
    }

    @Test
    void refusesAmbiguousOperatorNames() throws Exception {
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "INSERT INTO ECOS.OPERATORI(ID,USERNAME) VALUES ('"
                                        + UUID.randomUUID()
                                        + "','mario'),('"
                                        + UUID.randomUUID()
                                        + "','MARIO')");
                    }
                    return null;
                });
        var data = dataset(false);
        try (Connection c = db.openConnection()) {
            assertThrows(SQLException.class, () -> LegacyCrmImport.inspect(c, data));
        }
        assertEquals(0, count("CRM.CLIENTI"));
    }
}
