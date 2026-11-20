-- Product-owned production specifications. Article configurations remain immutable historical references.
CREATE TABLE TON.CONFIGURAZIONI_PRODOTTO (
    configurazione_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
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
    linea_id BIGINT,
    linea_revisione INTEGER,
    prodotto_revisione INTEGER,
    CONSTRAINT pk_ton_cfg_pro PRIMARY KEY (configurazione_id),
    CONSTRAINT uq_ton_cfg_pro_ver UNIQUE (prodotto_id,numero_versione),
    CONSTRAINT uq_ton_cfg_pro_id UNIQUE (prodotto_id,configurazione_id),
    CONSTRAINT fk_ton_cfg_pro_parent FOREIGN KEY (prodotto_id) REFERENCES TON.PRODOTTI(prodotto_id),
    CONSTRAINT fk_ton_cfg_pro_comp FOREIGN KEY (composizione_id,prodotto_id) REFERENCES TON.COMPOSIZIONI(composizione_id,prodotto_id),
    CONSTRAINT fk_ton_cfg_pro_model FOREIGN KEY (modello_id) REFERENCES TON.MODELLI_DISCO(modello_id),
    CONSTRAINT fk_ton_cfg_pro_line_hist FOREIGN KEY (linea_id,linea_revisione) REFERENCES TON.LINEE_REVISIONI(linea_id,numero_revisione),
    CONSTRAINT fk_ton_cfg_pro_hist FOREIGN KEY (prodotto_id,prodotto_revisione) REFERENCES TON.PRODOTTI_REVISIONI(prodotto_id,numero_revisione),
    CONSTRAINT ck_ton_cfg_pro_values CHECK (numero_versione>0 AND versione_modifica>0 AND LENGTH(TRIM(versione_calcolo))>0),
    CONSTRAINT ck_ton_cfg_pro_state CHECK ((stato='BOZZA' AND approvato_il IS NULL) OR (stato IN ('APPROVATA','RITIRATA') AND approvato_il IS NOT NULL)),
    CONSTRAINT ck_ton_cfg_pro_hist CHECK ((linea_id IS NULL AND linea_revisione IS NULL AND prodotto_revisione IS NULL) OR (linea_id IS NOT NULL AND linea_revisione IS NOT NULL AND prodotto_revisione IS NOT NULL AND linea_revisione>0 AND prodotto_revisione>0))
);
CREATE TABLE TON.CONFIGURAZIONI_MIGRATE (
    articolo_configurazione_id BIGINT NOT NULL PRIMARY KEY,
    prodotto_configurazione_id BIGINT NOT NULL,
    CONSTRAINT fk_ton_cfg_map_old FOREIGN KEY (articolo_configurazione_id) REFERENCES TON.CONFIGURAZIONI_ARTICOLO(configurazione_id),
    CONSTRAINT fk_ton_cfg_map_new FOREIGN KEY (prodotto_configurazione_id) REFERENCES TON.CONFIGURAZIONI_PRODOTTO(configurazione_id)
);
ALTER TABLE TON.PRODOTTI ADD COLUMN configurazione_predefinita_id BIGINT;
ALTER TABLE TON.PRODOTTI ADD COLUMN specifiche_da_verificare BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE TON.PRODOTTI ADD CONSTRAINT fk_ton_pro_cfg_default FOREIGN KEY (prodotto_id,configurazione_predefinita_id) REFERENCES TON.CONFIGURAZIONI_PRODOTTO(prodotto_id,configurazione_id);
ALTER TABLE TON.PRODOTTI_REVISIONI ADD COLUMN configurazione_predefinita_id BIGINT;
ALTER TABLE TON.PRODOTTI_REVISIONI ADD COLUMN specifiche_da_verificare BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE TON.PRODOTTI_REVISIONI ADD CONSTRAINT fk_ton_pro_hist_default FOREIGN KEY (prodotto_id,configurazione_predefinita_id) REFERENCES TON.CONFIGURAZIONI_PRODOTTO(prodotto_id,configurazione_id);
CREATE TABLE TON.PIANO_PRODOTTO_RIGHE (
    piano_id BIGINT NOT NULL,
    numero_riga INTEGER NOT NULL,
    articolo_id BIGINT NOT NULL,
    prodotto_id BIGINT NOT NULL,
    articolo_revisione INTEGER NOT NULL,
    configurazione_id BIGINT NOT NULL,
    quantita_richiesta INTEGER NOT NULL,
    CONSTRAINT pk_ton_piano_pro_righe PRIMARY KEY (piano_id,numero_riga),
    CONSTRAINT uq_ton_piano_pro_art UNIQUE (piano_id,articolo_id),
    CONSTRAINT fk_ton_piano_pro_plan FOREIGN KEY (piano_id) REFERENCES TON.PIANI_PRODUZIONE(piano_id),
    CONSTRAINT fk_ton_piano_pro_article FOREIGN KEY (articolo_id,prodotto_id) REFERENCES TON.ARTICOLI(articolo_id,prodotto_id),
    CONSTRAINT fk_ton_piano_pro_art_hist FOREIGN KEY (articolo_id,articolo_revisione) REFERENCES TON.ARTICOLI_REVISIONI(articolo_id,numero_revisione),
    CONSTRAINT fk_ton_piano_pro_spec FOREIGN KEY (prodotto_id,configurazione_id) REFERENCES TON.CONFIGURAZIONI_PRODOTTO(prodotto_id,configurazione_id),
    CONSTRAINT ck_ton_piano_pro_values CHECK (numero_riga>0 AND articolo_revisione>0 AND quantita_richiesta>0)
);
