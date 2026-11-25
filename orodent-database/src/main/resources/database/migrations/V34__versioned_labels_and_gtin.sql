ALTER TABLE TON.MODELLI_DOCUMENTO DROP CONSTRAINT ck_ton_docm_tipo;
ALTER TABLE TON.MODELLI_DOCUMENTO ADD CONSTRAINT ck_ton_docm_tipo CHECK (tipo IN ('PRODUZIONE','COTTURA','ETICHETTA'));
ALTER TABLE TON.CONFIGURAZIONI_PRODOTTO ADD COLUMN etichetta_modello_id BIGINT;
ALTER TABLE TON.CONFIGURAZIONI_PRODOTTO ADD CONSTRAINT fk_ton_cfg_etichetta FOREIGN KEY (etichetta_modello_id) REFERENCES TON.MODELLI_DOCUMENTO(modello_documento_id);
ALTER TABLE TON.ARTICOLI ADD COLUMN gtin VARCHAR(14);
ALTER TABLE TON.ARTICOLI_REVISIONI ADD COLUMN gtin VARCHAR(14);
ALTER TABLE TON.ARTICOLI ADD CONSTRAINT uq_ton_art_gtin UNIQUE (gtin);
ALTER TABLE TON.ARTICOLI ADD CONSTRAINT ck_ton_art_gtin CHECK (gtin IS NULL OR LENGTH(gtin)=14);
CREATE TABLE TON.ETICHETTE_EMESSE (
    etichetta_id BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    modello_documento_id BIGINT NOT NULL,
    ciclo_id BIGINT NOT NULL,
    piano_id BIGINT NOT NULL,
    numero_riga INTEGER NOT NULL,
    dati CLOB NOT NULL,
    contenuto_html CLOB NOT NULL,
    larghezza_mm DECIMAL(7,2) NOT NULL,
    altezza_mm DECIMAL(7,2) NOT NULL,
    margine_mm DECIMAL(7,2) NOT NULL,
    operatore_id VARCHAR(36),
    operatore_nome VARCHAR(150),
    generato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ton_etichetta PRIMARY KEY (etichetta_id),
    CONSTRAINT fk_ton_eti_modello FOREIGN KEY (modello_documento_id) REFERENCES TON.MODELLI_DOCUMENTO(modello_documento_id),
    CONSTRAINT fk_ton_eti_ciclo FOREIGN KEY (ciclo_id) REFERENCES TON.CICLI_COTTURA(ciclo_id),
    CONSTRAINT fk_ton_eti_piano FOREIGN KEY (piano_id) REFERENCES TON.PIANI_PRODUZIONE(piano_id),
    CONSTRAINT ck_ton_eti_formato CHECK (larghezza_mm>0 AND altezza_mm>0 AND margine_mm>=0 AND margine_mm*2<larghezza_mm AND margine_mm*2<altezza_mm AND numero_riga>0),
    CONSTRAINT ck_ton_eti_operatore CHECK ((operatore_id IS NULL AND operatore_nome IS NULL) OR (operatore_id IS NOT NULL AND operatore_nome IS NOT NULL))
);
CREATE TABLE TON.ETICHETTA_DISCHI (
    etichetta_id BIGINT NOT NULL,
    disco_id BIGINT NOT NULL,
    CONSTRAINT pk_ton_eti_dischi PRIMARY KEY (etichetta_id,disco_id),
    CONSTRAINT fk_ton_eti_dischi_etichetta FOREIGN KEY (etichetta_id) REFERENCES TON.ETICHETTE_EMESSE(etichetta_id),
    CONSTRAINT fk_ton_eti_dischi_disco FOREIGN KEY (disco_id) REFERENCES TON.DISCHI_PRODOTTI(disco_id)
);
