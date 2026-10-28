package com.orodent.ecos.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Read-only structural contract of the TON tables, checked on startup/reconnection. */
final class TonSchema {
    private TonSchema() { }

    private static final List<Table> TABLES = List.of(
            new Table("LINEE", List.of(
                    column("LINEA_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("CODICE", Types.VARCHAR, 50, 0, false, false, null),
                    column("NOME", Types.VARCHAR, 100, 0, false, false, null),
                    column("ATTIVO", Types.BOOLEAN, 0, 0, false, false, "TRUE"),
                    column("VERSIONE_MODIFICA", Types.INTEGER, 0, 0, false, false, "1"),
                    column("CREATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("AGGIORNATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP")),
                    List.of("PK_TON_LIN", "UQ_TON_LIN_CODICE", "CK_TON_LIN_NOME", "CK_TON_LIN_REVISIONE", "CK_TON_LIN_CODICE")),
            new Table("LINEE_PRODOTTI", List.of(
                    column("LINEA_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("PRODOTTO_ID", Types.BIGINT, 0, 0, false, false, null)),
                    List.of("PK_TON_LP", "FK_TON_LP_LINEA", "FK_TON_LP_PRODOTTO")),
            new Table("ARTICOLI", List.of(
                    column("ARTICOLO_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("CODICE", Types.VARCHAR, 80, 0, false, false, null),
                    column("PRODOTTO_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("DIAMETRO_MM", Types.DECIMAL, 6, 2, false, false, null),
                    column("ALTEZZA_MM", Types.DECIMAL, 6, 2, false, false, null),
                    column("DESCRIZIONE", Types.VARCHAR, 200, 0, true, false, null),
                    column("CONFIGURAZIONE_PREDEFINITA_ID", Types.BIGINT, 0, 0, true, false, null),
                    column("ATTIVO", Types.BOOLEAN, 0, 0, false, false, "TRUE"),
                    column("VERSIONE_MODIFICA", Types.INTEGER, 0, 0, false, false, "1"),
                    column("CREATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("AGGIORNATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP")),
                    List.of("PK_TON_ART", "UQ_TON_ART_CODICE", "UQ_TON_ART_FORMATO", "UQ_TON_ART_PRODOTTO", "FK_TON_ART_PRODOTTO", "CK_TON_ART_MISURE", "CK_TON_ART_REVISIONE", "CK_TON_ART_CODICE", "FK_TON_ART_PREDEFINITA")),
            new Table("POLVERI", List.of(
                    column("POLVERE_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("CODICE", Types.VARCHAR, 50, 0, false, false, null),
                    column("NOME", Types.VARCHAR, 100, 0, false, false, null),
                    column("ORDINE_VISUALIZZAZIONE", Types.INTEGER, 0, 0, false, false, "0"),
                    column("ATTIVO", Types.BOOLEAN, 0, 0, false, false, "TRUE"),
                    column("VERSIONE_MODIFICA", Types.INTEGER, 0, 0, false, false, "1"),
                    column("CREATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("AGGIORNATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP")),
                    List.of("PK_TON_POL", "UQ_TON_POL_CODICE", "CK_TON_POL_NOME", "CK_TON_POL_ORDINE", "CK_TON_POL_REVISIONE", "CK_TON_POL_CODICE")),
            new Table("POLVERI_REVISIONI", List.of(
                    column("POLVERE_REVISIONE_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("POLVERE_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("NUMERO_VERSIONE", Types.INTEGER, 0, 0, false, false, null),
                    column("RESISTENZA_MPA", Types.DECIMAL, 10, 3, true, false, null),
                    column("TRASLUCENZA", Types.DECIMAL, 10, 4, true, false, null),
                    column("CLASSE_ITTRIA_Y", Types.INTEGER, 0, 0, true, false, null),
                    column("NOTE", Types.VARCHAR, 1000, 0, true, false, null),
                    column("STATO", Types.VARCHAR, 20, 0, false, false, "'BOZZA'"),
                    column("VERSIONE_MODIFICA", Types.INTEGER, 0, 0, false, false, "1"),
                    column("CREATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("AGGIORNATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("APPROVATO_IL", Types.TIMESTAMP, 0, 0, true, false, null)),
                    List.of("PK_TON_PR", "UQ_TON_PR_VERSIONE", "FK_TON_PR_POLVERE", "CK_TON_PR_RESISTENZA", "CK_TON_PR_TRASLUCENZA", "CK_TON_PR_ITTRIA", "CK_TON_PR_REVISIONE", "CK_TON_PR_VERSIONE", "CK_TON_PR_STATO")),
            new Table("POLVERE_OSSIDI", List.of(
                    column("POLVERE_REVISIONE_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("OSSIDO_CODICE", Types.VARCHAR, 30, 0, false, false, null),
                    column("PERCENTUALE_MASSA", Types.DECIMAL, 7, 4, false, false, null)),
                    List.of("PK_TON_OSS", "FK_TON_OSS_REVISIONE", "CK_TON_OSS_QUOTA", "CK_TON_OSS_CODICE")),
            new Table("COMPOSIZIONI", List.of(
                    column("COMPOSIZIONE_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("PRODOTTO_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("NUMERO_VERSIONE", Types.INTEGER, 0, 0, false, false, null),
                    column("NOTE", Types.VARCHAR, 1000, 0, true, false, null),
                    column("STATO", Types.VARCHAR, 20, 0, false, false, "'BOZZA'"),
                    column("VERSIONE_MODIFICA", Types.INTEGER, 0, 0, false, false, "1"),
                    column("CREATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("AGGIORNATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("APPROVATO_IL", Types.TIMESTAMP, 0, 0, true, false, null)),
                    List.of("PK_TON_COMP", "UQ_TON_COMP_VERSIONE", "UQ_TON_COMP_PRODOTTO", "FK_TON_COMP_PRODOTTO", "CK_TON_COMP_REVISIONE", "CK_TON_COMP_VERSIONE", "CK_TON_COMP_STATO")),
            new Table("COMPOSIZIONE_STRATI", List.of(
                    column("COMPOSIZIONE_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("NUMERO_STRATO", Types.INTEGER, 0, 0, false, false, null),
                    column("NOME", Types.VARCHAR, 100, 0, true, false, null)),
                    List.of("PK_TON_CS", "FK_TON_CS_COMPOSIZIONE", "CK_TON_CS_NUMERO")),
            new Table("COMPOSIZIONE_INGREDIENTI", List.of(
                    column("COMPOSIZIONE_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("NUMERO_STRATO", Types.INTEGER, 0, 0, false, false, null),
                    column("POLVERE_REVISIONE_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("PERCENTUALE_MASSA", Types.DECIMAL, 7, 4, false, false, null)),
                    List.of("PK_TON_CI", "FK_TON_CI_STRATO", "FK_TON_CI_POLVERE", "CK_TON_CI_QUOTA")),
            new Table("MODELLI_DISCO", List.of(
                    column("MODELLO_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("CODICE", Types.VARCHAR, 100, 0, false, false, null),
                    column("NUMERO_VERSIONE", Types.INTEGER, 0, 0, false, false, null),
                    column("DIAMETRO_PRESSATURA_MM", Types.DECIMAL, 6, 2, false, false, null),
                    column("SOVRAMATERIALE_SUPERIORE_MM", Types.DECIMAL, 6, 2, false, false, null),
                    column("SOVRAMATERIALE_INFERIORE_MM", Types.DECIMAL, 6, 2, false, false, null),
                    column("PRESSIONE_KG_CM2", Types.DECIMAL, 10, 3, false, false, null),
                    column("GRAMMI_PER_MM", Types.DECIMAL, 12, 5, false, false, null),
                    column("NOTE", Types.VARCHAR, 1000, 0, true, false, null),
                    column("STATO", Types.VARCHAR, 20, 0, false, false, "'BOZZA'"),
                    column("VERSIONE_MODIFICA", Types.INTEGER, 0, 0, false, false, "1"),
                    column("CREATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("AGGIORNATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("APPROVATO_IL", Types.TIMESTAMP, 0, 0, true, false, null)),
                    List.of("PK_TON_MD", "UQ_TON_MD_VERSIONE", "CK_TON_MD_MISURE", "CK_TON_MD_REVISIONE", "CK_TON_MD_VERSIONE", "CK_TON_MD_CODICE", "CK_TON_MD_STATO")),
            new Table("MODELLO_STRATI", List.of(
                    column("MODELLO_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("NUMERO_STRATO", Types.INTEGER, 0, 0, false, false, null),
                    column("PERCENTUALE_ALTEZZA", Types.DECIMAL, 7, 4, false, false, null)),
                    List.of("PK_TON_MS", "FK_TON_MS_MODELLO", "CK_TON_MS_NUMERO", "CK_TON_MS_QUOTA")),
            new Table("MODELLO_SOVRAMATERIALI", List.of(
                    column("SOVRAMATERIALE_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("MODELLO_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("ALTEZZA_DA_MM", Types.DECIMAL, 6, 2, false, false, null),
                    column("ALTEZZA_A_MM", Types.DECIMAL, 6, 2, false, false, null),
                    column("SUPERIORE_MM", Types.DECIMAL, 6, 2, false, false, null),
                    column("INFERIORE_MM", Types.DECIMAL, 6, 2, false, false, null)),
                    List.of("PK_TON_SM", "UQ_TON_SM_FASCIA", "FK_TON_SM_MODELLO", "CK_TON_SM_FASCIA", "CK_TON_SM_MISURE")),
            new Table("CONFIGURAZIONI_ARTICOLO", List.of(
                    column("CONFIGURAZIONE_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("ARTICOLO_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("PRODOTTO_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("NUMERO_VERSIONE", Types.INTEGER, 0, 0, false, false, null),
                    column("COMPOSIZIONE_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("MODELLO_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("VERSIONE_CALCOLO", Types.VARCHAR, 50, 0, false, false, null),
                    column("NOTE", Types.VARCHAR, 1000, 0, true, false, null),
                    column("STATO", Types.VARCHAR, 20, 0, false, false, "'BOZZA'"),
                    column("VERSIONE_MODIFICA", Types.INTEGER, 0, 0, false, false, "1"),
                    column("CREATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("AGGIORNATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("APPROVATO_IL", Types.TIMESTAMP, 0, 0, true, false, null)),
                    List.of("PK_TON_CFG", "UQ_TON_CFG_VERSIONE", "UQ_TON_CFG_ARTICOLO", "FK_TON_CFG_ARTICOLO", "FK_TON_CFG_COMPOSIZIONE", "FK_TON_CFG_MODELLO", "CK_TON_CFG_CALCOLO", "CK_TON_CFG_REVISIONE", "CK_TON_CFG_VERSIONE", "CK_TON_CFG_STATO")),
            new Table("FORNI", List.of(
                    column("FORNO_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("CODICE", Types.VARCHAR, 50, 0, false, false, null),
                    column("NOME", Types.VARCHAR, 100, 0, false, false, null),
                    column("NOTE", Types.VARCHAR, 1000, 0, true, false, null),
                    column("ATTIVO", Types.BOOLEAN, 0, 0, false, false, "TRUE"),
                    column("VERSIONE_MODIFICA", Types.INTEGER, 0, 0, false, false, "1"),
                    column("CREATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("AGGIORNATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP")),
                    List.of("PK_TON_FOR", "UQ_TON_FOR_CODICE", "CK_TON_FOR_NOME", "CK_TON_FOR_REVISIONE", "CK_TON_FOR_CODICE")),
            new Table("PROGRAMMI_COTTURA", List.of(
                    column("PROGRAMMA_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("CODICE", Types.VARCHAR, 80, 0, false, false, null),
                    column("NUMERO_VERSIONE", Types.INTEGER, 0, 0, false, false, null),
                    column("NOME", Types.VARCHAR, 200, 0, false, false, null),
                    column("NOTE", Types.VARCHAR, 1000, 0, true, false, null),
                    column("STATO", Types.VARCHAR, 20, 0, false, false, "'BOZZA'"),
                    column("VERSIONE_MODIFICA", Types.INTEGER, 0, 0, false, false, "1"),
                    column("CREATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("AGGIORNATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("APPROVATO_IL", Types.TIMESTAMP, 0, 0, true, false, null)),
                    List.of("PK_TON_PROG", "UQ_TON_PROG_VERSIONE", "CK_TON_PROG_NOME", "CK_TON_PROG_REVISIONE", "CK_TON_PROG_VERSIONE", "CK_TON_PROG_CODICE", "CK_TON_PROG_STATO")),
            new Table("PROGRAMMA_PASSI", List.of(
                    column("PROGRAMMA_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("NUMERO_PASSO", Types.INTEGER, 0, 0, false, false, null),
                    column("TEMPERATURA_OBIETTIVO_C", Types.DECIMAL, 7, 2, false, false, null),
                    column("DURATA_RAMPA_MINUTI", Types.INTEGER, 0, 0, false, false, null),
                    column("DURATA_MANTENIMENTO_MINUTI", Types.INTEGER, 0, 0, false, false, null)),
                    List.of("PK_TON_PASS", "FK_TON_PASS_PROGRAMMA", "CK_TON_PASS_NUMERO", "CK_TON_PASS_PARAMETRI")),
            new Table("PRODUZIONI", List.of(
                    column("PRODUZIONE_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("NUMERO", Types.VARCHAR, 50, 0, false, false, null),
                    column("LINEA_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("DATA_PRODUZIONE", Types.DATE, 0, 0, false, false, null),
                    column("STATO", Types.VARCHAR, 20, 0, false, false, "'BOZZA'"),
                    column("CONFERMATA_IL", Types.TIMESTAMP, 0, 0, true, false, null),
                    column("ANNULLATA_IL", Types.TIMESTAMP, 0, 0, true, false, null),
                    column("MOTIVO_ANNULLAMENTO", Types.VARCHAR, 1000, 0, true, false, null),
                    column("NOTE", Types.VARCHAR, 1000, 0, true, false, null),
                    column("VERSIONE_MODIFICA", Types.INTEGER, 0, 0, false, false, "1"),
                    column("CREATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("AGGIORNATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP")),
                    List.of("PK_TON_PROD", "UQ_TON_PROD_NUMERO", "FK_TON_PROD_LINEA", "CK_TON_PROD_NUMERO", "CK_TON_PROD_STATO", "CK_TON_PROD_REVISIONE")),
            new Table("PRODUZIONE_RIGHE", List.of(
                    column("PRODUZIONE_RIGA_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("PRODUZIONE_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("NUMERO_RIGA", Types.INTEGER, 0, 0, false, false, null),
                    column("CONFIGURAZIONE_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("QUANTITA_PRODOTTA", Types.INTEGER, 0, 0, false, false, null),
                    column("DOSI_CALCOLATE", Types.CLOB, 0, 0, true, false, null)),
                    List.of("PK_TON_RIG", "UQ_TON_RIG_NUMERO", "FK_TON_RIG_PRODUZIONE", "FK_TON_RIG_CONFIGURAZIONE", "CK_TON_RIG_QUANTITA")),
            new Table("SCARTI_PRODUZIONE", List.of(
                    column("SCARTO_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("PRODUZIONE_RIGA_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("QUANTITA", Types.INTEGER, 0, 0, false, false, null),
                    column("REGISTRATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("MOTIVO", Types.VARCHAR, 1000, 0, false, false, null),
                    column("ANNULLATO_IL", Types.TIMESTAMP, 0, 0, true, false, null),
                    column("MOTIVO_ANNULLAMENTO", Types.VARCHAR, 1000, 0, true, false, null)),
                    List.of("PK_TON_SCP", "FK_TON_SCP_RIGA", "CK_TON_SCP_QUANTITA", "CK_TON_SCP_MOTIVO", "CK_TON_SCP_ANNULLAMENTO")),
            new Table("CICLI_COTTURA", List.of(
                    column("CICLO_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("NUMERO", Types.VARCHAR, 50, 0, false, false, null),
                    column("FORNO_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("PROGRAMMA_ID", Types.BIGINT, 0, 0, true, false, null),
                    column("DATA_PREVISTA", Types.DATE, 0, 0, false, false, null),
                    column("STATO", Types.VARCHAR, 20, 0, false, false, "'BOZZA'"),
                    column("INIZIATO_IL", Types.TIMESTAMP, 0, 0, true, false, null),
                    column("TERMINATO_IL", Types.TIMESTAMP, 0, 0, true, false, null),
                    column("TEMPERATURA_MASSIMA_RILEVATA_C", Types.DECIMAL, 7, 2, true, false, null),
                    column("ANNULLATO_IL", Types.TIMESTAMP, 0, 0, true, false, null),
                    column("MOTIVO_ANNULLAMENTO", Types.VARCHAR, 1000, 0, true, false, null),
                    column("NOTE", Types.VARCHAR, 1000, 0, true, false, null),
                    column("VERSIONE_MODIFICA", Types.INTEGER, 0, 0, false, false, "1"),
                    column("CREATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("AGGIORNATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP")),
                    List.of("PK_TON_CIC", "UQ_TON_CIC_NUMERO", "FK_TON_CIC_FORNO", "FK_TON_CIC_PROGRAMMA", "CK_TON_CIC_NUMERO", "CK_TON_CIC_TEMPERATURA", "CK_TON_CIC_STATO", "CK_TON_CIC_REVISIONE")),
            new Table("LOTTI_COTTURA", List.of(
                    column("LOTTO_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("CODICE", Types.VARCHAR, 50, 0, false, false, null),
                    column("CICLO_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("CREATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("NOTE", Types.VARCHAR, 1000, 0, true, false, null)),
                    List.of("PK_TON_LOT", "UQ_TON_LOT_CODICE", "UQ_TON_LOT_CICLO", "FK_TON_LOT_CICLO", "CK_TON_LOT_CODICE")),
            new Table("CARICHI_COTTURA", List.of(
                    column("CARICO_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("CICLO_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("PRODUZIONE_RIGA_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("QUANTITA_CARICATA", Types.INTEGER, 0, 0, false, false, null),
                    column("QUANTITA_BUONA", Types.INTEGER, 0, 0, true, false, null),
                    column("QUANTITA_SCARTATA", Types.INTEGER, 0, 0, true, false, null),
                    column("LOTTO_ID", Types.BIGINT, 0, 0, true, false, null),
                    column("NOTE_ESITO", Types.VARCHAR, 1000, 0, true, false, null)),
                    List.of("PK_TON_CAR", "FK_TON_CAR_CICLO", "FK_TON_CAR_RIGA", "FK_TON_CAR_LOTTO", "CK_TON_CAR_QUANTITA", "CK_TON_CAR_ESITO")),
            new Table("MODELLI_DOCUMENTO", List.of(
                    column("MODELLO_DOCUMENTO_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("CODICE", Types.VARCHAR, 100, 0, false, false, null),
                    column("NUMERO_VERSIONE", Types.INTEGER, 0, 0, false, false, null),
                    column("NOME", Types.VARCHAR, 150, 0, false, false, null),
                    column("TIPO", Types.VARCHAR, 20, 0, false, false, null),
                    column("CONTENUTO_MODELLO", Types.CLOB, 0, 0, false, false, null),
                    column("STATO", Types.VARCHAR, 20, 0, false, false, "'BOZZA'"),
                    column("VERSIONE_MODIFICA", Types.INTEGER, 0, 0, false, false, "1"),
                    column("CREATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("AGGIORNATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("APPROVATO_IL", Types.TIMESTAMP, 0, 0, true, false, null)),
                    List.of("PK_TON_DOCM", "UQ_TON_DOCM_VERSIONE", "UQ_TON_DOCM_TIPO", "CK_TON_DOCM_TIPO", "CK_TON_DOCM_NOME", "CK_TON_DOCM_REVISIONE", "CK_TON_DOCM_VERSIONE", "CK_TON_DOCM_CODICE", "CK_TON_DOCM_STATO")),
            new Table("DOCUMENTI", List.of(
                    column("DOCUMENTO_ID", Types.BIGINT, 0, 0, false, true, null),
                    column("MODELLO_DOCUMENTO_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("TIPO", Types.VARCHAR, 20, 0, false, false, null),
                    column("PRODUZIONE_ID", Types.BIGINT, 0, 0, true, false, null),
                    column("CICLO_ID", Types.BIGINT, 0, 0, true, false, null),
                    column("GENERATO_IL", Types.TIMESTAMP, 0, 0, false, false, "CURRENT_TIMESTAMP"),
                    column("CONTENUTO_EMESSO", Types.CLOB, 0, 0, false, false, null),
                    column("TIPO_CONTENUTO", Types.VARCHAR, 100, 0, false, false, null)),
                    List.of("PK_TON_DOC", "FK_TON_DOC_MODELLO", "FK_TON_DOC_PRODUZIONE", "FK_TON_DOC_CICLO", "CK_TON_DOC_ORIGINE", "CK_TON_DOC_CONTENUTO")));

    private static final Table POWDER_COLORS = new Table("POLVERI",
            List.of(column("COLORE", Types.VARCHAR, 10, 0, true, false, null)),
            List.of("CK_TON_POL_COLORE"));

    private static final List<Table> CATALOGUES = List.of(
            new Table("LINEE", List.of(
                    column("TECNOLOGIA", Types.VARCHAR, 200, 0, true, false, null),
                    column("MATERIA_PRIMA", Types.VARCHAR, 30, 0, true, false, null),
                    column("NOTE", Types.VARCHAR, 1000, 0, true, false, null)),
                    List.of()),
            new Table("PRODOTTI", List.of(
                    column("LINEA_ID", Types.BIGINT, 0, 0, true, false, null),
                    column("COLORE_COMMERCIALE", Types.VARCHAR, 30, 0, true, false, null),
                    column("NOTE", Types.VARCHAR, 1000, 0, true, false, null)),
                    List.of("FK_TON_PRO_LINEA","UQ_TON_PRO_LINEA_COLORE","CK_TON_PRO_COLORE")),
            new Table("ARTICOLI", List.of(
                    column("NOTE", Types.VARCHAR, 1000, 0, true, false, null)),
                    List.of()),
            new Table("LINEE_REVISIONI", List.of(
                    column("LINEA_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("NUMERO_REVISIONE", Types.INTEGER, 0, 0, false, false, null),
                    column("CODICE", Types.VARCHAR, 50, 0, false, false, null),
                    column("NOME", Types.VARCHAR, 100, 0, false, false, null),
                    column("TECNOLOGIA", Types.VARCHAR, 200, 0, true, false, null),
                    column("MATERIA_PRIMA", Types.VARCHAR, 30, 0, true, false, null),
                    column("NOTE", Types.VARCHAR, 1000, 0, true, false, null),
                    column("ATTIVO", Types.BOOLEAN, 0, 0, false, false, null),
                    column("REGISTRATO_IL", Types.TIMESTAMP, 0, 0, false, false, null),
                    column("ORIGINE", Types.VARCHAR, 20, 0, false, false, null)),
                    List.of("PK_TON_LIN_STOR","FK_TON_LIN_STOR","CK_TON_LIN_STOR_REV","CK_TON_LIN_STOR_ORI")),
            new Table("PRODOTTI_REVISIONI", List.of(
                    column("PRODOTTO_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("NUMERO_REVISIONE", Types.INTEGER, 0, 0, false, false, null),
                    column("CODICE", Types.VARCHAR, 50, 0, false, false, null),
                    column("DESCRIZIONE", Types.VARCHAR, 200, 0, true, false, null),
                    column("LINEA_ID", Types.BIGINT, 0, 0, true, false, null),
                    column("LINEA_CODICE", Types.VARCHAR, 50, 0, true, false, null),
                    column("COLORE_COMMERCIALE", Types.VARCHAR, 30, 0, true, false, null),
                    column("NOTE", Types.VARCHAR, 1000, 0, true, false, null),
                    column("ATTIVO", Types.BOOLEAN, 0, 0, false, false, null),
                    column("REGISTRATO_IL", Types.TIMESTAMP, 0, 0, false, false, null),
                    column("ORIGINE", Types.VARCHAR, 20, 0, false, false, null)),
                    List.of("PK_TON_PRO_STOR","FK_TON_PRO_STOR","CK_TON_PRO_STOR_REV","CK_TON_PRO_STOR_ORI","FK_TON_PRO_STOR_LINEA")),
            new Table("ARTICOLI_REVISIONI", List.of(
                    column("ARTICOLO_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("NUMERO_REVISIONE", Types.INTEGER, 0, 0, false, false, null),
                    column("CODICE", Types.VARCHAR, 80, 0, false, false, null),
                    column("DESCRIZIONE", Types.VARCHAR, 200, 0, true, false, null),
                    column("PRODOTTO_ID", Types.BIGINT, 0, 0, false, false, null),
                    column("PRODOTTO_CODICE", Types.VARCHAR, 50, 0, false, false, null),
                    column("LINEA_ID", Types.BIGINT, 0, 0, true, false, null),
                    column("LINEA_CODICE", Types.VARCHAR, 50, 0, true, false, null),
                    column("COLORE_COMMERCIALE", Types.VARCHAR, 30, 0, true, false, null),
                    column("DIAMETRO_MM", Types.DECIMAL, 6, 2, false, false, null),
                    column("ALTEZZA_MM", Types.DECIMAL, 6, 2, false, false, null),
                    column("CONFIGURAZIONE_PREDEFINITA_ID", Types.BIGINT, 0, 0, true, false, null),
                    column("NOTE", Types.VARCHAR, 1000, 0, true, false, null),
                    column("ATTIVO", Types.BOOLEAN, 0, 0, false, false, null),
                    column("REGISTRATO_IL", Types.TIMESTAMP, 0, 0, false, false, null),
                    column("ORIGINE", Types.VARCHAR, 20, 0, false, false, null)),
                    List.of("PK_TON_ART_STOR","FK_TON_ART_STOR","CK_TON_ART_STOR_REV","CK_TON_ART_STOR_ORI","FK_TON_ART_STOR_PRO","FK_TON_ART_STOR_LINEA","FK_TON_ART_STOR_CFG")),
            new Table("CONFIGURAZIONI_ARTICOLO", List.of(
                    column("LINEA_ID", Types.BIGINT, 0, 0, true, false, null),
                    column("LINEA_REVISIONE", Types.INTEGER, 0, 0, true, false, null),
                    column("PRODOTTO_REVISIONE", Types.INTEGER, 0, 0, true, false, null),
                    column("ARTICOLO_REVISIONE", Types.INTEGER, 0, 0, true, false, null)),
                    List.of("FK_TON_CFG_LIN_STOR","FK_TON_CFG_PRO_STOR","FK_TON_CFG_ART_STOR","CK_TON_CFG_STOR")));

    static void verify(Connection connection, int schemaVersion) throws SQLException {
        // One metadata scan for the entire domain, rather than one round-trip per column.
        Map<String, Map<String, Column>> actual = new HashMap<>();
        try (var rows = connection.getMetaData().getColumns(null, "TON", null, null)) {
            while (rows.next()) {
                actual.computeIfAbsent(rows.getString("TABLE_NAME"), ignored -> new HashMap<>())
                        .put(rows.getString("COLUMN_NAME"), new Column(rows.getString("COLUMN_NAME"),
                                rows.getInt("DATA_TYPE"), rows.getInt("COLUMN_SIZE"), rows.getInt("DECIMAL_DIGITS"),
                                rows.getInt("NULLABLE") != java.sql.DatabaseMetaData.columnNoNulls,
                                "YES".equals(rows.getString("IS_AUTOINCREMENT")), rows.getString("COLUMN_DEF")));
            }
        }
        Set<String> constraints = new HashSet<>();
        try (var statement = connection.prepareStatement("""
                SELECT t.tablename, c.constraintname
                FROM sys.sysconstraints c
                JOIN sys.systables t ON t.tableid = c.tableid
                JOIN sys.sysschemas s ON s.schemaid = t.schemaid
                WHERE s.schemaname = 'TON' AND c.state = 'E'
                """)) {
            statement.setQueryTimeout(3);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) constraints.add(rows.getString(1) + "." + rows.getString(2));
            }
        }
        var tables = new ArrayList<>(TABLES);
        if (schemaVersion >= 5) tables.add(POWDER_COLORS);
        if (schemaVersion >= 6) tables.addAll(CATALOGUES);
        for (Table table : tables) {
            Map<String, Column> columns = actual.getOrDefault(table.name(), Map.of());
            for (Column expected : table.columns()) {
                Column found = columns.get(expected.name());
                if (found == null || found.type() != expected.type() || found.nullable() != expected.nullable()
                        || found.identity() != expected.identity()
                        || expected.precision() > 0 && found.precision() < expected.precision()
                        || expected.type() == Types.DECIMAL && found.scale() != expected.scale()
                        || expected.defaultExpression() != null
                        && !normalize(expected.defaultExpression()).equals(normalize(found.defaultExpression()))) {
                    throw incompatible(table.name() + "." + expected.name());
                }
            }
            for (String constraint : table.constraints()) {
                if (!constraints.contains(table.name() + "." + constraint)) {
                    throw incompatible(table.name() + "." + constraint);
                }
            }
        }
    }

    private static SQLException incompatible(String member) {
        return new SQLException("Schema TON incompatibile: TON." + member, DatabaseSchema.INCOMPATIBLE_SCHEMA);
    }

    private static String normalize(String expression) {
        return expression == null ? "" : expression.trim().toUpperCase(Locale.ROOT);
    }

    private static Column column(String name, int type, int precision, int scale,
                                 boolean nullable, boolean identity, String defaultExpression) {
        return new Column(name, type, precision, scale, nullable, identity, defaultExpression);
    }

    private record Table(String name, List<Column> columns, List<String> constraints) { }
    private record Column(String name, int type, int precision, int scale,
                          boolean nullable, boolean identity, String defaultExpression) { }
}
