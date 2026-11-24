-- Preparation remains immutable. Physical output is declared explicitly, including partial runs.
CREATE TABLE TON.ESECUZIONI_PIANO (
    esecuzione_id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    piano_id BIGINT NOT NULL REFERENCES TON.PIANI_PRODUZIONE(piano_id),
    data_produzione DATE NOT NULL,
    note VARCHAR(1000) NOT NULL,
    registrato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ton_esecuzione_piano UNIQUE(esecuzione_id,piano_id)
);
CREATE TABLE TON.DISCHI_PRODOTTI (
    disco_id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    esecuzione_id BIGINT NOT NULL,
    piano_id BIGINT NOT NULL REFERENCES TON.PIANI_PRODUZIONE(piano_id),
    numero_riga INT NOT NULL,
    progressivo INT NOT NULL,
    articolo_id BIGINT NOT NULL REFERENCES TON.ARTICOLI(articolo_id),
    prodotto_id BIGINT NOT NULL REFERENCES TON.PRODOTTI(prodotto_id),
    articolo_codice VARCHAR(50) NOT NULL,
    prodotto_codice VARCHAR(50) NOT NULL,
    linea_codice VARCHAR(50) NOT NULL,
    colore VARCHAR(50) NOT NULL,
    diametro_mm DECIMAL(6,2) NOT NULL,
    altezza_mm DECIMAL(6,2) NOT NULL,
    specifica_prodotto_id BIGINT REFERENCES TON.CONFIGURAZIONI_PRODOTTO(configurazione_id),
    specifica_articolo_id BIGINT REFERENCES TON.CONFIGURAZIONI_ARTICOLO(configurazione_id),
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ton_disco_esecuzione FOREIGN KEY(esecuzione_id,piano_id) REFERENCES TON.ESECUZIONI_PIANO(esecuzione_id,piano_id),
    CONSTRAINT uq_ton_disco_origine UNIQUE(piano_id,numero_riga,progressivo),
    CONSTRAINT ck_ton_disco_valori CHECK(numero_riga>0 AND progressivo>0 AND diametro_mm>0 AND altezza_mm>0),
    CONSTRAINT ck_ton_disco_spec CHECK((specifica_prodotto_id IS NOT NULL AND specifica_articolo_id IS NULL) OR (specifica_prodotto_id IS NULL AND specifica_articolo_id IS NOT NULL))
);
ALTER TABLE TON.CICLI_COTTURA ADD COLUMN caricato_il TIMESTAMP;
ALTER TABLE TON.CICLI_COTTURA ADD COLUMN forno_codice VARCHAR(50);
ALTER TABLE TON.CICLI_COTTURA ADD COLUMN forno_nome VARCHAR(100);
ALTER TABLE TON.CICLI_COTTURA ADD COLUMN forno_revisione INT;
UPDATE TON.CICLI_COTTURA SET forno_codice=(SELECT f.codice FROM TON.FORNI f WHERE f.forno_id=TON.CICLI_COTTURA.forno_id),forno_nome=(SELECT f.nome FROM TON.FORNI f WHERE f.forno_id=TON.CICLI_COTTURA.forno_id),forno_revisione=(SELECT f.versione_modifica FROM TON.FORNI f WHERE f.forno_id=TON.CICLI_COTTURA.forno_id);
CREATE TABLE TON.CARICO_DISCHI (
    disco_id BIGINT NOT NULL PRIMARY KEY REFERENCES TON.DISCHI_PRODOTTI(disco_id),
    ciclo_id BIGINT NOT NULL REFERENCES TON.CICLI_COTTURA(ciclo_id),
    lotto_id BIGINT NOT NULL,
    registrato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ton_carico_disco_lotto FOREIGN KEY(ciclo_id,lotto_id) REFERENCES TON.LOTTI_COTTURA(ciclo_id,lotto_id)
);
CREATE INDEX ix_ton_disci_articolo ON TON.DISCHI_PRODOTTI(articolo_codice);
CREATE INDEX ix_ton_carico_dischi_ciclo ON TON.CARICO_DISCHI(ciclo_id);
