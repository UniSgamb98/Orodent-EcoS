CREATE TABLE TON.REGOLA_CUBATURA_VERSIONI (
    calcolo_versione_id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    regola_id BIGINT NOT NULL REFERENCES TON.REGOLE_CUBATURA(regola_id),
    numero_versione INT NOT NULL,
    numero_revisione INT NOT NULL,
    stato VARCHAR(20) NOT NULL,
    codice VARCHAR(50) NOT NULL,
    nome VARCHAR(150) NOT NULL,
    descrizione VARCHAR(1000) NOT NULL,
    attivo BOOLEAN NOT NULL,
    formule VARCHAR(16000) NOT NULL DEFAULT '',
    creato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ton_calcolo_rev UNIQUE(regola_id,numero_versione,numero_revisione),
    CONSTRAINT uq_ton_calcolo_codice UNIQUE(codice,calcolo_versione_id),
    CONSTRAINT ck_ton_calcolo_versione CHECK(numero_versione>0 AND numero_revisione>0 AND stato IN ('BOZZA','APPROVATA','STORICA')),
    CONSTRAINT ck_ton_calcolo_formule CHECK(stato<>'APPROVATA' OR LENGTH(TRIM(formule))>0)
);
INSERT INTO TON.REGOLA_CUBATURA_VERSIONI(regola_id,numero_versione,numero_revisione,stato,codice,nome,descrizione,attivo,creato_il)
SELECT r.regola_id,1,r.versione_modifica,
CASE WHEN EXISTS(SELECT 1 FROM TON.CONFIGURAZIONI_PRODOTTO c WHERE c.versione_calcolo=r.codice) THEN 'STORICA' ELSE 'BOZZA' END,
r.codice,r.nome,r.descrizione,r.attivo,r.aggiornato_il FROM TON.REGOLE_CUBATURA r;
ALTER TABLE TON.CONFIGURAZIONI_PRODOTTO ADD COLUMN calcolo_versione_id BIGINT;
UPDATE TON.CONFIGURAZIONI_PRODOTTO SET calcolo_versione_id=(SELECT v.calcolo_versione_id FROM TON.REGOLA_CUBATURA_VERSIONI v WHERE v.codice=TON.CONFIGURAZIONI_PRODOTTO.versione_calcolo);
ALTER TABLE TON.CONFIGURAZIONI_PRODOTTO ADD CONSTRAINT fk_ton_cfg_calcolo_versione FOREIGN KEY(versione_calcolo,calcolo_versione_id) REFERENCES TON.REGOLA_CUBATURA_VERSIONI(codice,calcolo_versione_id);
