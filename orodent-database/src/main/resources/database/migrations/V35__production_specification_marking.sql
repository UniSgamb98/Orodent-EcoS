-- An exact immutable marking snapshot belongs to each production specification version.
ALTER TABLE TON.CONFIGURAZIONI_PRODOTTO ADD COLUMN marcatura_versione_id BIGINT;
ALTER TABLE TON.CONFIGURAZIONI_PRODOTTO ADD CONSTRAINT fk_ton_cfg_marcatura FOREIGN KEY (marcatura_versione_id) REFERENCES TON.MARCATURA_VERSIONI(marcatura_versione_id);
CREATE INDEX idx_ton_cfg_marcatura ON TON.CONFIGURAZIONI_PRODOTTO(marcatura_versione_id);
