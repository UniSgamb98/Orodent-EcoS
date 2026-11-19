package com.orodent.ecos.ton.features.catalog.view;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;
import com.orodent.ecos.ton.features.technical.view.TechnicalDetailView;

import javafx.scene.control.*;

import java.util.*;

public final class CatalogEditorView extends TechnicalDetailView {
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
    private final Map<Tab, Sheet> sheets = new HashMap<>();
    private final Button save, cancel, archive, delete, parent;
    private final Label status = CatalogUi.hint("");
    private boolean updating;

    public CatalogEditorView(CatalogKind kind) {
        super(
                "ton-" + kind.id(),
                switch (kind) {
                    case LINES -> "← Torna alle linee";
                    case PRODUCTS -> "← Torna ai prodotti";
                    case ARTICLES -> "← Torna agli articoli";
                });
        this.kind = kind;
        String base = "ton-" + kind.id();
        form = new CatalogForm(kind);
        related = kind.child() == null ? null : new CatalogRelatedView(kind);
        history = new CatalogHistoryView(base);
        specifications = kind == CatalogKind.ARTICLES ? new SpecificationsView() : null;
        parent =
                CatalogUi.button(
                        kind == CatalogKind.ARTICLES ? "Apri prodotto" : "Apri linea",
                        base + "-open-parent",
                        false);
        save = CatalogUi.button("Salva revisione", base + "-save", true);
        cancel = CatalogUi.button("Annulla modifiche", base + "-cancel", false);
        archive = CatalogUi.button("Archivia", base + "-archive", false);
        delete = CatalogUi.button("Elimina", base + "-delete", false);
        delete.getStyleClass().add("ton-editor-danger");
        status.setId(base + "-status");
        tab("Anagrafica", Sheet.GENERAL, form);
        if (related != null) tab(kind.child().title(), Sheet.RELATED, related);
        if (specifications != null)
            tab("Specifiche di produzione", Sheet.SPECIFICATIONS, specifications);
        tab("Storico", Sheet.HISTORY, history);
        actions(cancel, save, archive, delete);
        status(false, status);
        CatalogUi.visible(parent, kind.parent() != null);
        addStatusAction(parent);
        tabs.getSelectionModel()
                .selectedItemProperty()
                .addListener((o, a, b) -> CatalogUi.visible(actions, sheet() == Sheet.GENERAL));
    }

    private void tab(String title, Sheet sheet, javafx.scene.Node content) {
        Tab tab = addSheet(title, content);
        sheets.put(tab, sheet);
    }

    public void show(Detail detail, Choice context) {
        updating = true;
        try {
            Entry e = detail == null ? null : detail.entry();
            form.show(detail, context);
            headerTitle.set(
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
