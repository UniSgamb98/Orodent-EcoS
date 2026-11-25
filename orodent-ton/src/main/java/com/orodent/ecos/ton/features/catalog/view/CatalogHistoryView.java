package com.orodent.ecos.ton.features.catalog.view;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;
import com.orodent.ecos.ton.features.technical.view.TechnicalRecordsView;
import com.orodent.ecos.ton.features.technical.view.TechnicalUi;

import javafx.scene.control.*;

/** Catalogue revisions use the shared sheet while retaining their read-only audit snapshots. */
public final class CatalogHistoryView extends TechnicalRecordsView<Audit> {
    private final String base;
    private Dialog<Void> snapshot;

    public CatalogHistoryView(String base, CatalogKind kind) {
        super(
                base + "-history",
                "Revisioni "
                        + switch (kind) {
                            case LINES -> "della linea";
                            case PRODUCTS -> "del prodotto";
                            case ARTICLES -> "dell'articolo";
                        },
                "Ogni revisione conserva i dati dell’anagrafica al momento del salvataggio, in sola"
                        + " lettura.");
        this.base = base;
        TechnicalUi.column(table(), "Revisione", 120, a -> "Rev. " + a.number());
        TechnicalUi.column(table(), "Data", 180, a -> a.recordedAt().format(CatalogUi.DATE));
        TechnicalUi.column(table(), "Operazione", 150, Audit::sourceLabel);
        onOpen(this::openSnapshot);
    }

    private void openSnapshot() {
        var audit = table().getSelectionModel().getSelectedItem();
        if (audit == null) return;
        var values = TechnicalUi.label(snapshotText(audit), "muted-label");
        values.setId(base + "-history-snapshot");
        var content = TechnicalUi.scroll(values);
        content.setPrefViewportWidth(620);
        content.setPrefViewportHeight(420);
        snapshot = new Dialog<>();
        snapshot.setTitle("Revisione " + audit.number() + " · " + audit.snapshot().code());
        if (getScene() != null) snapshot.initOwner(getScene().getWindow());
        snapshot.getDialogPane().setContent(content);
        snapshot.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        snapshot.show();
    }

    private String snapshotText(Audit audit) {
        Entry e = audit.snapshot();
        var text =
                new StringBuilder("Revisione ")
                        .append(audit.number())
                        .append("\nCodice: ")
                        .append(e.code())
                        .append("\nNome / descrizione: ")
                        .append(e.name())
                        .append("\nStato: ")
                        .append(e.active() ? "Attivo" : "Archiviato");
        switch (e) {
            case Line l -> {
                if (!l.technology().isBlank())
                    text.append("\nTecnologia (dato storico): ").append(l.technology());
                if (!l.material().isBlank())
                    text.append("\nMateria prima (dato storico): ").append(l.material());
            }
            case Product p ->
                    text.append("\nLinea: ")
                            .append(p.line() == null ? "Da specificare" : p.line().code())
                            .append("\nColore: ")
                            .append(p.colorLabel())
                            .append("\nConfigurazione predefinita: ")
                            .append(
                                    p.defaultSpecification() == null
                                            ? "Nessuna"
                                            : "ID " + p.defaultSpecification())
                            .append(p.specificationConflict() ? " · da verificare" : "");
            case Article a ->
                    text.append("\nProdotto: ")
                            .append(a.product().code())
                            .append("\nLinea: ")
                            .append(
                                    a.product().lineCode() == null
                                            ? "Da specificare"
                                            : a.product().lineCode())
                            .append("\nColore: ")
                            .append(
                                    com.orodent.ecos.ton.features.catalog.model.CatalogModels
                                            .colorLabelOf(a.product().color()))
                            .append("\nDiametro: ")
                            .append(CatalogUi.number(a.diameter()))
                            .append(" mm\nAltezza: ")
                            .append(CatalogUi.number(a.height()))
                            .append(" mm\nConfigurazione predefinita: ")
                            .append(a.specificationLabel())
                            .append("\nGTIN: ")
                            .append(a.gtin() == null ? "—" : a.gtin());
        }
        text.append("\nNote: ").append(e.meta().notes());
        return text.toString();
    }

    public void reset() {
        reset(false);
    }

    public void show(HistoryPage page) {
        show(page.rows());
    }

    @Override
    public void dispose() {
        if (snapshot != null) snapshot.close();
        super.dispose();
    }
}
