package com.orodent.ecos.database;

import java.sql.*;

final class FiringProgramFixtures {
    static void beforeV31(Connection c) throws SQLException {
        beforeV32(c);
        try (var q = c.createStatement()) {
            q.executeUpdate(
                    "ALTER TABLE TON.PROGRAMMI_COTTURA DROP CONSTRAINT fk_ton_programma_famiglia");
            q.executeUpdate(
                    "ALTER TABLE TON.PROGRAMMI_COTTURA DROP CONSTRAINT uq_ton_prog_revisione");
            q.executeUpdate(
                    "ALTER TABLE TON.PROGRAMMI_COTTURA DROP CONSTRAINT ck_ton_prog_num_rev");
            q.executeUpdate("ALTER TABLE TON.PROGRAMMI_COTTURA DROP COLUMN famiglia_id RESTRICT");
            q.executeUpdate(
                    "ALTER TABLE TON.PROGRAMMI_COTTURA DROP COLUMN numero_revisione RESTRICT");
            q.executeUpdate("ALTER TABLE TON.PROGRAMMI_COTTURA DROP COLUMN attivo RESTRICT");
            q.executeUpdate(
                    "ALTER TABLE TON.PROGRAMMI_COTTURA ADD CONSTRAINT uq_ton_prog_versione"
                        + " UNIQUE(codice,numero_versione)");
            q.executeUpdate("DROP TABLE TON.PROGRAMMI_FAMIGLIE");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=31");
        }
    }

    static void beforeV32(Connection c) throws SQLException {
        try (var q = c.createStatement()) {
            q.executeUpdate(
                    "ALTER TABLE TON.CICLI_COTTURA DROP CONSTRAINT fk_ton_cic_prog_precedente");
            q.executeUpdate("ALTER TABLE TON.CICLI_COTTURA DROP CONSTRAINT ck_ton_cic_target");
            q.executeUpdate(
                    "ALTER TABLE TON.CICLI_COTTURA DROP CONSTRAINT ck_ton_cic_prog_precedente");
            q.executeUpdate(
                    "ALTER TABLE TON.CICLI_COTTURA DROP COLUMN programma_precedente_id RESTRICT");
            q.executeUpdate(
                    "ALTER TABLE TON.CICLI_COTTURA DROP COLUMN temperatura_finale_c RESTRICT");
            q.executeUpdate(
                    "ALTER TABLE TON.PROGRAMMA_PASSI ALTER COLUMN temperatura_obiettivo_c NOT"
                        + " NULL");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=32");
        }
    }
}
