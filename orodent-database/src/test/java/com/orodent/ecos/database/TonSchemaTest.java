package com.orodent.ecos.database;

import com.orodent.ecos.core.ConnectionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises TON migrations against real Derby, including relationships no screen can bypass. */
class TonSchemaTest {
    private String url;
    private String previousLog;
    private ConnectionProvider connections;

    @BeforeEach
    void open() throws Exception {
        previousLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/ton-schema-derby.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:ton_schema_" + UUID.randomUUID().toString().replace("-", "");
        connections = () -> {
            try { return DriverManager.getConnection(url + ";create=true", "APP", "pw"); }
            catch (SQLException failure) { throw new IllegalStateException(failure); }
        };
    }

    @AfterEach
    void close() {
        try { DriverManager.getConnection(url + ";drop=true", "APP", "pw"); }
        catch (SQLException expected) { assertEquals("08006", expected.getSQLState()); }
        finally {
            if (previousLog == null) System.clearProperty("derby.stream.error.file");
            else System.setProperty("derby.stream.error.file", previousLog);
        }
    }

    @Test
    void upgradesVersionFourWithoutGuessingColorsOrChangingHistoricalPowderData() {
        SchemaV4Fixture.create(connections);
        execute("INSERT INTO TON.POLVERI(codice,nome,ordine_visualizzazione,attivo,versione_modifica) VALUES ('OLD','Da conservare',7,FALSE,9)",
                "INSERT INTO TON.POLVERI_REVISIONI(polvere_id,numero_versione,stato,approvato_il) VALUES (1,1,'APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.POLVERE_OSSIDI VALUES (1,'Y2O3',5.150)",
                "INSERT INTO TON.PRODOTTI(codice) VALUES ('KEEP')",
                "INSERT INTO TON.COMPOSIZIONI(prodotto_id,numero_versione) VALUES (1,1)",
                "INSERT INTO TON.COMPOSIZIONE_STRATI VALUES (1,1,'Strato unico')",
                "INSERT INTO TON.COMPOSIZIONE_INGREDIENTI VALUES (1,1,1,100)");
        var identity = connections.withConnection(DatabaseIdentity::initialize);
        var revisions = rows("SELECT * FROM TON.POLVERI_REVISIONI");
        var oxides = rows("SELECT * FROM TON.POLVERE_OSSIDI");
        var ingredients = rows("SELECT * FROM TON.COMPOSIZIONE_INGREDIENTI");
        var history = rows("SELECT * FROM ECOS.ECOS_SCHEMA_HISTORY ORDER BY version");
        var backups = new AtomicInteger();
        new DatabaseSchema(connections).initialize(connection -> {
            assertEquals(4, scalar("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
            backups.incrementAndGet();
            return null;
        });
        new DatabaseSchema(connections).initialize(connection -> { fail("Nessun secondo backup"); return null; });
        assertEquals(1, backups.get());
        assertEquals(identity, connections.withConnection(DatabaseIdentity::read));
        assertEquals(history, rows("SELECT * FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version<=4 ORDER BY version"));
        assertEquals(revisions, rows("SELECT * FROM TON.POLVERI_REVISIONI"));
        assertEquals(oxides, rows("SELECT * FROM TON.POLVERE_OSSIDI"));
        assertEquals(ingredients, rows("SELECT * FROM TON.COMPOSIZIONE_INGREDIENTI"));
        assertEquals(List.of(List.of("OLD", "Da conservare", 7, false, 9)),
                rows("SELECT codice,nome,ordine_visualizzazione,attivo,versione_modifica FROM TON.POLVERI"));
        assertEquals(1, scalar("SELECT COUNT(*) FROM TON.POLVERI WHERE colore IS NULL"));
        execute("UPDATE TON.POLVERI SET colore='ROSA'");
        reject("23513", "UPDATE TON.POLVERI SET colore='BLU'");
        assertEquals(List.of(List.of("ROSA")), rows("SELECT colore FROM TON.POLVERI"));
    }

    @Test
    void versionFiveClientRejectsV4WithoutWritingAndChecksTheColorContract() {
        SchemaV4Fixture.create(connections);
        connections.withConnection(connection -> {
            connection.setReadOnly(true);
            SQLException failure = assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(connection));
            assertEquals("XEC01", failure.getSQLState());
            return null;
        });
        assertEquals(4, scalar("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
        migrate();
        connections.withConnection(connection -> {
            connection.setReadOnly(true);
            DatabaseSchema.verifyCompatible(connection);
            return null;
        });
        execute("ALTER TABLE TON.POLVERI DROP CONSTRAINT ck_ton_pol_colore");
        connections.withConnection(connection -> {
            assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(connection));
            return null;
        });
    }

    @Test
    void aFailedColorConstraintRollsBackTheAddedColumnAndKeepsV4Usable() {
        SchemaV4Fixture.create(connections);
        execute("INSERT INTO TON.POLVERI(codice,nome) VALUES ('KEEP','Da conservare')",
                "ALTER TABLE TON.POLVERI ADD CONSTRAINT ck_ton_pol_colore CHECK (ordine_visualizzazione>=0)");
        assertThrows(RuntimeException.class, this::migrate);
        assertEquals(4, scalar("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
        assertEquals(List.of(List.of("KEEP")), rows("SELECT codice FROM TON.POLVERI"));
        connections.withConnection(connection -> {
            try (var columns = connection.getMetaData().getColumns(null, "TON", "POLVERI", "COLORE")) {
                assertFalse(columns.next(), "Il DDL della migrazione fallita deve essere annullato");
            }
            return null;
        });
        execute("ALTER TABLE TON.POLVERI DROP CONSTRAINT ck_ton_pol_colore");
        migrate();
        assertEquals(DatabaseSchema.CURRENT_VERSION, scalar("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
    }

    @Test
    void upgradesVersionThreeOnceAndPreservesProductsSalesHistoryAndIdentity() {
        SchemaV3Fixture.create(connections);
        execute("ALTER TABLE TON.PRODOTTI ALTER COLUMN prodotto_id RESTART WITH 42",
                "INSERT INTO TON.PRODOTTI(codice,descrizione,attivo,versione_modifica) VALUES ('OLD','Già presente',FALSE,7)",
                "ALTER TABLE TON.PRODOTTI ALTER COLUMN prodotto_id RESTART WITH 100",
                "INSERT INTO ANAGRAFICHE.CLIENTI(codice_cliente,ragione_sociale) VALUES ('KEEP','Cliente da conservare')",
                "INSERT INTO VENDITE.VENDITE_DETTAGLIO(sorgente,documento_id,numero_riga,data_vendita,codice_cliente,codice_prodotto,quantita) "
                        + "VALUES ('TEST','KEEP',1,DATE('2026-01-01'),'KEEP','OLD',2)");
        var identity = connections.withConnection(DatabaseIdentity::initialize);
        var products = rows("SELECT prodotto_id,codice,descrizione,attivo,versione_modifica,creato_il,aggiornato_il FROM TON.PRODOTTI");
        var sales = rows("SELECT * FROM VENDITE.VENDITE_DETTAGLIO");
        var history = rows("SELECT * FROM ECOS.ECOS_SCHEMA_HISTORY ORDER BY version");
        var backups = new AtomicInteger();
        new DatabaseSchema(connections).initialize(connection -> {
            assertTrue(connection.getAutoCommit());
            assertEquals(3, scalar("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
            assertEquals(1, tonTableCount());
            backups.incrementAndGet();
            return null;
        });
        new DatabaseSchema(connections).initialize(connection -> { fail("Nessun backup al riavvio ordinario"); return null; });
        assertEquals(1, backups.get());
        assertEquals(products, rows("SELECT prodotto_id,codice,descrizione,attivo,versione_modifica,creato_il,aggiornato_il FROM TON.PRODOTTI"));
        assertEquals(sales, rows("SELECT * FROM VENDITE.VENDITE_DETTAGLIO"));
        assertEquals(history, rows("SELECT * FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version <= 3 ORDER BY version"));
        assertEquals(identity, connections.withConnection(DatabaseIdentity::read));
        assertEquals(28, tonTableCount());
        execute("INSERT INTO TON.PRODOTTI(codice) VALUES ('NEXT')");
        assertEquals(100, scalar("SELECT prodotto_id FROM TON.PRODOTTI WHERE codice='NEXT'"));
    }

    @Test
    void clientRejectsV3WithoutMigratingAndAcceptsV4OnAReadOnlyConnection() {
        SchemaV3Fixture.create(connections);
        connections.withConnection(connection -> {
            connection.setReadOnly(true);
            SQLException failure = assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(connection));
            assertEquals("XEC01", failure.getSQLState());
            assertTrue(failure.getMessage().contains("aggiornare e riavviare l'HOST"));
            return null;
        });
        assertEquals(1, tonTableCount());
        migrate();
        connections.withConnection(connection -> {
            connection.setReadOnly(true);
            DatabaseSchema.verifyCompatible(connection);
            return null;
        });
    }

    @Test
    void aLateCollisionRollsBackAllEarlierV4DdlWithoutAdoptingUntrackedTables() {
        SchemaV3Fixture.create(connections);
        execute("INSERT INTO TON.PRODOTTI(codice) VALUES ('KEEP')",
                "CREATE TABLE TON.DOCUMENTI(note VARCHAR(50))", "INSERT INTO TON.DOCUMENTI VALUES ('Da conservare')");
        assertThrows(RuntimeException.class, this::migrate);
        assertEquals(List.of(List.of("Da conservare")), rows("SELECT note FROM TON.DOCUMENTI"));
        assertEquals(List.of(List.of("KEEP")), rows("SELECT codice FROM TON.PRODOTTI"));
        assertEquals(3, scalar("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY"));
        assertEquals(2, tonTableCount());
        execute("DROP TABLE TON.DOCUMENTI");
        migrate();
        assertEquals(28, tonTableCount());
    }

    @Test
    void persistsProductionProvenanceOutcomesAndOriginalDocumentsAcrossNewTechnicalVersions() {
        seedWorkflow();
        execute("INSERT INTO TON.SCARTI_PRODUZIONE(produzione_riga_id,quantita,motivo) VALUES (1,5,'Difetto prima del forno')",
                "INSERT INTO TON.LOTTI_COTTURA(codice,ciclo_id) VALUES ('L-001',1)",
                "INSERT INTO TON.CARICHI_COTTURA(ciclo_id,produzione_riga_id,quantita_caricata,quantita_buona,quantita_scartata,lotto_id) VALUES (1,1,60,58,2,1)",
                "INSERT INTO TON.MODELLI_DOCUMENTO(codice,numero_versione,nome,tipo,contenuto_modello,stato,approvato_il) "
                        + "VALUES ('SCHEDA',1,'Scheda produzione','PRODUZIONE','<h1>{{numero}}</h1>','APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.DOCUMENTI(modello_documento_id,tipo,produzione_id,contenuto_emesso,tipo_contenuto) "
                        + "VALUES (1,'PRODUZIONE',1,'<h1>PR-001 — ZR A2</h1>','text/html; charset=utf-8')");
        assertEquals(35, scalar("""
                SELECT r.quantita_prodotta
                     - (SELECT SUM(s.quantita) FROM TON.SCARTI_PRODUZIONE s WHERE s.produzione_riga_id=r.produzione_riga_id)
                     - (SELECT SUM(c.quantita_caricata) FROM TON.CARICHI_COTTURA c WHERE c.produzione_riga_id=r.produzione_riga_id)
                FROM TON.PRODUZIONE_RIGHE r WHERE r.produzione_riga_id=1
                """));
        execute("INSERT INTO TON.COMPOSIZIONI(prodotto_id,numero_versione,stato,approvato_il) VALUES (1,4,'APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.COMPOSIZIONE_STRATI VALUES (2,1,'Strato unico')",
                "INSERT INTO TON.COMPOSIZIONE_INGREDIENTI VALUES (2,1,1,100)",
                "INSERT INTO TON.CONFIGURAZIONI_ARTICOLO(articolo_id,prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo,stato,approvato_il) "
                        + "VALUES (1,1,2,2,1,'dosi-v1','APPROVATA',CURRENT_TIMESTAMP)",
                "UPDATE TON.ARTICOLI SET configurazione_predefinita_id=2 WHERE articolo_id=1");
        assertEquals(List.of(List.of("ZR A2", 3, 2, 58, "L-001")), rows("""
                SELECT p.codice, comp.numero_versione, m.numero_versione, car.quantita_buona, lot.codice
                FROM TON.CARICHI_COTTURA car
                JOIN TON.LOTTI_COTTURA lot ON lot.lotto_id=car.lotto_id
                JOIN TON.PRODUZIONE_RIGHE r ON r.produzione_riga_id=car.produzione_riga_id
                JOIN TON.CONFIGURAZIONI_ARTICOLO cfg ON cfg.configurazione_id=r.configurazione_id
                JOIN TON.PRODOTTI p ON p.prodotto_id=cfg.prodotto_id
                JOIN TON.COMPOSIZIONI comp ON comp.composizione_id=cfg.composizione_id
                JOIN TON.MODELLI_DISCO m ON m.modello_id=cfg.modello_id
                """));
        connections.withConnection(connection -> {
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT contenuto_emesso FROM TON.DOCUMENTI")) {
                assertTrue(rows.next()); assertEquals("<h1>PR-001 — ZR A2</h1>", rows.getString(1));
            }
            return null;
        });
    }

    @Test
    void rejectsAnArticleAndCompositionFromDifferentProducts() {
        seedWorkflow();
        execute("INSERT INTO TON.PRODOTTI(codice) VALUES ('OTHER')",
                "INSERT INTO TON.COMPOSIZIONI(prodotto_id,numero_versione) VALUES (2,1)");
        reject("23503", "INSERT INTO TON.CONFIGURAZIONI_ARTICOLO(articolo_id,prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo) "
                + "VALUES (1,1,2,2,1,'dosi-v1')");
        reject("23503", "INSERT INTO TON.CONFIGURAZIONI_ARTICOLO(articolo_id,prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo) "
                + "VALUES (1,2,2,2,1,'dosi-v1')");
        assertEquals(1, scalar("SELECT COUNT(*) FROM TON.CONFIGURAZIONI_ARTICOLO"));
    }

    @Test
    void defaultConfigurationMustBelongToTheSameArticle() {
        seedWorkflow();
        execute("INSERT INTO TON.ARTICOLI(codice,prodotto_id,diametro_mm,altezza_mm) VALUES ('ZR-A2-D98-H16',1,98,16)");
        reject("23503", "UPDATE TON.ARTICOLI SET configurazione_predefinita_id=1 WHERE articolo_id=2");
        execute("UPDATE TON.ARTICOLI SET configurazione_predefinita_id=1 WHERE articolo_id=1");
        assertEquals(1, scalar("SELECT configurazione_predefinita_id FROM TON.ARTICOLI WHERE articolo_id=1"));
    }

    @Test
    void ingredientsRequireAnExistingLayerAndKeepExactDecimalPercentages() {
        seedWorkflow();
        reject("23503", "INSERT INTO TON.COMPOSIZIONE_INGREDIENTI VALUES (1,99,1,50)");
        reject("23513", "UPDATE TON.COMPOSIZIONE_INGREDIENTI SET percentuale_massa=100.0001");
        reject("23513", "UPDATE TON.COMPOSIZIONE_INGREDIENTI SET percentuale_massa=0");
        execute("UPDATE TON.COMPOSIZIONE_INGREDIENTI SET percentuale_massa=33.3333");
        assertEquals(List.of(List.of(new BigDecimal("33.3333"))), rows("SELECT percentuale_massa FROM TON.COMPOSIZIONE_INGREDIENTI"));
        reject("23505", "INSERT INTO TON.COMPOSIZIONE_INGREDIENTI VALUES (1,1,1,20)");
    }

    @Test
    void aLoadCannotReceiveALotFromAnotherCycle() {
        seedWorkflow();
        execute("INSERT INTO TON.CICLI_COTTURA(numero,forno_id,data_prevista) VALUES ('C-002',1,CURRENT_DATE)",
                "INSERT INTO TON.LOTTI_COTTURA(codice,ciclo_id) VALUES ('L-OTHER',2)");
        reject("23503", "INSERT INTO TON.CARICHI_COTTURA(ciclo_id,produzione_riga_id,quantita_caricata,lotto_id) VALUES (1,1,10,1)");
        execute("INSERT INTO TON.CARICHI_COTTURA(ciclo_id,produzione_riga_id,quantita_caricata,lotto_id) VALUES (2,1,10,1)");
    }

    @Test
    void outcomesMustBeCompleteNonnegativeAndAccountForEveryLoadedPiece() {
        seedWorkflow();
        execute("INSERT INTO TON.CARICHI_COTTURA(ciclo_id,produzione_riga_id,quantita_caricata) VALUES (1,1,60)");
        reject("23513", "UPDATE TON.CARICHI_COTTURA SET quantita_buona=58");
        reject("23513", "UPDATE TON.CARICHI_COTTURA SET quantita_buona=58,quantita_scartata=3");
        reject("23513", "UPDATE TON.CARICHI_COTTURA SET quantita_buona=-1,quantita_scartata=61");
        reject("23513", "UPDATE TON.CARICHI_COTTURA SET quantita_caricata=2147483647,quantita_buona=2147483647,quantita_scartata=1");
        execute("UPDATE TON.CARICHI_COTTURA SET quantita_buona=58,quantita_scartata=2");
    }

    @Test
    void documentsHaveExactlyOneOriginAndTheTemplateMustMatchItsKind() {
        seedWorkflow();
        execute("INSERT INTO TON.MODELLI_DOCUMENTO(codice,numero_versione,nome,tipo,contenuto_modello) VALUES ('COTTURA',1,'Scheda forno','COTTURA','<html/>')");
        reject("23513", "INSERT INTO TON.DOCUMENTI(modello_documento_id,tipo,contenuto_emesso,tipo_contenuto) VALUES (1,'COTTURA','x','text/html')");
        reject("23513", "INSERT INTO TON.DOCUMENTI(modello_documento_id,tipo,produzione_id,ciclo_id,contenuto_emesso,tipo_contenuto) VALUES (1,'COTTURA',1,1,'x','text/html')");
        reject("23503", "INSERT INTO TON.DOCUMENTI(modello_documento_id,tipo,produzione_id,contenuto_emesso,tipo_contenuto) VALUES (1,'PRODUZIONE',1,'x','text/html')");
        execute("INSERT INTO TON.DOCUMENTI(modello_documento_id,tipo,ciclo_id,contenuto_emesso,tipo_contenuto) VALUES (1,'COTTURA',1,'x','text/html')");
        assertEquals(1, scalar("SELECT COUNT(*) FROM TON.DOCUMENTI"));
    }

    @Test
    void technicalAndOperationalStatesRequireTheirCorrespondingTimestamps() {
        seedWorkflow();
        reject("23513", "UPDATE TON.COMPOSIZIONI SET stato='BOZZA'");
        reject("23513", "UPDATE TON.POLVERI_REVISIONI SET stato='APPROVATA',approvato_il=NULL");
        reject("23513", "UPDATE TON.PRODUZIONI SET stato='ANNULLATA'");
        reject("23513", "UPDATE TON.CICLI_COTTURA SET stato='IN_CORSO'");
        execute("UPDATE TON.CICLI_COTTURA SET stato='IN_CORSO',iniziato_il=CURRENT_TIMESTAMP");
        reject("23513", "UPDATE TON.CICLI_COTTURA SET stato='ANNULLATA',annullato_il=CURRENT_TIMESTAMP,motivo_annullamento='Interrotta'");
        execute("UPDATE TON.CICLI_COTTURA SET stato='COMPLETATA',terminato_il=CURRENT_TIMESTAMP");
    }

    @Test
    void preventsDuplicateVersionsInvalidMeasuresAndSilentCascadingDeletes() {
        seedWorkflow();
        reject("23505", "INSERT INTO TON.COMPOSIZIONI(prodotto_id,numero_versione) VALUES (1,3)");
        reject("23513", "INSERT INTO TON.ARTICOLI(codice,prodotto_id,diametro_mm,altezza_mm) VALUES ('INVALID',1,98,0)");
        reject("23513", "INSERT INTO TON.MODELLO_SOVRAMATERIALI(modello_id,altezza_da_mm,altezza_a_mm,superiore_mm,inferiore_mm) VALUES (1,20,10,0,0)");
        execute("INSERT INTO TON.CARICHI_COTTURA(ciclo_id,produzione_riga_id,quantita_caricata) VALUES (1,1,10)");
        reject("23503", "DELETE FROM TON.PRODUZIONE_RIGHE WHERE produzione_riga_id=1");
        assertEquals(1, scalar("SELECT COUNT(*) FROM TON.CARICHI_COTTURA"));
    }

    @Test
    void rejectsRemovedColumnsOrForeignKeysEvenWithAnUntouchedMigrationHistory() {
        migrate();
        execute("ALTER TABLE TON.COMPOSIZIONE_INGREDIENTI DROP CONSTRAINT fk_ton_ci_polvere");
        assertIncompatible("FK_TON_CI_POLVERE");
        execute("ALTER TABLE TON.COMPOSIZIONE_INGREDIENTI ADD CONSTRAINT fk_ton_ci_polvere "
                + "FOREIGN KEY (polvere_revisione_id) REFERENCES TON.POLVERI_REVISIONI(polvere_revisione_id)",
                "ALTER TABLE TON.DOCUMENTI DROP COLUMN contenuto_emesso");
        assertIncompatible("CONTENUTO_EMESSO");
    }

    @Test
    void rejectsAlteredNullabilityEvenWhenAllColumnNamesArePresent() {
        migrate();
        execute("ALTER TABLE TON.LINEE ALTER COLUMN nome NULL");
        assertIncompatible("NOME");
    }

    private void seedWorkflow() {
        migrate();
        execute("INSERT INTO TON.PRODOTTI(codice) VALUES ('ZR A2')",
                "INSERT INTO TON.LINEE(codice,nome) VALUES ('ML','Multistrato')",
                "INSERT INTO TON.LINEE_PRODOTTI VALUES (1,1)",
                "INSERT INTO TON.ARTICOLI(codice,prodotto_id,diametro_mm,altezza_mm) VALUES ('ZR-A2-D98-H14',1,98,14)",
                "INSERT INTO TON.POLVERI(codice,nome) VALUES ('BIANCA','Polvere bianca')",
                "INSERT INTO TON.POLVERI_REVISIONI(polvere_id,numero_versione,resistenza_mpa,stato,approvato_il) VALUES (1,1,1200,'APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.POLVERE_OSSIDI VALUES (1,'ZRO2',95)",
                "INSERT INTO TON.COMPOSIZIONI(prodotto_id,numero_versione,stato,approvato_il) VALUES (1,3,'APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.COMPOSIZIONE_STRATI VALUES (1,1,'Strato unico')",
                "INSERT INTO TON.COMPOSIZIONE_INGREDIENTI VALUES (1,1,1,100)",
                "INSERT INTO TON.MODELLI_DISCO(codice,numero_versione,diametro_pressatura_mm,sovramateriale_superiore_mm,sovramateriale_inferiore_mm,pressione_kg_cm2,grammi_per_mm,stato,approvato_il) "
                        + "VALUES ('ML-98',2,98,1,1,1000,20,'APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.MODELLO_STRATI VALUES (1,1,100)",
                "INSERT INTO TON.MODELLO_SOVRAMATERIALI(modello_id,altezza_da_mm,altezza_a_mm,superiore_mm,inferiore_mm) VALUES (1,10,20,1,1)",
                "INSERT INTO TON.CONFIGURAZIONI_ARTICOLO(articolo_id,prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo,stato,approvato_il) "
                        + "VALUES (1,1,1,1,1,'dosi-v1','APPROVATA',CURRENT_TIMESTAMP)",
                "UPDATE TON.ARTICOLI SET configurazione_predefinita_id=1",
                "INSERT INTO TON.PRODUZIONI(numero,linea_id,data_produzione,stato,confermata_il) VALUES ('PR-001',1,CURRENT_DATE,'CONFERMATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.PRODUZIONE_RIGHE(produzione_id,numero_riga,configurazione_id,quantita_prodotta,dosi_calcolate) VALUES (1,1,1,100,'{\"version\":1,\"unit\":\"g\",\"doses\":[320]}')",
                "INSERT INTO TON.FORNI(codice,nome) VALUES ('F01','Forno 1')",
                "INSERT INTO TON.PROGRAMMI_COTTURA(codice,numero_versione,nome,stato,approvato_il) VALUES ('PRE-A',2,'Presinterizzazione','APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.PROGRAMMA_PASSI VALUES (1,1,1000,120,30)",
                "INSERT INTO TON.CICLI_COTTURA(numero,forno_id,programma_id,data_prevista) VALUES ('C-001',1,1,CURRENT_DATE)");
    }

    private void migrate() { new DatabaseSchema(connections).initialize(); }

    private void assertIncompatible(String member) {
        connections.withConnection(connection -> {
            connection.setReadOnly(true);
            var failure = assertThrows(SQLException.class, () -> DatabaseSchema.verifyCompatible(connection));
            assertEquals("XEC01", failure.getSQLState());
            assertTrue(failure.getMessage().contains(member), failure::getMessage);
            return null;
        });
        assertThrows(RuntimeException.class, this::migrate);
    }

    private void reject(String state, String sql) {
        connections.withConnection(connection -> {
            try (var statement = connection.createStatement()) {
                var failure = assertThrows(SQLException.class, () -> statement.executeUpdate(sql), sql);
                assertEquals(state, failure.getSQLState(), failure::getMessage);
            }
            return null;
        });
    }

    private int tonTableCount() {
        return scalar("SELECT COUNT(*) FROM sys.systables t JOIN sys.sysschemas s ON t.schemaid=s.schemaid "
                + "WHERE s.schemaname='TON' AND t.tabletype='T'");
    }

    private int scalar(String sql) { return ((Number) rows(sql).getFirst().getFirst()).intValue(); }

    private void execute(String... statements) {
        connections.withTransaction(connection -> {
            try (var statement = connection.createStatement()) {
                for (String sql : statements) statement.executeUpdate(sql);
            }
            return null;
        });
    }

    private List<List<Object>> rows(String sql) {
        return connections.withConnection(connection -> {
            try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
                List<List<Object>> rows = new ArrayList<>();
                while (result.next()) {
                    List<Object> values = new ArrayList<>();
                    for (int column = 1; column <= result.getMetaData().getColumnCount(); column++) values.add(result.getObject(column));
                    rows.add(values);
                }
                return rows;
            }
        });
    }
}
