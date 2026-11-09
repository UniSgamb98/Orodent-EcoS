-- Manual minimum-stock policies. No stock movements or forecast results are persisted.
CREATE TABLE COSTI.SCORTA_MINIMA (
    polvere_id BIGINT NOT NULL,
    numero_versione INTEGER NOT NULL,
    unita VARCHAR(10) NOT NULL,
    valore DECIMAL(15,3) NOT NULL,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_costi_sco PRIMARY KEY (polvere_id,numero_versione),
    CONSTRAINT fk_costi_sco_pol FOREIGN KEY (polvere_id) REFERENCES TON.POLVERI(polvere_id),
    CONSTRAINT ck_costi_sco_ver CHECK (numero_versione>0),
    CONSTRAINT ck_costi_sco_uni CHECK (unita IN ('KG','GIORNI')),
    CONSTRAINT ck_costi_sco_val CHECK (valore>=0)
);
