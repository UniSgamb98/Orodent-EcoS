CREATE SCHEMA COSTI;

-- Each save appends an immutable economic snapshot, independently of TON technical revisions.
CREATE TABLE COSTI.POLVERI_PREZZI (
    prezzo_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    polvere_id BIGINT NOT NULL,
    numero_versione INTEGER NOT NULL,
    data_riferimento DATE NOT NULL,
    modalita VARCHAR(10) NOT NULL,
    prezzo_semplice DECIMAL(15,4),
    quantita_semplice DECIMAL(15,3),
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_cos_pp PRIMARY KEY (prezzo_id),
    CONSTRAINT uq_cos_pp_versione UNIQUE (polvere_id, numero_versione),
    CONSTRAINT fk_cos_pp_polvere FOREIGN KEY (polvere_id) REFERENCES TON.POLVERI(polvere_id),
    CONSTRAINT ck_cos_pp_versione CHECK (numero_versione > 0),
    CONSTRAINT ck_cos_pp_modalita CHECK (modalita IN ('SEMPLICE','AVANZATA')),
    CONSTRAINT ck_cos_pp_prezzo CHECK (prezzo_semplice IS NULL OR prezzo_semplice > 0),
    CONSTRAINT ck_cos_pp_quantita CHECK (quantita_semplice IS NULL OR quantita_semplice >= 0),
    CONSTRAINT ck_cos_pp_semplice CHECK (modalita <> 'SEMPLICE' OR prezzo_semplice IS NOT NULL)
);

-- Distinct purchases of the same lot may have distinct prices. The lot is not a unique key.
CREATE TABLE COSTI.POLVERI_LOTTI (
    prezzo_id BIGINT NOT NULL,
    posizione INTEGER NOT NULL,
    lotto VARCHAR(100) NOT NULL DEFAULT '',
    quantita_kg DECIMAL(15,3) NOT NULL,
    prezzo_kg DECIMAL(15,4) NOT NULL,
    CONSTRAINT pk_cos_pl PRIMARY KEY (prezzo_id, posizione),
    CONSTRAINT fk_cos_pl_prezzo FOREIGN KEY (prezzo_id) REFERENCES COSTI.POLVERI_PREZZI(prezzo_id),
    CONSTRAINT ck_cos_pl_posizione CHECK (posizione > 0),
    CONSTRAINT ck_cos_pl_quantita CHECK (quantita_kg >= 0),
    CONSTRAINT ck_cos_pl_prezzo CHECK (prezzo_kg > 0)
);
