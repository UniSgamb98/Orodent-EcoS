CREATE TABLE vendite_dettaglio (
    vendita_id BIGINT
        GENERATED ALWAYS AS IDENTITY
        PRIMARY KEY,

    sorgente VARCHAR(30) NOT NULL,
    documento_id VARCHAR(100) NOT NULL,
    numero_documento VARCHAR(50),
    numero_riga INTEGER NOT NULL,

    data_vendita DATE NOT NULL,

    codice_cliente VARCHAR(50) NOT NULL,
    codice_prodotto VARCHAR(50) NOT NULL,
    descrizione_prodotto VARCHAR(250),
    categoria_prodotto VARCHAR(100),

    quantita DECIMAL(15,3) NOT NULL,
    prezzo_unitario DECIMAL(15,4),
    sconto_percentuale DECIMAL(7,4) DEFAULT 0,
    sconto_importo DECIMAL(15,2) DEFAULT 0,
    importo_netto DECIMAL(15,2),

    tipo_operazione VARCHAR(20) NOT NULL DEFAULT 'VENDITA',
    canale VARCHAR(50),
    agente VARCHAR(150),

    importato_il TIMESTAMP
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_vendita_sorgente
        UNIQUE (
            sorgente,
            documento_id,
            numero_riga
        ),

    CONSTRAINT ck_vendita_quantita
        CHECK (quantita <> 0),

    CONSTRAINT ck_vendita_sconto
        CHECK (
            sconto_percentuale IS NULL
            OR sconto_percentuale BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_vendita_operazione
        CHECK (
            tipo_operazione IN (
                'VENDITA',
                'RESO',
                'NOTA_CREDITO'
            )
        )
);

CREATE INDEX idx_vendite_data
    ON vendite_dettaglio (
        data_vendita
    );

CREATE INDEX idx_vendite_cliente_data
    ON vendite_dettaglio (
        codice_cliente,
        data_vendita
    );

CREATE INDEX idx_vendite_prodotto_data
    ON vendite_dettaglio (
        codice_prodotto,
        data_vendita
    );

CREATE INDEX idx_vendite_cliente_prodotto_data
    ON vendite_dettaglio (
        codice_cliente,
        codice_prodotto,
        data_vendita
    );

CREATE INDEX idx_vendite_categoria_data
    ON vendite_dettaglio (
        categoria_prodotto,
        data_vendita
    );

CREATE TABLE clienti (
    codice_cliente VARCHAR(50) PRIMARY KEY,
    codice_iso VARCHAR(10),
    categoria VARCHAR(100),
    listino VARCHAR(100),
    agente VARCHAR(150),
    tipo_cliente VARCHAR(50),
    ragione_sociale VARCHAR(500) NOT NULL,
    importato_il TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_clienti_ragione_sociale ON clienti (ragione_sociale);
CREATE INDEX idx_clienti_agente ON clienti (agente);
CREATE INDEX idx_clienti_categoria ON clienti (categoria);
CREATE INDEX idx_clienti_codice_iso ON clienti (codice_iso);
