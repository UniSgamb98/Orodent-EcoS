-- NULL in a step target is the explicit Tfinale placeholder resolved by the load.
ALTER TABLE TON.PROGRAMMA_PASSI ALTER COLUMN temperatura_obiettivo_c NULL;
ALTER TABLE TON.CICLI_COTTURA ADD COLUMN temperatura_finale_c DECIMAL(7,2);
ALTER TABLE TON.CICLI_COTTURA ADD COLUMN programma_precedente_id BIGINT;
ALTER TABLE TON.CICLI_COTTURA ADD CONSTRAINT fk_ton_cic_prog_precedente FOREIGN KEY(programma_precedente_id) REFERENCES TON.PROGRAMMI_COTTURA(programma_id);
ALTER TABLE TON.CICLI_COTTURA ADD CONSTRAINT ck_ton_cic_target CHECK(temperatura_finale_c IS NULL OR (temperatura_finale_c>0 AND programma_id IS NOT NULL));
ALTER TABLE TON.CICLI_COTTURA ADD CONSTRAINT ck_ton_cic_prog_precedente CHECK(programma_precedente_id IS NULL OR (programma_id IS NOT NULL AND temperatura_finale_c IS NOT NULL));
