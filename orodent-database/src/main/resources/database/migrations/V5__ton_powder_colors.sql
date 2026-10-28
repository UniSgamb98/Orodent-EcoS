-- A display position does not identify a powder's color. Existing rows stay
-- unspecified until an operator chooses their color. The old position is kept
-- for reference and is no longer used by the powder catalogue.
ALTER TABLE TON.POLVERI ADD COLUMN colore VARCHAR(10);
ALTER TABLE TON.POLVERI ADD CONSTRAINT ck_ton_pol_colore
    CHECK (colore IN ('BIANCA', 'GIALLA', 'ROSA', 'GRIGIA'));
