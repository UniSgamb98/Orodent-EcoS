package com.orodent.ecos.costi;

import com.orodent.ecos.core.ConnectionProvider;

final class ProductCostFixture {
    static void seed(ConnectionProvider db) {
        execute(
                db,
                "INSERT INTO TON.LINEE(codice,nome) VALUES ('MST','Thor')",
                "INSERT INTO TON.PRODOTTI(codice,linea_id,colore_commerciale) VALUES"
                    + " ('MSTA2',1,'A2')",
                "INSERT INTO TON.PRODOTTI(codice,linea_id,colore_commerciale,attivo) VALUES"
                    + " ('MSTA3',1,'A3',FALSE)",
                "INSERT INTO TON.ARTICOLI(codice,prodotto_id,diametro_mm,altezza_mm) VALUES"
                    + " ('MSTA2-98-10',1,98,10)",
                "INSERT INTO TON.ARTICOLI(codice,prodotto_id,diametro_mm,altezza_mm) VALUES"
                    + " ('MSTA2-98-14',1,98,14)",
                "INSERT INTO TON.ARTICOLI(codice,prodotto_id,diametro_mm,altezza_mm,attivo) VALUES"
                    + " ('MSTA3-98-10',2,98,10,FALSE)",
                "INSERT INTO TON.POLVERI(codice,nome,colore) VALUES ('WHITE','Bianca','BIANCA')",
                "INSERT INTO TON.POLVERI(codice,nome,colore) VALUES ('YELLOW','Gialla','GIALLA')",
                "INSERT INTO TON.POLVERI_REVISIONI(polvere_id,numero_versione,stato,approvato_il)"
                    + " VALUES (1,1,'APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.POLVERI_REVISIONI(polvere_id,numero_versione,stato,approvato_il)"
                    + " VALUES (1,2,'APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.POLVERI_REVISIONI(polvere_id,numero_versione,stato,approvato_il)"
                    + " VALUES (2,1,'APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.COMPOSIZIONI(prodotto_id,numero_versione,stato,approvato_il)"
                    + " VALUES (1,1,'APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.COMPOSIZIONE_STRATI(composizione_id,numero_strato) VALUES"
                    + " (1,1),(1,2)",
                "INSERT INTO"
                    + " TON.COMPOSIZIONE_INGREDIENTI(composizione_id,numero_strato,polvere_revisione_id,percentuale_massa)"
                    + " VALUES (1,1,1,60),(1,1,3,40),(1,2,1,100)",
                "INSERT INTO"
                    + " TON.MODELLI_DISCO(codice,numero_versione,diametro_pressatura_mm,sovramateriale_superiore_mm,sovramateriale_inferiore_mm,pressione_kg_cm2,grammi_per_mm,stato,approvato_il)"
                    + " VALUES ('THOR',1,100,1,2,300,25,'APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.MODELLO_STRATI(modello_id,numero_strato,percentuale_altezza)"
                    + " VALUES (1,1,30),(1,2,70)",
                "INSERT INTO"
                    + " TON.CONFIGURAZIONI_ARTICOLO(articolo_id,prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo,stato,approvato_il)"
                    + " VALUES (1,1,1,1,1,'TON-ORIGINALE','APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO"
                    + " TON.CONFIGURAZIONI_ARTICOLO(articolo_id,prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo,stato,approvato_il)"
                    + " VALUES (1,1,2,1,1,'TON-ORIGINALE','APPROVATA',CURRENT_TIMESTAMP)",
                "UPDATE TON.ARTICOLI SET configurazione_predefinita_id=1 WHERE articolo_id=1",
                "INSERT INTO"
                    + " COSTI.POLVERI_PREZZI(polvere_id,numero_versione,data_riferimento,modalita,prezzo_semplice)"
                    + " VALUES (1,1,DATE('2026-11-08'),'SEMPLICE',80)",
                "INSERT INTO"
                    + " COSTI.POLVERI_PREZZI(polvere_id,numero_versione,data_riferimento,modalita,prezzo_semplice)"
                    + " VALUES (1,2,DATE('2026-11-09'),'SEMPLICE',100)",
                "INSERT INTO"
                    + " COSTI.POLVERI_PREZZI(polvere_id,numero_versione,data_riferimento,modalita)"
                    + " VALUES (2,1,DATE('2026-11-09'),'AVANZATA')",
                "INSERT INTO COSTI.POLVERI_LOTTI(prezzo_id,posizione,lotto,quantita_kg,prezzo_kg)"
                    + " VALUES (3,1,'L01',100,180),(3,2,'L01',50,240)");
    }

    static void execute(ConnectionProvider db, String... sql) {
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        for (String statement : sql) q.executeUpdate(statement);
                    }
                    return null;
                });
    }
}
