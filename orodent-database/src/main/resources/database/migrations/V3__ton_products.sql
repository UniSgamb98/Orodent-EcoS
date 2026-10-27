CREATE SCHEMA TON;

CREATE TABLE TON.PRODOTTI (
    prodotto_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codice VARCHAR(50) NOT NULL,
    descrizione VARCHAR(200),
    attivo BOOLEAN NOT NULL DEFAULT TRUE,
    versione_modifica INTEGER NOT NULL DEFAULT 1,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ton_prodotto_codice UNIQUE (codice),
    CONSTRAINT ck_ton_prodotto_codice CHECK (LENGTH(TRIM(codice)) > 0 AND codice = UPPER(TRIM(codice))),
    CONSTRAINT ck_ton_prodotto_versione CHECK (versione_modifica > 0)
);
