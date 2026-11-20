-- Keep the established diameter column for production/cost readers, at mould precision.
ALTER TABLE TON.MODELLI_DISCO ADD COLUMN diametro_tmp_mm DECIMAL(10,3);
UPDATE TON.MODELLI_DISCO SET diametro_tmp_mm=diametro_pressatura_mm;
ALTER TABLE TON.MODELLI_DISCO ALTER COLUMN diametro_tmp_mm NOT NULL;
ALTER TABLE TON.MODELLI_DISCO DROP CONSTRAINT ck_ton_md_misure;
ALTER TABLE TON.MODELLI_DISCO DROP COLUMN diametro_pressatura_mm RESTRICT;
RENAME COLUMN TON.MODELLI_DISCO.diametro_tmp_mm TO diametro_pressatura_mm;
ALTER TABLE TON.MODELLI_DISCO ADD CONSTRAINT ck_ton_md_misure CHECK (diametro_pressatura_mm>0 AND pressione_kg_cm2>0 AND grammi_per_mm>0 AND sovramateriale_superiore_mm>=0 AND sovramateriale_inferiore_mm>=0);
-- Immutable per-version snapshots. Migration never guesses a reference or admits other moulds.
CREATE TABLE TON.MODELLO_STAMPO_RIF (
    modello_id BIGINT NOT NULL,
    stampo_id BIGINT NOT NULL,
    stampo_revisione INTEGER NOT NULL,
    codice VARCHAR(50) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    diametro_mm DECIMAL(10,3) NOT NULL,
    CONSTRAINT pk_ton_msr PRIMARY KEY (modello_id),
    CONSTRAINT fk_ton_msr_modello FOREIGN KEY (modello_id) REFERENCES TON.MODELLI_DISCO (modello_id),
    CONSTRAINT fk_ton_msr_stampo FOREIGN KEY (stampo_id) REFERENCES TON.STAMPI (stampo_id),
    CONSTRAINT ck_ton_msr_valori CHECK (stampo_revisione>0 AND diametro_mm>0 AND LENGTH(TRIM(codice))>0 AND LENGTH(TRIM(nome))>0)
);
CREATE TABLE TON.MODELLO_STAMPI_AMMESSI (
    modello_id BIGINT NOT NULL,
    stampo_id BIGINT NOT NULL,
    stampo_revisione INTEGER NOT NULL,
    codice VARCHAR(50) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    diametro_mm DECIMAL(10,3) NOT NULL,
    CONSTRAINT pk_ton_msa PRIMARY KEY (modello_id,stampo_id),
    CONSTRAINT fk_ton_msa_rif FOREIGN KEY (modello_id) REFERENCES TON.MODELLO_STAMPO_RIF (modello_id),
    CONSTRAINT fk_ton_msa_stampo FOREIGN KEY (stampo_id) REFERENCES TON.STAMPI (stampo_id),
    CONSTRAINT ck_ton_msa_valori CHECK (stampo_revisione>0 AND diametro_mm>0 AND LENGTH(TRIM(codice))>0 AND LENGTH(TRIM(nome))>0)
);
