-- V6: canonical catalogue hierarchy and real, typed catalogue history.
-- Previous line/product links remain available for explicit reconciliation.
ALTER TABLE TON.LINEE ADD COLUMN tecnologia VARCHAR(200);
ALTER TABLE TON.LINEE ADD COLUMN materia_prima VARCHAR(30);
ALTER TABLE TON.LINEE ADD COLUMN note VARCHAR(1000);
ALTER TABLE TON.PRODOTTI ADD COLUMN linea_id BIGINT;
ALTER TABLE TON.PRODOTTI ADD COLUMN colore_commerciale VARCHAR(30);
ALTER TABLE TON.PRODOTTI ADD COLUMN note VARCHAR(1000);
ALTER TABLE TON.PRODOTTI ADD CONSTRAINT fk_ton_pro_linea FOREIGN KEY (linea_id) REFERENCES TON.LINEE(linea_id);
ALTER TABLE TON.PRODOTTI ADD CONSTRAINT uq_ton_pro_linea_colore UNIQUE (linea_id,colore_commerciale);
ALTER TABLE TON.PRODOTTI ADD CONSTRAINT ck_ton_pro_colore CHECK (colore_commerciale IS NULL OR (LENGTH(TRIM(colore_commerciale))>0 AND colore_commerciale=UPPER(TRIM(colore_commerciale))));
ALTER TABLE TON.ARTICOLI ADD COLUMN note VARCHAR(1000);
UPDATE TON.PRODOTTI SET linea_id=(SELECT MIN(lp.linea_id) FROM TON.LINEE_PRODOTTI lp WHERE lp.prodotto_id=TON.PRODOTTI.prodotto_id) WHERE (SELECT COUNT(*) FROM TON.LINEE_PRODOTTI lp WHERE lp.prodotto_id=TON.PRODOTTI.prodotto_id)=1;

CREATE TABLE TON.LINEE_REVISIONI (
    linea_id BIGINT NOT NULL,
    numero_revisione INTEGER NOT NULL,
    codice VARCHAR(50) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    tecnologia VARCHAR(200),
    materia_prima VARCHAR(30),
    note VARCHAR(1000),
    attivo BOOLEAN NOT NULL,
    registrato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    origine VARCHAR(20) NOT NULL,
    CONSTRAINT pk_ton_lin_stor PRIMARY KEY (linea_id,numero_revisione),
    CONSTRAINT fk_ton_lin_stor FOREIGN KEY (linea_id) REFERENCES TON.LINEE(linea_id),
    CONSTRAINT ck_ton_lin_stor_rev CHECK (numero_revisione>0),
    CONSTRAINT ck_ton_lin_stor_ori CHECK (origine IN ('MIGRAZIONE','CREAZIONE','MODIFICA','ARCHIVIAZIONE','RIATTIVAZIONE'))
);

CREATE TABLE TON.PRODOTTI_REVISIONI (
    prodotto_id BIGINT NOT NULL,
    numero_revisione INTEGER NOT NULL,
    codice VARCHAR(50) NOT NULL,
    descrizione VARCHAR(200),
    linea_id BIGINT,
    linea_codice VARCHAR(50),
    colore_commerciale VARCHAR(30),
    note VARCHAR(1000),
    attivo BOOLEAN NOT NULL,
    registrato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    origine VARCHAR(20) NOT NULL,
    CONSTRAINT pk_ton_pro_stor PRIMARY KEY (prodotto_id,numero_revisione),
    CONSTRAINT fk_ton_pro_stor FOREIGN KEY (prodotto_id) REFERENCES TON.PRODOTTI(prodotto_id),
    CONSTRAINT ck_ton_pro_stor_rev CHECK (numero_revisione>0),
    CONSTRAINT ck_ton_pro_stor_ori CHECK (origine IN ('MIGRAZIONE','CREAZIONE','MODIFICA','ARCHIVIAZIONE','RIATTIVAZIONE')),
    CONSTRAINT fk_ton_pro_stor_linea FOREIGN KEY (linea_id) REFERENCES TON.LINEE(linea_id)
);

CREATE TABLE TON.ARTICOLI_REVISIONI (
    articolo_id BIGINT NOT NULL,
    numero_revisione INTEGER NOT NULL,
    codice VARCHAR(80) NOT NULL,
    descrizione VARCHAR(200),
    prodotto_id BIGINT NOT NULL,
    prodotto_codice VARCHAR(50) NOT NULL,
    linea_id BIGINT,
    linea_codice VARCHAR(50),
    colore_commerciale VARCHAR(30),
    diametro_mm DECIMAL(6,2) NOT NULL,
    altezza_mm DECIMAL(6,2) NOT NULL,
    configurazione_predefinita_id BIGINT,
    note VARCHAR(1000),
    attivo BOOLEAN NOT NULL,
    registrato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    origine VARCHAR(20) NOT NULL,
    CONSTRAINT pk_ton_art_stor PRIMARY KEY (articolo_id,numero_revisione),
    CONSTRAINT fk_ton_art_stor FOREIGN KEY (articolo_id) REFERENCES TON.ARTICOLI(articolo_id),
    CONSTRAINT ck_ton_art_stor_rev CHECK (numero_revisione>0),
    CONSTRAINT ck_ton_art_stor_ori CHECK (origine IN ('MIGRAZIONE','CREAZIONE','MODIFICA','ARCHIVIAZIONE','RIATTIVAZIONE')),
    CONSTRAINT fk_ton_art_stor_pro FOREIGN KEY (prodotto_id) REFERENCES TON.PRODOTTI(prodotto_id),
    CONSTRAINT fk_ton_art_stor_linea FOREIGN KEY (linea_id) REFERENCES TON.LINEE(linea_id),
    CONSTRAINT fk_ton_art_stor_cfg FOREIGN KEY (configurazione_predefinita_id) REFERENCES TON.CONFIGURAZIONI_ARTICOLO(configurazione_id)
);

INSERT INTO TON.LINEE_REVISIONI(linea_id,numero_revisione,codice,nome,tecnologia,materia_prima,note,attivo,origine)
SELECT linea_id,versione_modifica,codice,nome,tecnologia,materia_prima,note,attivo,'MIGRAZIONE' FROM TON.LINEE;
INSERT INTO TON.PRODOTTI_REVISIONI(prodotto_id,numero_revisione,codice,descrizione,linea_id,linea_codice,colore_commerciale,note,attivo,origine)
SELECT p.prodotto_id,p.versione_modifica,p.codice,p.descrizione,p.linea_id,l.codice,p.colore_commerciale,p.note,p.attivo,'MIGRAZIONE' FROM TON.PRODOTTI p LEFT JOIN TON.LINEE l ON l.linea_id=p.linea_id;
INSERT INTO TON.ARTICOLI_REVISIONI(articolo_id,numero_revisione,codice,descrizione,prodotto_id,prodotto_codice,linea_id,linea_codice,colore_commerciale,diametro_mm,altezza_mm,configurazione_predefinita_id,note,attivo,origine)
SELECT a.articolo_id,a.versione_modifica,a.codice,a.descrizione,a.prodotto_id,p.codice,p.linea_id,l.codice,p.colore_commerciale,a.diametro_mm,a.altezza_mm,a.configurazione_predefinita_id,a.note,a.attivo,'MIGRAZIONE' FROM TON.ARTICOLI a JOIN TON.PRODOTTI p ON p.prodotto_id=a.prodotto_id LEFT JOIN TON.LINEE l ON l.linea_id=p.linea_id;

ALTER TABLE TON.CONFIGURAZIONI_ARTICOLO ADD COLUMN linea_id BIGINT;
ALTER TABLE TON.CONFIGURAZIONI_ARTICOLO ADD COLUMN linea_revisione INTEGER;
ALTER TABLE TON.CONFIGURAZIONI_ARTICOLO ADD COLUMN prodotto_revisione INTEGER;
ALTER TABLE TON.CONFIGURAZIONI_ARTICOLO ADD COLUMN articolo_revisione INTEGER;
ALTER TABLE TON.CONFIGURAZIONI_ARTICOLO ADD CONSTRAINT fk_ton_cfg_lin_stor FOREIGN KEY (linea_id,linea_revisione) REFERENCES TON.LINEE_REVISIONI(linea_id,numero_revisione);
ALTER TABLE TON.CONFIGURAZIONI_ARTICOLO ADD CONSTRAINT fk_ton_cfg_pro_stor FOREIGN KEY (prodotto_id,prodotto_revisione) REFERENCES TON.PRODOTTI_REVISIONI(prodotto_id,numero_revisione);
ALTER TABLE TON.CONFIGURAZIONI_ARTICOLO ADD CONSTRAINT fk_ton_cfg_art_stor FOREIGN KEY (articolo_id,articolo_revisione) REFERENCES TON.ARTICOLI_REVISIONI(articolo_id,numero_revisione);
ALTER TABLE TON.CONFIGURAZIONI_ARTICOLO ADD CONSTRAINT ck_ton_cfg_stor CHECK ((linea_id IS NULL AND linea_revisione IS NULL AND prodotto_revisione IS NULL AND articolo_revisione IS NULL) OR (linea_id IS NOT NULL AND linea_revisione IS NOT NULL AND prodotto_revisione IS NOT NULL AND articolo_revisione IS NOT NULL AND linea_revisione>0 AND prodotto_revisione>0 AND articolo_revisione>0));

CREATE INDEX TON.ix_ton_lin_stor_codice ON TON.LINEE_REVISIONI(codice);
CREATE INDEX TON.ix_ton_pro_stor_codice ON TON.PRODOTTI_REVISIONI(codice);
CREATE INDEX TON.ix_ton_art_stor_codice ON TON.ARTICOLI_REVISIONI(codice);
