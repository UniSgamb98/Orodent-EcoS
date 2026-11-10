-- Independent immutable scenario bases. Real TON/economic records are never modified.
CREATE TABLE COSTI.SCENARI_BASI (
    base_id VARCHAR(36) NOT NULL PRIMARY KEY,
    fotografia CLOB NOT NULL,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE COSTI.SCENARI (
    scenario_id VARCHAR(36) NOT NULL PRIMARY KEY,
    numero_versione INTEGER NOT NULL CHECK (numero_versione>0)
);
CREATE TABLE COSTI.SCENARI_REVISIONI (
    scenario_id VARCHAR(36) NOT NULL,
    numero_versione INTEGER NOT NULL CHECK (numero_versione>0),
    base_id VARCHAR(36) NOT NULL,
    nome VARCHAR(160) NOT NULL,
    note VARCHAR(4000) NOT NULL,
    archiviato BOOLEAN NOT NULL DEFAULT FALSE,
    modifiche CLOB NOT NULL,
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (scenario_id,numero_versione),
    FOREIGN KEY (scenario_id) REFERENCES COSTI.SCENARI(scenario_id),
    FOREIGN KEY (base_id) REFERENCES COSTI.SCENARI_BASI(base_id)
);
