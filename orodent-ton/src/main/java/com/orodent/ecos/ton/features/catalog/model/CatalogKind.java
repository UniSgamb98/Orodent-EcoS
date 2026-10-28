package com.orodent.ecos.ton.features.catalog.model;

import com.orodent.ecos.ton.navigation.TonFeature;

public enum CatalogKind {
    LINES("Linee", "linea", "line", "LINEE", "linea_id", TonFeature.LINES),
    PRODUCTS(
            "Prodotti tecnici",
            "prodotto",
            "product",
            "PRODOTTI",
            "prodotto_id",
            TonFeature.PRODUCTS),
    ARTICLES("Articoli", "articolo", "article", "ARTICOLI", "articolo_id", TonFeature.ARTICLES);
    private final String title, singular, id, table, key;
    private final TonFeature feature;

    CatalogKind(
            String title,
            String singular,
            String id,
            String table,
            String key,
            TonFeature feature) {
        this.title = title;
        this.singular = singular;
        this.id = id;
        this.table = table;
        this.key = key;
        this.feature = feature;
    }

    public String title() {
        return title;
    }

    public String singular() {
        return singular;
    }

    public String id() {
        return id;
    }

    public String pluralId() {
        return switch (this) {
            case LINES -> "lines";
            case PRODUCTS -> "products";
            case ARTICLES -> "articles";
        };
    }

    /** Only these trusted identifiers are used to compose repository statements. */
    public String table() {
        return "TON." + table;
    }

    public String historyTable() {
        return table() + "_REVISIONI";
    }

    public String key() {
        return key;
    }

    public TonFeature feature() {
        return feature;
    }

    public CatalogKind child() {
        return this == LINES ? PRODUCTS : this == PRODUCTS ? ARTICLES : null;
    }

    public CatalogKind parent() {
        return this == ARTICLES ? PRODUCTS : this == PRODUCTS ? LINES : null;
    }
}
