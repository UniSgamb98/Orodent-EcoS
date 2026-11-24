CREATE TABLE TON.MODELLI_MARCATURA (
    modello_id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codice VARCHAR(50) NOT NULL UNIQUE,
    versione_modifica INT NOT NULL DEFAULT 1
);
CREATE TABLE TON.MARCATURA_VERSIONI (
    marcatura_versione_id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    modello_id BIGINT NOT NULL REFERENCES TON.MODELLI_MARCATURA(modello_id),
    numero_versione INT NOT NULL,
    numero_revisione INT NOT NULL,
    stato VARCHAR(20) NOT NULL,
    nome VARCHAR(150) NOT NULL,
    descrizione VARCHAR(1000) NOT NULL,
    attivo BOOLEAN NOT NULL,
    testo VARCHAR(2000) NOT NULL,
    calcolo_versione_id BIGINT REFERENCES TON.REGOLA_CUBATURA_VERSIONI(calcolo_versione_id),
    decimali VARCHAR(4000) NOT NULL,
    separatore VARCHAR(1) NOT NULL,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ton_marcatura_rev UNIQUE(modello_id,numero_versione,numero_revisione),
    CONSTRAINT ck_ton_marcatura_rev CHECK(numero_versione>0 AND numero_revisione>0 AND stato IN ('BOZZA','APPROVATA')),
    CONSTRAINT ck_ton_marcatura_sep CHECK(separatore IN ('.',',')),
    CONSTRAINT ck_ton_marcatura_testo CHECK(stato<>'APPROVATA' OR LENGTH(TRIM(testo))>0)
);
