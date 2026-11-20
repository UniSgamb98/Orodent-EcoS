-- Optional per-layer chroma inputs. Existing recipes keep their exact mass percentages.
CREATE TABLE TON.COMPOSIZIONE_CROMA (
    composizione_id BIGINT NOT NULL,
    numero_strato INTEGER NOT NULL,
    ppm_obiettivo DECIMAL(7,3) NOT NULL,
    CONSTRAINT pk_ton_cc PRIMARY KEY (composizione_id,numero_strato),
    CONSTRAINT fk_ton_cc_strato FOREIGN KEY (composizione_id,numero_strato) REFERENCES TON.COMPOSIZIONE_STRATI (composizione_id,numero_strato),
    CONSTRAINT ck_ton_cc_ppm CHECK (ppm_obiettivo>=0 AND ppm_obiettivo<=2000)
);
CREATE TABLE TON.COMPOSIZIONE_CROMA_POLVERI (
    composizione_id BIGINT NOT NULL,
    numero_strato INTEGER NOT NULL,
    polvere_revisione_id BIGINT NOT NULL,
    gruppo VARCHAR(12) NOT NULL,
    quota DECIMAL(7,4) NOT NULL,
    CONSTRAINT pk_ton_ccp PRIMARY KEY (composizione_id,numero_strato,polvere_revisione_id),
    CONSTRAINT fk_ton_ccp_croma FOREIGN KEY (composizione_id,numero_strato) REFERENCES TON.COMPOSIZIONE_CROMA (composizione_id,numero_strato),
    CONSTRAINT fk_ton_ccp_polvere FOREIGN KEY (polvere_revisione_id) REFERENCES TON.POLVERI_REVISIONI (polvere_revisione_id),
    CONSTRAINT ck_ton_ccp_gruppo CHECK (gruppo IN ('BIANCA','CROMATICA','GRIGIA')),
    CONSTRAINT ck_ton_ccp_quota CHECK (quota>=0 AND quota<=100)
);
