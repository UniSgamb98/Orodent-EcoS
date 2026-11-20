-- Nominal article diameter is explicit: never infer it from a physical mould.
-- Existing models and V19 mould snapshots remain unchanged as historical versions.
CREATE TABLE TON.MODELLO_DIAMETRI (
    modello_id BIGINT NOT NULL,
    diametro_articolo_mm DECIMAL(6,2) NOT NULL,
    grammi_per_mm DECIMAL(12,5) NOT NULL,
    stampo_id BIGINT NOT NULL,
    stampo_revisione INTEGER NOT NULL,
    codice VARCHAR(50) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    diametro_mm DECIMAL(10,3) NOT NULL,
    CONSTRAINT pk_ton_mdiam PRIMARY KEY (modello_id,diametro_articolo_mm),
    CONSTRAINT fk_ton_mdiam_mod FOREIGN KEY (modello_id) REFERENCES TON.MODELLI_DISCO(modello_id),
    CONSTRAINT fk_ton_mdiam_st FOREIGN KEY (stampo_id) REFERENCES TON.STAMPI(stampo_id),
    CONSTRAINT ck_ton_mdiam_val CHECK (diametro_articolo_mm>0 AND grammi_per_mm>0 AND stampo_revisione>0 AND diametro_mm>0 AND LENGTH(TRIM(codice))>0 AND LENGTH(TRIM(nome))>0)
);
CREATE TABLE TON.MODELLO_DIAM_STAMPI (
    modello_id BIGINT NOT NULL,
    diametro_articolo_mm DECIMAL(6,2) NOT NULL,
    stampo_id BIGINT NOT NULL,
    stampo_revisione INTEGER NOT NULL,
    codice VARCHAR(50) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    diametro_mm DECIMAL(10,3) NOT NULL,
    CONSTRAINT pk_ton_mdsta PRIMARY KEY (modello_id,diametro_articolo_mm,stampo_id),
    CONSTRAINT fk_ton_mdsta_diam FOREIGN KEY (modello_id,diametro_articolo_mm) REFERENCES TON.MODELLO_DIAMETRI(modello_id,diametro_articolo_mm),
    CONSTRAINT fk_ton_mdsta_st FOREIGN KEY (stampo_id) REFERENCES TON.STAMPI(stampo_id),
    CONSTRAINT ck_ton_mdsta_val CHECK (stampo_revisione>0 AND diametro_mm>0 AND LENGTH(TRIM(codice))>0 AND LENGTH(TRIM(nome))>0)
);
