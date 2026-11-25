package com.orodent.ecos.database;

import java.sql.*;

final class FiringProgramFixtures {
    static void beforeV34(Connection c) throws SQLException {
        try (var q = c.createStatement()) {
            q.executeUpdate("DROP TABLE TON.ETICHETTA_DISCHI");
            q.executeUpdate("DROP TABLE TON.ETICHETTE_EMESSE");
            q.executeUpdate(
                    "ALTER TABLE TON.CONFIGURAZIONI_PRODOTTO DROP CONSTRAINT fk_ton_cfg_etichetta");
            q.executeUpdate(
                    "ALTER TABLE TON.CONFIGURAZIONI_PRODOTTO DROP COLUMN etichetta_modello_id"
                            + " RESTRICT");
            q.executeUpdate("ALTER TABLE TON.ARTICOLI DROP CONSTRAINT uq_ton_art_gtin");
            q.executeUpdate("ALTER TABLE TON.ARTICOLI DROP CONSTRAINT ck_ton_art_gtin");
            q.executeUpdate("ALTER TABLE TON.ARTICOLI DROP COLUMN gtin RESTRICT");
            q.executeUpdate("ALTER TABLE TON.ARTICOLI_REVISIONI DROP COLUMN gtin RESTRICT");
            q.executeUpdate("ALTER TABLE TON.MODELLI_DOCUMENTO DROP CONSTRAINT ck_ton_docm_tipo");
            q.executeUpdate(
                    "ALTER TABLE TON.MODELLI_DOCUMENTO ADD CONSTRAINT ck_ton_docm_tipo CHECK (tipo"
                            + " IN ('PRODUZIONE','COTTURA'))");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=34");
        }
    }

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
        beforeV34(c);
        try (var q = c.createStatement()) {
            q.executeUpdate("DROP TABLE TON.DISCO_MISURE_REVISIONI");
            q.executeUpdate("DELETE FROM ECOS.ECOS_SCHEMA_HISTORY WHERE version=33");
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
