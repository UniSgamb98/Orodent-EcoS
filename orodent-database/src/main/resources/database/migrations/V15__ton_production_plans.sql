-- Requested work is separate from physically executed TON.PRODUZIONI.
CREATE TABLE TON.PIANI_PRODUZIONE (
    piano_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    prodotto_id BIGINT NOT NULL,
    diametro_mm DECIMAL(6,2) NOT NULL,
    data_preparazione DATE NOT NULL,
    stato VARCHAR(20) NOT NULL DEFAULT 'BOZZA',
    modello_documento_id BIGINT,
    note VARCHAR(1000),
    etichetta_prodotto VARCHAR(350) NOT NULL,
    numero_formati INTEGER NOT NULL,
    numero_dischi INTEGER NOT NULL,
    titolo_documento VARCHAR(300) NOT NULL,
    contenuto_piano CLOB NOT NULL,
    versione_modifica INTEGER NOT NULL DEFAULT 1,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    preparato_il TIMESTAMP,
    CONSTRAINT pk_ton_piani PRIMARY KEY (piano_id),
    CONSTRAINT fk_ton_piano_prodotto FOREIGN KEY (prodotto_id) REFERENCES TON.PRODOTTI (prodotto_id),
    CONSTRAINT fk_ton_piano_modello FOREIGN KEY (modello_documento_id) REFERENCES TON.MODELLI_DOCUMENTO (modello_documento_id),
    CONSTRAINT ck_ton_piano_stato CHECK ((stato='BOZZA' AND preparato_il IS NULL) OR (stato='PREPARATA' AND preparato_il IS NOT NULL)),
    CONSTRAINT ck_ton_piano_valori CHECK (diametro_mm>0 AND versione_modifica>0 AND numero_formati>=0 AND numero_dischi>=0 AND (stato='BOZZA' OR (numero_dischi>0 AND numero_formati>0)))
);
CREATE TABLE TON.PIANO_PRODUZIONE_RIGHE (
    piano_id BIGINT NOT NULL,
    numero_riga INTEGER NOT NULL,
    configurazione_id BIGINT NOT NULL,
    quantita_richiesta INTEGER NOT NULL,
    CONSTRAINT pk_ton_piano_righe PRIMARY KEY (piano_id,numero_riga),
    CONSTRAINT uq_ton_piano_spec UNIQUE (piano_id,configurazione_id),
    CONSTRAINT fk_ton_piano_riga FOREIGN KEY (piano_id) REFERENCES TON.PIANI_PRODUZIONE (piano_id),
    CONSTRAINT fk_ton_piano_spec FOREIGN KEY (configurazione_id) REFERENCES TON.CONFIGURAZIONI_ARTICOLO (configurazione_id),
    CONSTRAINT ck_ton_piano_riga CHECK (numero_riga>0 AND quantita_richiesta>0)
);
CREATE TABLE TON.PIANI_DOCUMENTI (
    piano_id BIGINT NOT NULL,
    contenuto_html CLOB NOT NULL,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ton_piano_doc PRIMARY KEY (piano_id),
    CONSTRAINT fk_ton_piano_doc FOREIGN KEY (piano_id) REFERENCES TON.PIANI_PRODUZIONE (piano_id)
);
CREATE INDEX ix_ton_piani_data ON TON.PIANI_PRODUZIONE (data_preparazione,stato);
