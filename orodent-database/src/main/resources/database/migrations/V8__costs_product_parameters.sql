-- Immutable transformation parameters, separate from technical specifications and powder prices.
CREATE TABLE COSTI.PRODOTTI_PARAMETRI (
    parametro_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    prodotto_id BIGINT NOT NULL,
    numero_versione INTEGER NOT NULL,
    trasformazione_eur DECIMAL(15,4),
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_costi_par_pro FOREIGN KEY (prodotto_id) REFERENCES TON.PRODOTTI(prodotto_id),
    CONSTRAINT uq_costi_par_ver UNIQUE (prodotto_id,numero_versione),
    CONSTRAINT ck_costi_par_ver CHECK (numero_versione>0),
    CONSTRAINT ck_costi_par_val CHECK (trasformazione_eur IS NULL OR trasformazione_eur>=0)
);
CREATE TABLE COSTI.ARTICOLI_TRASFORMAZIONE (
    parametro_id BIGINT NOT NULL,
    articolo_id BIGINT NOT NULL,
    trasformazione_eur DECIMAL(15,4) NOT NULL,
    CONSTRAINT pk_costi_tra PRIMARY KEY (parametro_id,articolo_id),
    CONSTRAINT fk_costi_tra_par FOREIGN KEY (parametro_id) REFERENCES COSTI.PRODOTTI_PARAMETRI(parametro_id),
    CONSTRAINT fk_costi_tra_art FOREIGN KEY (articolo_id) REFERENCES TON.ARTICOLI(articolo_id),
    CONSTRAINT ck_costi_tra_val CHECK (trasformazione_eur>=0)
);
