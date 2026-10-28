package com.orodent.ecos.ton.features.catalog.view;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;

public final class CatalogEditorView extends VBox {
    public enum Sheet {
        GENERAL,
        RELATED,
        SPECIFICATIONS,
        HISTORY
    }

    private final CatalogKind kind;
    private final CatalogForm form;
    private final CatalogRelatedView related;
    private final CatalogHistoryView history;
    private final SpecificationsView specifications;
    private final TabPane tabs = new TabPane();
    private final Map<Tab, Sheet> sheets = new HashMap<>();
    private final Button back, reload, save, cancel, archive, delete, parent;
    private final Label heading = new Label(), status = CatalogUi.hint(""), feedback;
    private final FlowPane actions;
    private boolean updating;

    public CatalogEditorView(CatalogKind kind) {
        super(10);
        this.kind = kind;
        String base = "ton-" + kind.id();
        setId(base + "-editor");
        setMinWidth(0);
        getStyleClass().add("ton-catalogue-page");
        form = new CatalogForm(kind);
        related = kind.child() == null ? null : new CatalogRelatedView(kind);
        history = new CatalogHistoryView(base);
        specifications = kind == CatalogKind.ARTICLES ? new SpecificationsView() : null;
        back =
                CatalogUi.button(
                        "← Elenco " + kind.title().toLowerCase(Locale.ITALIAN),
                        base + "-back",
                        false);
        reload = CatalogUi.button("Ricarica scheda", base + "-reload", false);
        parent =
                CatalogUi.button(
                        kind == CatalogKind.ARTICLES ? "Apri prodotto" : "Apri linea",
                        base + "-open-parent",
                        false);
        save = CatalogUi.button("Salva revisione", base + "-save", true);
        cancel = CatalogUi.button("Annulla modifiche", base + "-cancel", false);
        archive = CatalogUi.button("Archivia", base + "-archive", false);
        delete = CatalogUi.button("Elimina", base + "-delete", false);
        delete.getStyleClass().add("ton-catalogue-danger");
        heading.setId(base + "-heading");
        heading.getStyleClass().add("ton-page-heading");
        heading.setWrapText(true);
        status.setId(base + "-status");
        status.getStyleClass().add("ton-area-status");
        tabs.setId(base + "-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setMinHeight(250);
        tabs.setPrefHeight(420);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        tab("Anagrafica", Sheet.GENERAL, form);
        if (related != null) tab(kind.child().title(), Sheet.RELATED, related);
        if (specifications != null)
            tab("Specifiche di produzione", Sheet.SPECIFICATIONS, specifications);
        tab("Storico", Sheet.HISTORY, history);
        actions = new FlowPane(10, 8, cancel, save, archive, delete);
        feedback = CatalogUi.feedback(base + "-feedback");
        HBox toolbar = new HBox(10, back, reload, parent);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        getChildren().setAll(toolbar, heading, status, tabs, actions, feedback);
        tabs.getSelectionModel()
                .selectedItemProperty()
                .addListener((o, a, b) -> CatalogUi.visible(actions, sheet() == Sheet.GENERAL));
    }

    private void tab(String title, Sheet sheet, javafx.scene.Node content) {
        Tab tab = new Tab(title, CatalogUi.scroll(content));
        sheets.put(tab, sheet);
        tabs.getTabs().add(tab);
    }

    public void show(Detail detail, Choice context) {
        updating = true;
        try {
            Entry e = detail == null ? null : detail.entry();
            form.show(detail, context);
            heading.setText(
                    e == null
                            ? "Nuov"
                                    + (kind == CatalogKind.LINES
                                            ? "a linea"
                                            : "o " + kind.singular())
                            : e.code() + (e.name().isBlank() ? "" : " · " + e.name()));
            status.setText(
                    e == null
                            ? "Nuova anagrafica · non ancora salvata."
                            : "Revisione " + e.revision() + " · " + e.status());
            reload.setDisable(e == null);
            archive.setDisable(e == null);
            archive.setText(e != null && !e.active() ? "Riattiva" : "Archivia");
            delete.setDisable(detail == null || !detail.deletable());
            delete.setTooltip(
                    new Tooltip(
                            "Eliminabile solo se mai utilizzato e senza revisioni precedenti. Negli"
                                + " altri casi usa Archivia."));
            CatalogUi.visible(parent, kind.parent() != null);
            parent.setDisable(e == null || form.parent() == null || form.parent().id() == null);
            save.setText(e == null ? "Crea " + kind.singular() : "Salva revisione");
            feedback.setText("");
            if (related != null) related.reset(e != null, e != null && e.available());
            history.reset();
            if (specifications != null) specifications.reset(e != null, e != null && e.available());
        } finally {
            updating = false;
        }
    }

    public Sheet sheet() {
        Tab selected = tabs.getSelectionModel().getSelectedItem();
        return sheets.getOrDefault(selected, Sheet.GENERAL);
    }

    public void sheet(Sheet sheet) {
        updating = true;
        try {
            sheets.entrySet().stream()
                    .filter(e -> e.getValue() == sheet)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .ifPresent(tabs.getSelectionModel()::select);
        } finally {
            updating = false;
        }
        CatalogUi.visible(actions, sheet() == Sheet.GENERAL);
    }

    public boolean updating() {
        return updating;
    }

    public void feedback(String text, boolean error) {
        feedback.setText(text);
        feedback.getStyleClass().remove("ton-error-feedback");
        if (error) feedback.getStyleClass().add("ton-error-feedback");
    }

    public CatalogForm form() {
        return form;
    }

    public CatalogRelatedView related() {
        return related;
    }

    public CatalogHistoryView history() {
        return history;
    }

    public SpecificationsView specifications() {
        return specifications;
    }

    public TabPane tabs() {
        return tabs;
    }

    public Button back() {
        return back;
    }

    public Button reload() {
        return reload;
    }

    public Button save() {
        return save;
    }

    public Button cancel() {
        return cancel;
    }

    public Button archive() {
        return archive;
    }

    public Button delete() {
        return delete;
    }

    public Button parent() {
        return parent;
    }
}
