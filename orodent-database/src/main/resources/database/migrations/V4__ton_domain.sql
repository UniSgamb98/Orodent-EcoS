-- V4: TON catalogues, versioned specifications, production, firing and documents.
-- Row and relationship constraints live here. Aggregate approval and allocation
-- rules require the transactional services described in docs/ton-schema-proposta.md.
-- No business rows, legacy imports or automatic document generation are added.

CREATE TABLE TON.LINEE (
    linea_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    codice VARCHAR(50) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    attivo BOOLEAN NOT NULL DEFAULT TRUE,
    versione_modifica INTEGER NOT NULL DEFAULT 1,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ton_lin PRIMARY KEY (linea_id),
    CONSTRAINT uq_ton_lin_codice UNIQUE (codice),
    CONSTRAINT ck_ton_lin_nome CHECK (LENGTH(TRIM(nome)) > 0),
    CONSTRAINT ck_ton_lin_revisione CHECK (versione_modifica > 0),
    CONSTRAINT ck_ton_lin_codice CHECK (LENGTH(TRIM(codice)) > 0 AND codice = UPPER(TRIM(codice)))
);

CREATE TABLE TON.LINEE_PRODOTTI (
    linea_id BIGINT NOT NULL,
    prodotto_id BIGINT NOT NULL,
    CONSTRAINT pk_ton_lp PRIMARY KEY (linea_id, prodotto_id),
    CONSTRAINT fk_ton_lp_linea FOREIGN KEY (linea_id) REFERENCES TON.LINEE (linea_id),
    CONSTRAINT fk_ton_lp_prodotto FOREIGN KEY (prodotto_id) REFERENCES TON.PRODOTTI (prodotto_id)
);

CREATE TABLE TON.ARTICOLI (
    articolo_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    codice VARCHAR(80) NOT NULL,
    prodotto_id BIGINT NOT NULL,
    diametro_mm DECIMAL(6,2) NOT NULL,
    altezza_mm DECIMAL(6,2) NOT NULL,
    descrizione VARCHAR(200),
    configurazione_predefinita_id BIGINT,
    attivo BOOLEAN NOT NULL DEFAULT TRUE,
    versione_modifica INTEGER NOT NULL DEFAULT 1,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ton_art PRIMARY KEY (articolo_id),
    CONSTRAINT uq_ton_art_codice UNIQUE (codice),
    CONSTRAINT uq_ton_art_formato UNIQUE (prodotto_id, diametro_mm, altezza_mm),
    CONSTRAINT uq_ton_art_prodotto UNIQUE (articolo_id, prodotto_id),
    CONSTRAINT fk_ton_art_prodotto FOREIGN KEY (prodotto_id) REFERENCES TON.PRODOTTI (prodotto_id),
    CONSTRAINT ck_ton_art_misure CHECK (diametro_mm > 0 AND altezza_mm > 0),
    CONSTRAINT ck_ton_art_revisione CHECK (versione_modifica > 0),
    CONSTRAINT ck_ton_art_codice CHECK (LENGTH(TRIM(codice)) > 0 AND codice = UPPER(TRIM(codice)))
);

CREATE TABLE TON.POLVERI (
    polvere_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    codice VARCHAR(50) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    ordine_visualizzazione INTEGER NOT NULL DEFAULT 0,
    attivo BOOLEAN NOT NULL DEFAULT TRUE,
    versione_modifica INTEGER NOT NULL DEFAULT 1,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ton_pol PRIMARY KEY (polvere_id),
    CONSTRAINT uq_ton_pol_codice UNIQUE (codice),
    CONSTRAINT ck_ton_pol_nome CHECK (LENGTH(TRIM(nome)) > 0),
    CONSTRAINT ck_ton_pol_ordine CHECK (ordine_visualizzazione >= 0),
    CONSTRAINT ck_ton_pol_revisione CHECK (versione_modifica > 0),
    CONSTRAINT ck_ton_pol_codice CHECK (LENGTH(TRIM(codice)) > 0 AND codice = UPPER(TRIM(codice)))
);

CREATE TABLE TON.POLVERI_REVISIONI (
    polvere_revisione_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    polvere_id BIGINT NOT NULL,
    numero_versione INTEGER NOT NULL,
    resistenza_mpa DECIMAL(10,3),
    traslucenza DECIMAL(10,4),
    classe_ittria_y INTEGER,
    note VARCHAR(1000),
    stato VARCHAR(20) NOT NULL DEFAULT 'BOZZA',
    versione_modifica INTEGER NOT NULL DEFAULT 1,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approvato_il TIMESTAMP,
    CONSTRAINT pk_ton_pr PRIMARY KEY (polvere_revisione_id),
    CONSTRAINT uq_ton_pr_versione UNIQUE (polvere_id, numero_versione),
    CONSTRAINT fk_ton_pr_polvere FOREIGN KEY (polvere_id) REFERENCES TON.POLVERI (polvere_id),
    CONSTRAINT ck_ton_pr_resistenza CHECK (resistenza_mpa IS NULL OR resistenza_mpa > 0),
    CONSTRAINT ck_ton_pr_traslucenza CHECK (traslucenza IS NULL OR traslucenza >= 0),
    CONSTRAINT ck_ton_pr_ittria CHECK (classe_ittria_y IS NULL OR classe_ittria_y > 0),
    CONSTRAINT ck_ton_pr_revisione CHECK (versione_modifica > 0),
    CONSTRAINT ck_ton_pr_versione CHECK (numero_versione > 0),
    CONSTRAINT ck_ton_pr_stato CHECK ((stato = 'BOZZA' AND approvato_il IS NULL) OR (stato IN ('APPROVATA', 'RITIRATA') AND approvato_il IS NOT NULL))
);

CREATE TABLE TON.POLVERE_OSSIDI (
    polvere_revisione_id BIGINT NOT NULL,
    ossido_codice VARCHAR(30) NOT NULL,
    percentuale_massa DECIMAL(7,4) NOT NULL,
    CONSTRAINT pk_ton_oss PRIMARY KEY (polvere_revisione_id, ossido_codice),
    CONSTRAINT fk_ton_oss_revisione FOREIGN KEY (polvere_revisione_id) REFERENCES TON.POLVERI_REVISIONI (polvere_revisione_id),
    CONSTRAINT ck_ton_oss_quota CHECK (percentuale_massa > 0 AND percentuale_massa <= 100),
    CONSTRAINT ck_ton_oss_codice CHECK (LENGTH(TRIM(ossido_codice)) > 0 AND ossido_codice = UPPER(TRIM(ossido_codice)))
);

CREATE TABLE TON.COMPOSIZIONI (
    composizione_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    prodotto_id BIGINT NOT NULL,
    numero_versione INTEGER NOT NULL,
    note VARCHAR(1000),
    stato VARCHAR(20) NOT NULL DEFAULT 'BOZZA',
    versione_modifica INTEGER NOT NULL DEFAULT 1,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approvato_il TIMESTAMP,
    CONSTRAINT pk_ton_comp PRIMARY KEY (composizione_id),
    CONSTRAINT uq_ton_comp_versione UNIQUE (prodotto_id, numero_versione),
    CONSTRAINT uq_ton_comp_prodotto UNIQUE (composizione_id, prodotto_id),
    CONSTRAINT fk_ton_comp_prodotto FOREIGN KEY (prodotto_id) REFERENCES TON.PRODOTTI (prodotto_id),
    CONSTRAINT ck_ton_comp_revisione CHECK (versione_modifica > 0),
    CONSTRAINT ck_ton_comp_versione CHECK (numero_versione > 0),
    CONSTRAINT ck_ton_comp_stato CHECK ((stato = 'BOZZA' AND approvato_il IS NULL) OR (stato IN ('APPROVATA', 'RITIRATA') AND approvato_il IS NOT NULL))
);

CREATE TABLE TON.COMPOSIZIONE_STRATI (
    composizione_id BIGINT NOT NULL,
    numero_strato INTEGER NOT NULL,
    nome VARCHAR(100),
    CONSTRAINT pk_ton_cs PRIMARY KEY (composizione_id, numero_strato),
    CONSTRAINT fk_ton_cs_composizione FOREIGN KEY (composizione_id) REFERENCES TON.COMPOSIZIONI (composizione_id),
    CONSTRAINT ck_ton_cs_numero CHECK (numero_strato > 0)
);

CREATE TABLE TON.COMPOSIZIONE_INGREDIENTI (
    composizione_id BIGINT NOT NULL,
    numero_strato INTEGER NOT NULL,
    polvere_revisione_id BIGINT NOT NULL,
    percentuale_massa DECIMAL(7,4) NOT NULL,
    CONSTRAINT pk_ton_ci PRIMARY KEY (composizione_id, numero_strato, polvere_revisione_id),
    CONSTRAINT fk_ton_ci_strato FOREIGN KEY (composizione_id, numero_strato) REFERENCES TON.COMPOSIZIONE_STRATI (composizione_id, numero_strato),
    CONSTRAINT fk_ton_ci_polvere FOREIGN KEY (polvere_revisione_id) REFERENCES TON.POLVERI_REVISIONI (polvere_revisione_id),
    CONSTRAINT ck_ton_ci_quota CHECK (percentuale_massa > 0 AND percentuale_massa <= 100)
);

CREATE TABLE TON.MODELLI_DISCO (
    modello_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    codice VARCHAR(100) NOT NULL,
    numero_versione INTEGER NOT NULL,
    diametro_pressatura_mm DECIMAL(6,2) NOT NULL,
    sovramateriale_superiore_mm DECIMAL(6,2) NOT NULL,
    sovramateriale_inferiore_mm DECIMAL(6,2) NOT NULL,
    pressione_kg_cm2 DECIMAL(10,3) NOT NULL,
    grammi_per_mm DECIMAL(12,5) NOT NULL,
    note VARCHAR(1000),
    stato VARCHAR(20) NOT NULL DEFAULT 'BOZZA',
    versione_modifica INTEGER NOT NULL DEFAULT 1,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approvato_il TIMESTAMP,
    CONSTRAINT pk_ton_md PRIMARY KEY (modello_id),
    CONSTRAINT uq_ton_md_versione UNIQUE (codice, numero_versione),
    CONSTRAINT ck_ton_md_misure CHECK (diametro_pressatura_mm > 0 AND pressione_kg_cm2 > 0 AND grammi_per_mm > 0 AND sovramateriale_superiore_mm >= 0 AND sovramateriale_inferiore_mm >= 0),
    CONSTRAINT ck_ton_md_revisione CHECK (versione_modifica > 0),
    CONSTRAINT ck_ton_md_versione CHECK (numero_versione > 0),
    CONSTRAINT ck_ton_md_codice CHECK (LENGTH(TRIM(codice)) > 0 AND codice = UPPER(TRIM(codice))),
    CONSTRAINT ck_ton_md_stato CHECK ((stato = 'BOZZA' AND approvato_il IS NULL) OR (stato IN ('APPROVATA', 'RITIRATA') AND approvato_il IS NOT NULL))
);

CREATE TABLE TON.MODELLO_STRATI (
    modello_id BIGINT NOT NULL,
    numero_strato INTEGER NOT NULL,
    percentuale_altezza DECIMAL(7,4) NOT NULL,
    CONSTRAINT pk_ton_ms PRIMARY KEY (modello_id, numero_strato),
    CONSTRAINT fk_ton_ms_modello FOREIGN KEY (modello_id) REFERENCES TON.MODELLI_DISCO (modello_id),
    CONSTRAINT ck_ton_ms_numero CHECK (numero_strato > 0),
    CONSTRAINT ck_ton_ms_quota CHECK (percentuale_altezza > 0 AND percentuale_altezza <= 100)
);

CREATE TABLE TON.MODELLO_SOVRAMATERIALI (
    sovramateriale_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    modello_id BIGINT NOT NULL,
    altezza_da_mm DECIMAL(6,2) NOT NULL,
    altezza_a_mm DECIMAL(6,2) NOT NULL,
    superiore_mm DECIMAL(6,2) NOT NULL,
    inferiore_mm DECIMAL(6,2) NOT NULL,
    CONSTRAINT pk_ton_sm PRIMARY KEY (sovramateriale_id),
    CONSTRAINT uq_ton_sm_fascia UNIQUE (modello_id, altezza_da_mm, altezza_a_mm),
    CONSTRAINT fk_ton_sm_modello FOREIGN KEY (modello_id) REFERENCES TON.MODELLI_DISCO (modello_id),
    CONSTRAINT ck_ton_sm_fascia CHECK (altezza_da_mm >= 0 AND altezza_a_mm > altezza_da_mm),
    CONSTRAINT ck_ton_sm_misure CHECK (superiore_mm >= 0 AND inferiore_mm >= 0)
);

CREATE TABLE TON.CONFIGURAZIONI_ARTICOLO (
    configurazione_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    articolo_id BIGINT NOT NULL,
    prodotto_id BIGINT NOT NULL,
    numero_versione INTEGER NOT NULL,
    composizione_id BIGINT NOT NULL,
    modello_id BIGINT NOT NULL,
    versione_calcolo VARCHAR(50) NOT NULL,
    note VARCHAR(1000),
    stato VARCHAR(20) NOT NULL DEFAULT 'BOZZA',
    versione_modifica INTEGER NOT NULL DEFAULT 1,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approvato_il TIMESTAMP,
    CONSTRAINT pk_ton_cfg PRIMARY KEY (configurazione_id),
    CONSTRAINT uq_ton_cfg_versione UNIQUE (articolo_id, numero_versione),
    CONSTRAINT uq_ton_cfg_articolo UNIQUE (articolo_id, configurazione_id),
    CONSTRAINT fk_ton_cfg_articolo FOREIGN KEY (articolo_id, prodotto_id) REFERENCES TON.ARTICOLI (articolo_id, prodotto_id),
    CONSTRAINT fk_ton_cfg_composizione FOREIGN KEY (composizione_id, prodotto_id) REFERENCES TON.COMPOSIZIONI (composizione_id, prodotto_id),
    CONSTRAINT fk_ton_cfg_modello FOREIGN KEY (modello_id) REFERENCES TON.MODELLI_DISCO (modello_id),
    CONSTRAINT ck_ton_cfg_calcolo CHECK (LENGTH(TRIM(versione_calcolo)) > 0),
    CONSTRAINT ck_ton_cfg_revisione CHECK (versione_modifica > 0),
    CONSTRAINT ck_ton_cfg_versione CHECK (numero_versione > 0),
    CONSTRAINT ck_ton_cfg_stato CHECK ((stato = 'BOZZA' AND approvato_il IS NULL) OR (stato IN ('APPROVATA', 'RITIRATA') AND approvato_il IS NOT NULL))
);

CREATE TABLE TON.FORNI (
    forno_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    codice VARCHAR(50) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    note VARCHAR(1000),
    attivo BOOLEAN NOT NULL DEFAULT TRUE,
    versione_modifica INTEGER NOT NULL DEFAULT 1,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ton_for PRIMARY KEY (forno_id),
    CONSTRAINT uq_ton_for_codice UNIQUE (codice),
    CONSTRAINT ck_ton_for_nome CHECK (LENGTH(TRIM(nome)) > 0),
    CONSTRAINT ck_ton_for_revisione CHECK (versione_modifica > 0),
    CONSTRAINT ck_ton_for_codice CHECK (LENGTH(TRIM(codice)) > 0 AND codice = UPPER(TRIM(codice)))
);

CREATE TABLE TON.PROGRAMMI_COTTURA (
    programma_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    codice VARCHAR(80) NOT NULL,
    numero_versione INTEGER NOT NULL,
    nome VARCHAR(200) NOT NULL,
    note VARCHAR(1000),
    stato VARCHAR(20) NOT NULL DEFAULT 'BOZZA',
    versione_modifica INTEGER NOT NULL DEFAULT 1,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approvato_il TIMESTAMP,
    CONSTRAINT pk_ton_prog PRIMARY KEY (programma_id),
    CONSTRAINT uq_ton_prog_versione UNIQUE (codice, numero_versione),
    CONSTRAINT ck_ton_prog_nome CHECK (LENGTH(TRIM(nome)) > 0),
    CONSTRAINT ck_ton_prog_revisione CHECK (versione_modifica > 0),
    CONSTRAINT ck_ton_prog_versione CHECK (numero_versione > 0),
    CONSTRAINT ck_ton_prog_codice CHECK (LENGTH(TRIM(codice)) > 0 AND codice = UPPER(TRIM(codice))),
    CONSTRAINT ck_ton_prog_stato CHECK ((stato = 'BOZZA' AND approvato_il IS NULL) OR (stato IN ('APPROVATA', 'RITIRATA') AND approvato_il IS NOT NULL))
);

CREATE TABLE TON.PROGRAMMA_PASSI (
    programma_id BIGINT NOT NULL,
    numero_passo INTEGER NOT NULL,
    temperatura_obiettivo_c DECIMAL(7,2) NOT NULL,
    durata_rampa_minuti INTEGER NOT NULL,
    durata_mantenimento_minuti INTEGER NOT NULL,
    CONSTRAINT pk_ton_pass PRIMARY KEY (programma_id, numero_passo),
    CONSTRAINT fk_ton_pass_programma FOREIGN KEY (programma_id) REFERENCES TON.PROGRAMMI_COTTURA (programma_id),
    CONSTRAINT ck_ton_pass_numero CHECK (numero_passo > 0),
    CONSTRAINT ck_ton_pass_parametri CHECK (temperatura_obiettivo_c > 0 AND durata_rampa_minuti >= 0 AND durata_mantenimento_minuti >= 0)
);

CREATE TABLE TON.PRODUZIONI (
    produzione_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    numero VARCHAR(50) NOT NULL,
    linea_id BIGINT NOT NULL,
    data_produzione DATE NOT NULL,
    stato VARCHAR(20) NOT NULL DEFAULT 'BOZZA',
    confermata_il TIMESTAMP,
    annullata_il TIMESTAMP,
    motivo_annullamento VARCHAR(1000),
    note VARCHAR(1000),
    versione_modifica INTEGER NOT NULL DEFAULT 1,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ton_prod PRIMARY KEY (produzione_id),
    CONSTRAINT uq_ton_prod_numero UNIQUE (numero),
    CONSTRAINT fk_ton_prod_linea FOREIGN KEY (linea_id) REFERENCES TON.LINEE (linea_id),
    CONSTRAINT ck_ton_prod_numero CHECK (LENGTH(TRIM(numero)) > 0),
    CONSTRAINT ck_ton_prod_stato CHECK ((stato = 'BOZZA' AND confermata_il IS NULL AND annullata_il IS NULL AND motivo_annullamento IS NULL) OR (stato = 'CONFERMATA' AND confermata_il IS NOT NULL AND annullata_il IS NULL AND motivo_annullamento IS NULL) OR (stato = 'ANNULLATA' AND annullata_il IS NOT NULL AND motivo_annullamento IS NOT NULL AND LENGTH(TRIM(motivo_annullamento)) > 0 AND (confermata_il IS NULL OR annullata_il >= confermata_il))),
    CONSTRAINT ck_ton_prod_revisione CHECK (versione_modifica > 0)
);

CREATE TABLE TON.PRODUZIONE_RIGHE (
    produzione_riga_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    produzione_id BIGINT NOT NULL,
    numero_riga INTEGER NOT NULL,
    configurazione_id BIGINT NOT NULL,
    quantita_prodotta INTEGER NOT NULL,
    dosi_calcolate CLOB,
    CONSTRAINT pk_ton_rig PRIMARY KEY (produzione_riga_id),
    CONSTRAINT uq_ton_rig_numero UNIQUE (produzione_id, numero_riga),
    CONSTRAINT fk_ton_rig_produzione FOREIGN KEY (produzione_id) REFERENCES TON.PRODUZIONI (produzione_id),
    CONSTRAINT fk_ton_rig_configurazione FOREIGN KEY (configurazione_id) REFERENCES TON.CONFIGURAZIONI_ARTICOLO (configurazione_id),
    CONSTRAINT ck_ton_rig_quantita CHECK (quantita_prodotta > 0 AND numero_riga > 0)
);

CREATE TABLE TON.SCARTI_PRODUZIONE (
    scarto_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    produzione_riga_id BIGINT NOT NULL,
    quantita INTEGER NOT NULL,
    registrato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    motivo VARCHAR(1000) NOT NULL,
    annullato_il TIMESTAMP,
    motivo_annullamento VARCHAR(1000),
    CONSTRAINT pk_ton_scp PRIMARY KEY (scarto_id),
    CONSTRAINT fk_ton_scp_riga FOREIGN KEY (produzione_riga_id) REFERENCES TON.PRODUZIONE_RIGHE (produzione_riga_id),
    CONSTRAINT ck_ton_scp_quantita CHECK (quantita > 0),
    CONSTRAINT ck_ton_scp_motivo CHECK (LENGTH(TRIM(motivo)) > 0),
    CONSTRAINT ck_ton_scp_annullamento CHECK ((annullato_il IS NULL AND motivo_annullamento IS NULL) OR (annullato_il IS NOT NULL AND annullato_il >= registrato_il AND motivo_annullamento IS NOT NULL AND LENGTH(TRIM(motivo_annullamento)) > 0))
);

CREATE TABLE TON.CICLI_COTTURA (
    ciclo_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    numero VARCHAR(50) NOT NULL,
    forno_id BIGINT NOT NULL,
    programma_id BIGINT,
    data_prevista DATE NOT NULL,
    stato VARCHAR(20) NOT NULL DEFAULT 'BOZZA',
    iniziato_il TIMESTAMP,
    terminato_il TIMESTAMP,
    temperatura_massima_rilevata_c DECIMAL(7,2),
    annullato_il TIMESTAMP,
    motivo_annullamento VARCHAR(1000),
    note VARCHAR(1000),
    versione_modifica INTEGER NOT NULL DEFAULT 1,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ton_cic PRIMARY KEY (ciclo_id),
    CONSTRAINT uq_ton_cic_numero UNIQUE (numero),
    CONSTRAINT fk_ton_cic_forno FOREIGN KEY (forno_id) REFERENCES TON.FORNI (forno_id),
    CONSTRAINT fk_ton_cic_programma FOREIGN KEY (programma_id) REFERENCES TON.PROGRAMMI_COTTURA (programma_id),
    CONSTRAINT ck_ton_cic_numero CHECK (LENGTH(TRIM(numero)) > 0),
    CONSTRAINT ck_ton_cic_temperatura CHECK (temperatura_massima_rilevata_c IS NULL OR temperatura_massima_rilevata_c > 0),
    CONSTRAINT ck_ton_cic_stato CHECK ((stato = 'BOZZA' AND iniziato_il IS NULL AND terminato_il IS NULL AND annullato_il IS NULL AND motivo_annullamento IS NULL) OR (stato = 'IN_CORSO' AND programma_id IS NOT NULL AND iniziato_il IS NOT NULL AND terminato_il IS NULL AND annullato_il IS NULL AND motivo_annullamento IS NULL) OR (stato = 'COMPLETATA' AND programma_id IS NOT NULL AND iniziato_il IS NOT NULL AND terminato_il IS NOT NULL AND terminato_il >= iniziato_il AND annullato_il IS NULL AND motivo_annullamento IS NULL) OR (stato = 'ANNULLATA' AND iniziato_il IS NULL AND terminato_il IS NULL AND annullato_il IS NOT NULL AND motivo_annullamento IS NOT NULL AND LENGTH(TRIM(motivo_annullamento)) > 0)),
    CONSTRAINT ck_ton_cic_revisione CHECK (versione_modifica > 0)
);

CREATE TABLE TON.LOTTI_COTTURA (
    lotto_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    codice VARCHAR(50) NOT NULL,
    ciclo_id BIGINT NOT NULL,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    note VARCHAR(1000),
    CONSTRAINT pk_ton_lot PRIMARY KEY (lotto_id),
    CONSTRAINT uq_ton_lot_codice UNIQUE (codice),
    CONSTRAINT uq_ton_lot_ciclo UNIQUE (ciclo_id, lotto_id),
    CONSTRAINT fk_ton_lot_ciclo FOREIGN KEY (ciclo_id) REFERENCES TON.CICLI_COTTURA (ciclo_id),
    CONSTRAINT ck_ton_lot_codice CHECK (LENGTH(TRIM(codice)) > 0 AND codice = UPPER(TRIM(codice)))
);

CREATE TABLE TON.CARICHI_COTTURA (
    carico_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    ciclo_id BIGINT NOT NULL,
    produzione_riga_id BIGINT NOT NULL,
    quantita_caricata INTEGER NOT NULL,
    quantita_buona INTEGER,
    quantita_scartata INTEGER,
    lotto_id BIGINT,
    note_esito VARCHAR(1000),
    CONSTRAINT pk_ton_car PRIMARY KEY (carico_id),
    CONSTRAINT fk_ton_car_ciclo FOREIGN KEY (ciclo_id) REFERENCES TON.CICLI_COTTURA (ciclo_id),
    CONSTRAINT fk_ton_car_riga FOREIGN KEY (produzione_riga_id) REFERENCES TON.PRODUZIONE_RIGHE (produzione_riga_id),
    CONSTRAINT fk_ton_car_lotto FOREIGN KEY (ciclo_id, lotto_id) REFERENCES TON.LOTTI_COTTURA (ciclo_id, lotto_id),
    CONSTRAINT ck_ton_car_quantita CHECK (quantita_caricata > 0),
    CONSTRAINT ck_ton_car_esito CHECK ((quantita_buona IS NULL AND quantita_scartata IS NULL) OR (quantita_buona IS NOT NULL AND quantita_scartata IS NOT NULL AND quantita_buona >= 0 AND quantita_scartata >= 0 AND BIGINT(quantita_buona) + BIGINT(quantita_scartata) = BIGINT(quantita_caricata)))
);

CREATE TABLE TON.MODELLI_DOCUMENTO (
    modello_documento_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    codice VARCHAR(100) NOT NULL,
    numero_versione INTEGER NOT NULL,
    nome VARCHAR(150) NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    contenuto_modello CLOB NOT NULL,
    stato VARCHAR(20) NOT NULL DEFAULT 'BOZZA',
    versione_modifica INTEGER NOT NULL DEFAULT 1,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approvato_il TIMESTAMP,
    CONSTRAINT pk_ton_docm PRIMARY KEY (modello_documento_id),
    CONSTRAINT uq_ton_docm_versione UNIQUE (codice, numero_versione),
    CONSTRAINT uq_ton_docm_tipo UNIQUE (modello_documento_id, tipo),
    CONSTRAINT ck_ton_docm_tipo CHECK (tipo IN ('PRODUZIONE', 'COTTURA')),
    CONSTRAINT ck_ton_docm_nome CHECK (LENGTH(TRIM(nome)) > 0),
    CONSTRAINT ck_ton_docm_revisione CHECK (versione_modifica > 0),
    CONSTRAINT ck_ton_docm_versione CHECK (numero_versione > 0),
    CONSTRAINT ck_ton_docm_codice CHECK (LENGTH(TRIM(codice)) > 0 AND codice = UPPER(TRIM(codice))),
    CONSTRAINT ck_ton_docm_stato CHECK ((stato = 'BOZZA' AND approvato_il IS NULL) OR (stato IN ('APPROVATA', 'RITIRATA') AND approvato_il IS NOT NULL))
);

CREATE TABLE TON.DOCUMENTI (
    documento_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    modello_documento_id BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    produzione_id BIGINT,
    ciclo_id BIGINT,
    generato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    contenuto_emesso CLOB NOT NULL,
    tipo_contenuto VARCHAR(100) NOT NULL,
    CONSTRAINT pk_ton_doc PRIMARY KEY (documento_id),
    CONSTRAINT fk_ton_doc_modello FOREIGN KEY (modello_documento_id, tipo) REFERENCES TON.MODELLI_DOCUMENTO (modello_documento_id, tipo),
    CONSTRAINT fk_ton_doc_produzione FOREIGN KEY (produzione_id) REFERENCES TON.PRODUZIONI (produzione_id),
    CONSTRAINT fk_ton_doc_ciclo FOREIGN KEY (ciclo_id) REFERENCES TON.CICLI_COTTURA (ciclo_id),
    CONSTRAINT ck_ton_doc_origine CHECK ((tipo = 'PRODUZIONE' AND produzione_id IS NOT NULL AND ciclo_id IS NULL) OR (tipo = 'COTTURA' AND produzione_id IS NULL AND ciclo_id IS NOT NULL)),
    CONSTRAINT ck_ton_doc_contenuto CHECK (LENGTH(TRIM(tipo_contenuto)) > 0)
);

ALTER TABLE TON.ARTICOLI ADD CONSTRAINT fk_ton_art_predefinita FOREIGN KEY (articolo_id, configurazione_predefinita_id) REFERENCES TON.CONFIGURAZIONI_ARTICOLO (articolo_id, configurazione_id);

CREATE INDEX TON.ix_ton_produzioni_data_stato ON TON.PRODUZIONI (data_produzione, stato);
CREATE INDEX TON.ix_ton_cicli_stato_forno_data ON TON.CICLI_COTTURA (stato, forno_id, data_prevista);
CREATE INDEX TON.ix_ton_documenti_data ON TON.DOCUMENTI (generato_il);
