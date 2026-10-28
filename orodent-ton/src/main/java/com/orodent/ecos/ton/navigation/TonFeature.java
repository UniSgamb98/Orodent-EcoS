package com.orodent.ecos.ton.navigation;

import java.util.Arrays;
import java.util.List;

/** Planned work is descriptive; only implemented features get action buttons. */
public enum TonFeature {
    PRODUCTIONS(TonSection.PRODUCTION, "Produzioni e bozze", "Crea una produzione, riprendi le bozze e consulta quelle confermate."),
    SCRAPS(TonSection.PRODUCTION, "Scarti e annullamenti", "Registra gli scarti dalla produzione e conserva il motivo delle variazioni."),
    PLANNING(TonSection.FIRING, "Pianificazione e carichi", "Prepara la presinterizzazione e assegna ai forni i pezzi da lavorare."),
    FIRINGS(TonSection.FIRING, "Cotture ed esiti", "Segui i cicli effettivi: forno, inizio, conclusione, pezzi buoni e scarti."),
    FURNACES(TonSection.FIRING, "Forni", "Gestisci l'anagrafica delle attrezzature e la loro disponibilità per nuove lavorazioni."),
    PROGRAMS(TonSection.FIRING, "Programmi di cottura", "Definisci temperature, rampe e mantenimenti. Un programma è la ricetta termica, non una cottura eseguita."),
    PRODUCTS(TonSection.TECHNICAL, "Prodotti tecnici", "Gestisci famiglie e materiali, come ZR A2. Ricerca, modifica e archiviazione sono già disponibili."),
    LINES(TonSection.TECHNICAL, "Linee", "Raggruppa i prodotti nelle linee utilizzate dal laboratorio e dalla produzione."),
    POWDERS(TonSection.TECHNICAL, "Polveri", "Organizza le polveri, le caratteristiche tecniche e le revisioni delle schede."),
    COMPOSITIONS(TonSection.TECHNICAL, "Composizioni", "Prepara le ricette per strato, consulta lo storico e gestisci le versioni approvate."),
    MODELS(TonSection.TECHNICAL, "Modelli disco", "Definisci geometria, strati, pressione e sovramateriali dei modelli."),
    ARTICLES(TonSection.TECHNICAL, "Articoli e configurazioni", "Collega ogni formato alle versioni di composizione e modello da utilizzare."),
    LOTS(TonSection.TRACEABILITY, "Ricerca lotti e articoli", "Individua un lotto o un articolo e consulta la sua provenienza."),
    HISTORY(TonSection.TRACEABILITY, "Storia delle lavorazioni", "Collega produzioni, carichi e cotture alle specifiche effettivamente utilizzate."),
    LINKED_DOCUMENTS(TonSection.TRACEABILITY, "Documenti collegati", "Raggiungi i documenti emessi a partire dalla produzione o dal lotto che stai consultando."),
    ISSUED_DOCUMENTS(TonSection.DOCUMENTS, "Documenti emessi", "Consulta e riapri le schede di produzione e di cottura conservate nell'archivio."),
    DOCUMENT_TEMPLATES(TonSection.DOCUMENTS, "Modelli di stampa", "Crea e modifica i modelli, controlla l'anteprima e conserva le versioni precedenti.");

    private final TonSection section;
    private final String title;
    private final String description;

    TonFeature(TonSection section, String title, String description) {
        this.section = section; this.title = title; this.description = description;
    }

    public TonSection section() { return section; }
    public String title() { return title; }
    public String description() { return description; }
    public boolean available() { return this == PRODUCTS || this == POWDERS; }
    public static List<TonFeature> in(TonSection section) {
        return Arrays.stream(values()).filter(feature -> feature.section == section).toList();
    }
}
