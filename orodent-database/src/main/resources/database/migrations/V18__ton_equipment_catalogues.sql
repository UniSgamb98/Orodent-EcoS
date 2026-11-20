-- Physical presses and moulds. Existing furnace IDs and firing links are unchanged.
CREATE TABLE TON.PRESSE (
    pressa_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    codice VARCHAR(50) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    alesaggio_mm DECIMAL(10,3) NOT NULL,
    note VARCHAR(1000),
    attivo BOOLEAN NOT NULL DEFAULT TRUE,
    versione_modifica INTEGER NOT NULL DEFAULT 1,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ton_presse PRIMARY KEY (pressa_id),
    CONSTRAINT uq_ton_presse_codice UNIQUE (codice),
    CONSTRAINT ck_ton_presse_codice CHECK (LENGTH(TRIM(codice))>0 AND codice=UPPER(TRIM(codice))),
    CONSTRAINT ck_ton_presse_nome CHECK (LENGTH(TRIM(nome))>0),
    CONSTRAINT ck_ton_presse_alesaggio CHECK (alesaggio_mm>0),
    CONSTRAINT ck_ton_presse_versione CHECK (versione_modifica>0)
);
CREATE TABLE TON.STAMPI (
    stampo_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    codice VARCHAR(50) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    diametro_mm DECIMAL(10,3) NOT NULL,
    note VARCHAR(1000),
    attivo BOOLEAN NOT NULL DEFAULT TRUE,
    versione_modifica INTEGER NOT NULL DEFAULT 1,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aggiornato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ton_stampi PRIMARY KEY (stampo_id),
    CONSTRAINT uq_ton_stampi_codice UNIQUE (codice),
    CONSTRAINT ck_ton_stampi_codice CHECK (LENGTH(TRIM(codice))>0 AND codice=UPPER(TRIM(codice))),
    CONSTRAINT ck_ton_stampi_nome CHECK (LENGTH(TRIM(nome))>0),
    CONSTRAINT ck_ton_stampi_diametro CHECK (diametro_mm>0),
    CONSTRAINT ck_ton_stampi_versione CHECK (versione_modifica>0)
);
