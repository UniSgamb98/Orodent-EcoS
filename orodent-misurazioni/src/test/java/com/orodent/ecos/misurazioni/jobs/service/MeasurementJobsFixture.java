package com.orodent.ecos.misurazioni.jobs.service;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.*;

import java.sql.*;
import java.time.*;
import java.util.*;

public final class MeasurementJobsFixture {
    public static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneId.of("Europe/Rome"));

    public static void beforeV34(Connection c) throws SQLException {
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

    public static void seed(ConnectionProvider db) {
        db.withTransaction(
                c -> {
                    CubatureRuleVersions.save(
                            c,
                            null,
                            null,
                            new CubatureRuleCatalogue.Draft("RITIRO", "Ritiro", "", true),
                            "D / Hmedia = fattore",
                            true);
                    try (var q = c.createStatement()) {
                        for (String sql :
                                List.of(
                                        "INSERT INTO TON.LINEE(codice,nome) VALUES"
                                                + " ('ZRMST','Thor')",
                                        "INSERT INTO"
                                            + " TON.PRODOTTI(codice,descrizione,linea_id,colore_commerciale)"
                                            + " VALUES ('ZRMSTA1','Thor"
                                            + " A1',1,'A1'),('ZRMSTA2','Thor A2',1,'A2')",
                                        "INSERT INTO"
                                            + " TON.ARTICOLI(codice,prodotto_id,diametro_mm,altezza_mm)"
                                            + " VALUES"
                                            + " ('ZR9814MSTA1',1,98,14),('ZR9818MSTA1',1,98,18),('ZR9814MSTA2',2,98,14)",
                                        "INSERT INTO"
                                            + " TON.COMPOSIZIONI(prodotto_id,numero_versione,stato,approvato_il)"
                                            + " VALUES"
                                            + " (1,1,'APPROVATA',CURRENT_TIMESTAMP),(2,1,'APPROVATA',CURRENT_TIMESTAMP)",
                                        "INSERT INTO"
                                            + " TON.MODELLI_DISCO(codice,numero_versione,diametro_pressatura_mm,sovramateriale_superiore_mm,sovramateriale_inferiore_mm,pressione_kg_cm2,grammi_per_mm,stato,approvato_il)"
                                            + " VALUES"
                                            + " ('M',1,99,1,1,1500,25,'APPROVATA',CURRENT_TIMESTAMP)",
                                        "INSERT INTO"
                                            + " TON.CONFIGURAZIONI_PRODOTTO(prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo,calcolo_versione_id,stato,approvato_il)"
                                            + " VALUES"
                                            + " (1,1,1,1,'RITIRO',1,'APPROVATA',CURRENT_TIMESTAMP),(2,1,2,1,'RITIRO',1,'APPROVATA',CURRENT_TIMESTAMP)",
                                        "INSERT INTO"
                                            + " TON.PIANI_PRODUZIONE(prodotto_id,diametro_mm,data_preparazione,stato,preparato_il,etichetta_prodotto,numero_formati,numero_dischi,titolo_documento,contenuto_piano)"
                                            + " VALUES"
                                            + " (1,98,DATE('2026-11-21'),'PREPARATA',CURRENT_TIMESTAMP,'Thor"
                                            + " A1',2,5,'Pressatura','{}'),(1,98,DATE('2026-11-21'),'PREPARATA',CURRENT_TIMESTAMP,'Thor"
                                            + " A1',1,1,'Pressatura','{}'),(2,98,DATE('2026-11-21'),'PREPARATA',CURRENT_TIMESTAMP,'Thor"
                                            + " A2',1,2,'Pressatura','{}')",
                                        "INSERT INTO"
                                            + " TON.ESECUZIONI_PIANO(piano_id,data_produzione,note)"
                                            + " VALUES"
                                            + " (1,DATE('2026-11-21'),''),(2,DATE('2026-11-21'),''),(3,DATE('2026-11-21'),'')",
                                        "INSERT INTO TON.FORNI(codice,nome) VALUES ('F1','Forno"
                                                + " reparto')",
                                        "INSERT INTO TON.PROGRAMMI_FAMIGLIE(codice) VALUES ('STD')",
                                        "INSERT INTO"
                                            + " TON.PROGRAMMI_COTTURA(famiglia_id,codice,numero_versione,nome,stato,approvato_il)"
                                            + " VALUES (1,'STD',1,'Ciclo"
                                            + " standard','APPROVATA',CURRENT_TIMESTAMP)",
                                        "INSERT INTO TON.PROGRAMMA_PASSI VALUES"
                                                + " (1,1,500,10,0),(1,2,NULL,10,0)",
                                        "INSERT INTO"
                                            + " TON.CICLI_COTTURA(numero,forno_id,programma_id,data_prevista,caricato_il,forno_codice,forno_nome,forno_revisione,temperatura_finale_c)"
                                            + " VALUES"
                                            + " ('C1',1,1,DATE('2026-11-24'),CURRENT_TIMESTAMP,'F1','Forno"
                                            + " reparto',1,1450),('C2',1,1,DATE('2026-11-25'),CURRENT_TIMESTAMP,'F1','Forno"
                                            + " reparto',1,1460)",
                                        "INSERT INTO TON.LOTTI_COTTURA(codice,ciclo_id) VALUES"
                                                + " ('L-01',1),('L-FUTURO',2)"))
                            q.executeUpdate(sql);
                    }
                    try (var q =
                            c.prepareStatement(
                                    "INSERT INTO"
                                        + " TON.DISCHI_PRODOTTI(esecuzione_id,piano_id,numero_riga,progressivo,articolo_id,prodotto_id,articolo_codice,prodotto_codice,linea_codice,colore,diametro_mm,altezza_mm,specifica_prodotto_id)"
                                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                        for (int id = 1; id <= 8; id++) {
                            int plan = id <= 5 ? 1 : id == 6 ? 2 : 3,
                                    product = plan == 3 ? 2 : 1,
                                    article = id <= 3 || id == 6 ? 1 : id <= 5 ? 2 : 3,
                                    row = id <= 3 || id >= 6 ? 1 : 2,
                                    ordinal =
                                            id <= 3 ? id : id <= 5 ? id - 3 : id == 6 ? 1 : id - 6;
                            q.setInt(1, plan);
                            q.setInt(2, plan);
                            q.setInt(3, row);
                            q.setInt(4, ordinal);
                            q.setInt(5, article);
                            q.setInt(6, product);
                            q.setString(
                                    7,
                                    article == 1
                                            ? "ZR9814MSTA1"
                                            : article == 2 ? "ZR9818MSTA1" : "ZR9814MSTA2");
                            q.setString(8, product == 1 ? "ZRMSTA1" : "ZRMSTA2");
                            q.setString(9, "ZRMST");
                            q.setString(10, product == 1 ? "A1" : "A2");
                            q.setInt(11, 98);
                            q.setInt(12, article == 2 ? 18 : 14);
                            q.setInt(13, product);
                            q.addBatch();
                        }
                        q.executeBatch();
                    }
                    try (var q = c.createStatement()) {
                        for (int id = 1; id <= 8; id++)
                            q.executeUpdate(
                                    "INSERT INTO TON.CARICO_DISCHI VALUES ("
                                            + id
                                            + ","
                                            + (id == 6 ? "2,2" : "1,1")
                                            + ",CURRENT_TIMESTAMP)");
                    }
                    return null;
                });
    }
}
