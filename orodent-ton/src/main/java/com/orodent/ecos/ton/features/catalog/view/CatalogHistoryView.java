package com.orodent.ecos.ton.features.catalog.view;

import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.function.Function;

public final class CatalogHistoryView extends VBox {
    private final TableView<Audit> table = new TableView<>();
    private final Label result = new Label(), feedback, values = new Label();
    private final Button previous, next;

    public CatalogHistoryView(String base) {
        super(12);
        getStyleClass().add("ton-catalogue-tab");
        table.setId(base + "-history-table");
        table.setMinHeight(140);
        table.setPrefHeight(210);
        column("Revisione", a -> "Rev. " + a.number());
        column("Data", a -> a.recordedAt().format(CatalogUi.DATE));
        column("Operazione", Audit::sourceLabel);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> snapshot(b));
        values.setId(base + "-history-snapshot");
        values.setWrapText(true);
        values.setMinHeight(Region.USE_PREF_SIZE);
        previous = CatalogUi.button("←", base + "-history-previous", false);
        next = CatalogUi.button("→", base + "-history-next", false);
        previous.setAccessibleText("Revisioni precedenti nella lista");
        next.setAccessibleText("Altre revisioni");
        feedback = CatalogUi.feedback(base + "-history-feedback");
        feedback.getStyleClass().add("ton-error-feedback");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        getChildren()
                .setAll(
                        CatalogUi.hint(
                                "Revisioni complete dell'anagrafica, in sola lettura. Lo stato"
                                    + " iniziale disponibile non ricostruisce modifiche precedenti"
                                    + " alla migrazione."),
                        table,
                        new HBox(10, result, gap, previous, next),
                        values,
                        feedback);
    }

    private void column(String label, Function<Audit, String> f) {
        TableColumn<Audit, String> c = new TableColumn<>(label);
        c.setCellValueFactory(v -> new ReadOnlyStringWrapper(f.apply(v.getValue())));
        c.setSortable(false);
        table.getColumns().add(c);
    }

    private void snapshot(Audit audit) {
        if (audit == null) {
            values.setText("");
            return;
        }
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
            case Line l ->
                    text.append("\nTecnologia: ")
                            .append(l.technology())
                            .append("\nMateria prima: ")
                            .append(l.material());
            case Product p ->
                    text.append("\nLinea: ")
                            .append(p.line() == null ? "Da specificare" : p.line().code())
                            .append("\nColore: ")
                            .append(p.colorLabel());
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
                            .append(a.specificationLabel());
        }
        text.append("\nNote: ").append(e.meta().notes());
        values.setText(text.toString());
    }

    public void reset() {
        table.getItems().clear();
        values.setText("");
        result.setText("Salva prima l'anagrafica.");
        previous.setDisable(true);
        next.setDisable(true);
        feedback.setText("");
    }

    public void loading() {
        table.setDisable(true);
        result.setText("Caricamento…");
    }

    public void show(HistoryPage page) {
        table.getItems().setAll(page.rows());
        table.setDisable(false);
        table.getSelectionModel().selectFirst();
        previous.setDisable(page.offset() == 0);
        next.setDisable(!page.hasNext());
        result.setText(
                page.total() == 0
                        ? "Nessuna revisione"
                        : (page.offset() + 1)
                                + "–"
                                + (page.offset() + page.rows().size())
                                + " di "
                                + page.total());
        feedback.setText("");
    }

    public void error(String message) {
        feedback.setText(message);
        table.setDisable(false);
    }

    public Button previous() {
        return previous;
    }

    public Button next() {
        return next;
    }
}
