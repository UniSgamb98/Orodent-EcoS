CREATE TABLE TON.DISCO_MISURE_REVISIONI (
    disco_id BIGINT NOT NULL,
    numero_revisione INT NOT NULL,
    micrometro VARCHAR(4000) NOT NULL,
    bilancia VARCHAR(1000) NOT NULL,
    calcolo_versione_id BIGINT,
    operatore_id VARCHAR(36),
    operatore_nome VARCHAR(150),
    registrato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ton_disco_misure PRIMARY KEY(disco_id,numero_revisione),
    CONSTRAINT fk_ton_misure_disco FOREIGN KEY(disco_id) REFERENCES TON.DISCHI_PRODOTTI(disco_id),
    CONSTRAINT fk_ton_misure_calcolo FOREIGN KEY(calcolo_versione_id) REFERENCES TON.REGOLA_CUBATURA_VERSIONI(calcolo_versione_id),
    CONSTRAINT ck_ton_misure_rev CHECK(numero_revisione>0),
    CONSTRAINT ck_ton_misure_operatore CHECK((operatore_id IS NULL AND operatore_nome IS NULL) OR (operatore_id IS NOT NULL AND operatore_nome IS NOT NULL))
);
