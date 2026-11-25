package com.orodent.ecos.database;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;

import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.UUID;

class FiringProgramSchemaTest {
    @Test
    void upgradePreservesProgramIdsStepsAndTheVersionReferencedByExistingCycles() {
        String url =
                "jdbc:derby:memory:program_upgrade_"
                        + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException failure) {
                        throw new IllegalStateException(failure);
                    }
                };
        try {
            new DatabaseSchema(db).initialize();
            db.withTransaction(
                    c -> {
                        FiringProgramFixtures.beforeV31(c);
                        try (var q = c.createStatement()) {
                            q.executeUpdate(
                                    "INSERT INTO"
                                        + " TON.PROGRAMMI_COTTURA(codice,numero_versione,nome,note,stato,approvato_il,versione_modifica)"
                                        + " VALUES ('OLD',1,'Originale','Note"
                                        + " storiche','APPROVATA',CURRENT_TIMESTAMP,4)");
                            q.executeUpdate(
                                    "INSERT INTO TON.PROGRAMMA_PASSI VALUES (1,1,1450,90,120)");
                            q.executeUpdate(
                                    "INSERT INTO TON.FORNI(codice,nome) VALUES ('F1','Forno"
                                        + " storico')");
                            q.executeUpdate(
                                    "INSERT INTO"
                                        + " TON.CICLI_COTTURA(numero,forno_id,programma_id,data_prevista)"
                                        + " VALUES ('C1',1,1,CURRENT_DATE)");
                        }
                        return null;
                    });
            new DatabaseSchema(db).initialize();
            new DatabaseSchema(db).initialize();
            db.withConnection(
                    c -> {
                        try (var q = c.createStatement();
                                var r =
                                        q.executeQuery(
                                                "SELECT"
                                                    + " p.programma_id,p.numero_versione,p.numero_revisione,p.nome,p.note,p.stato,p.famiglia_id,s.durata_rampa_minuti,s.durata_mantenimento_minuti,c.programma_id"
                                                    + " FROM TON.PROGRAMMI_COTTURA p JOIN"
                                                    + " TON.PROGRAMMA_PASSI s ON"
                                                    + " s.programma_id=p.programma_id JOIN"
                                                    + " TON.CICLI_COTTURA c ON"
                                                    + " c.programma_id=p.programma_id")) {
                            assertTrue(r.next());
                            assertEquals(1, r.getLong(1));
                            assertEquals(1, r.getInt(2));
                            assertEquals(4, r.getInt(3));
                            assertEquals("Originale", r.getString(4));
                            assertEquals("Note storiche", r.getString(5));
                            assertEquals("APPROVATA", r.getString(6));
                            assertTrue(r.getLong(7) > 0);
                            assertEquals(90, r.getInt(8));
                            assertEquals(120, r.getInt(9));
                            assertEquals(1, r.getLong(10));
                            assertFalse(r.next());
                        }
                        return null;
                    });
        } finally {
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException expected) {
                assertEquals("08006", expected.getSQLState());
            }
        }
    }
}
