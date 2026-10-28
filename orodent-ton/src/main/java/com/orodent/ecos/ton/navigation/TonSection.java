package com.orodent.ecos.ton.navigation;

/** Stable destinations of the TON workspace; entering an area does not open a database connection. */
public enum TonSection {
    OVERVIEW("overview", "Panoramica", "Il punto di partenza per il laboratorio e la produzione.",
            "M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z"),
    PRODUCTION("production", "Produzione", "Organizza le produzioni, riprendi le bozze e registra gli scarti.",
            "M3 21V9l6 3V7l6 3V3h6v18z"),
    FIRING("firing", "Forni e cotture", "Prepara i carichi e segui le cotture, dalla pianificazione all'esito.",
            "M5 2h14v20H5z M8 5v3h8V5z M8 11v7h8v-7z"),
    TECHNICAL("technical", "Dati tecnici", "Prepara prodotti, materiali e specifiche da utilizzare in produzione.",
            "M3 3h18v4H3z M3 10h18v4H3z M3 17h18v4H3z"),
    TRACEABILITY("traceability", "Tracciabilità", "Risali da un articolo o da un lotto alle lavorazioni e alle specifiche utilizzate.",
            "M4 3h6v6H4z M14 15h6v6h-6z M6 11h2v7h4v2H6z M12 5h6v8h-2V7h-4z"),
    DOCUMENTS("documents", "Documenti", "Consulta i documenti emessi e prepara i modelli di stampa.",
            "M5 2h10l5 5v15H5z M14 3v6h5 M8 12h9 M8 16h9");

    private final String id;
    private final String title;
    private final String description;
    private final String icon;

    TonSection(String id, String title, String description, String icon) {
        this.id = id; this.title = title; this.description = description; this.icon = icon;
    }

    public String id() { return id; }
    public String title() { return title; }
    public String description() { return description; }
    public String icon() { return icon; }
}
