-- Base totals and reference prices are independent from optional lot detail.
ALTER TABLE COSTI.POLVERI_PREZZI DROP CONSTRAINT ck_cos_pp_semplice;
ALTER TABLE COSTI.POLVERI_PREZZI ADD COLUMN modello_dati INTEGER NOT NULL DEFAULT 1;
ALTER TABLE COSTI.POLVERI_PREZZI ADD COLUMN prezzo_riferimento DECIMAL(19,8);
ALTER TABLE COSTI.POLVERI_PREZZI ADD COLUMN quantita_totale DECIMAL(15,3);
ALTER TABLE COSTI.POLVERI_PREZZI ADD CONSTRAINT ck_cos_pp_modello CHECK (modello_dati IN (1,2));
ALTER TABLE COSTI.POLVERI_PREZZI ADD CONSTRAINT ck_cos_pp_rif CHECK (prezzo_riferimento IS NULL OR prezzo_riferimento>0);
ALTER TABLE COSTI.POLVERI_PREZZI ADD CONSTRAINT ck_cos_pp_tot CHECK (quantita_totale IS NULL OR quantita_totale>=0);

CREATE TABLE COSTI.ORDINI_POLVERI (
    ordine_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fornitore VARCHAR(80) NOT NULL,
    numero_ordine VARCHAR(80) NOT NULL,
    posizione INTEGER NOT NULL,
    CONSTRAINT uq_cos_ord_chiave UNIQUE (fornitore,numero_ordine,posizione),
    CONSTRAINT ck_cos_ord_pos CHECK (posizione>0)
);
CREATE TABLE COSTI.ORDINI_POLVERI_REVISIONI (
    ordine_id BIGINT NOT NULL,
    numero_versione INTEGER NOT NULL,
    polvere_id BIGINT NOT NULL,
    codice_sorgente VARCHAR(80) NOT NULL,
    descrizione VARCHAR(500) NOT NULL,
    data_ordine DATE NOT NULL,
    consegna_prevista DATE,
    consegna_manuale DATE,
    quantita_ordinata DECIMAL(15,3) NOT NULL,
    quantita_consegnata DECIMAL(15,3) NOT NULL,
    quantita_residua DECIMAL(15,3) NOT NULL,
    prezzo_kg DECIMAL(15,4) NOT NULL,
    escluso BOOLEAN NOT NULL DEFAULT FALSE,
    origine VARCHAR(20) NOT NULL,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_cos_orv PRIMARY KEY (ordine_id,numero_versione),
    CONSTRAINT fk_cos_orv_ord FOREIGN KEY (ordine_id) REFERENCES COSTI.ORDINI_POLVERI(ordine_id),
    CONSTRAINT fk_cos_orv_pol FOREIGN KEY (polvere_id) REFERENCES TON.POLVERI(polvere_id),
    CONSTRAINT ck_cos_orv_ver CHECK (numero_versione>0),
    CONSTRAINT ck_cos_orv_qty CHECK (quantita_ordinata>=0 AND quantita_consegnata>=0 AND quantita_residua>=0),
    CONSTRAINT ck_cos_orv_pre CHECK (prezzo_kg>=0),
    CONSTRAINT ck_cos_orv_ori CHECK (origine IN ('CSV','MODIFICA'))
);
CREATE TABLE COSTI.POLVERI_CODICI_FORNITORE (
    fornitore VARCHAR(80) NOT NULL,
    codice_sorgente VARCHAR(80) NOT NULL,
    polvere_id BIGINT NOT NULL,
    CONSTRAINT pk_cos_cod PRIMARY KEY (fornitore,codice_sorgente),
    CONSTRAINT fk_cos_cod_pol FOREIGN KEY (polvere_id) REFERENCES TON.POLVERI(polvere_id)
);
