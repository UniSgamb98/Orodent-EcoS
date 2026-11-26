package com.orodent.ecos.ton.features.marking.view;

import static com.orodent.ecos.ton.features.marking.model.MarkingTemplates.*;

import com.orodent.ecos.database.CubatureRuleVersions.Version;
import com.orodent.ecos.ton.features.technical.view.*;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.*;

final class MarkingTemplateEditor extends TechnicalDetailView {
    private static final String ID = "ton-marking";
    final TextField code = TechnicalUi.input(ID + "-code", "Codice modello"),
            name = TechnicalUi.input(ID + "-name", "Nome modello");
    final TextArea description = new TextArea();
    final CheckBox active = new CheckBox("Modello attivo");
    final Button cancel = TechnicalUi.button("Annulla modifiche", ID + "-cancel", false),
            save = TechnicalUi.button("Salva bozza", ID + "-save", true),
            approve = TechnicalUi.button("Salva e approva", ID + "-approve", true),
            copy = TechnicalUi.button("Crea nuova versione", ID + "-copy", true);
    final MarkingContentPane content = new MarkingContentPane();
    final TechnicalRecordsView<Snapshot> versions =
            new TechnicalRecordsView<>(
                    ID + "-versions",
                    "Versioni della marcatura",
                    "Ogni salvataggio conserva testo, campi e formati. Doppio clic per consultare"
                            + " una revisione.");
    final TechnicalRecordsView<Usage> usages =
            new TechnicalRecordsView<>(
                    ID + "-usages",
                    "Utilizzi della marcatura",
                    "Specifiche di produzione che usano questa esatta versione. Doppio clic per"
                            + " aprire il prodotto.");
    private final VBox general;
    private final Label state = TechnicalUi.label("", "muted-label");

    MarkingTemplateEditor() {
        super(ID, "← Torna ai modelli di marcatura");
        description.setId(ID + "-description");
        description.setWrapText(true);
        description.setPrefRowCount(4);
        active.setId(ID + "-active");
        general =
                new VBox(
                        16,
                        TechnicalUi.fields(
                                TechnicalUi.field("Codice *", code),
                                TechnicalUi.field("Nome *", name)),
                        TechnicalUi.field("Descrizione", description),
                        active);
        addSheet("Dati generali", general, true);
        addSheet("Marcatura", content);
        addSheet("Utilizzi", usages, true);
        TechnicalUi.column(usages.table(), "Prodotto", 220, Usage::productCode);
        TechnicalUi.column(
                usages.table(),
                "Specifica di produzione",
                180,
                u -> "v" + u.specificationVersion());
        TechnicalUi.column(
                usages.table(),
                "Stato",
                180,
                u -> u.state() + (u.preferred() ? " · predefinita" : ""));
        addSheet("Versioni", versions, true);
        TechnicalUi.column(versions.table(), "Versione", 100, v -> "v" + v.number());
        TechnicalUi.column(versions.table(), "Revisione", 100, v -> "r" + v.revision());
        TechnicalUi.column(versions.table(), "Stato", 160, Snapshot::stateLabel);
        TechnicalUi.column(
                versions.table(),
                "Salvata il",
                180,
                v -> v.savedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        status(true, state);
        actions(cancel, save, approve, copy);
    }

    void show(Detail detail, List<Version> rules) {
        Snapshot v = detail == null ? null : detail.selected();
        boolean editable = v == null || v.editable();
        code.setText(detail == null ? "" : detail.entry().code());
        name.setText(v == null ? "" : v.name());
        description.setText(v == null ? "" : v.description());
        active.setSelected(v == null || v.active());
        general.setDisable(!editable);
        code.setDisable(detail != null);
        content.show(v, rules);
        content.editable(editable);
        TechnicalUi.visible(save, editable);
        TechnicalUi.visible(approve, editable);
        TechnicalUi.visible(copy, !editable);
        copy.setDisable(detail != null && detail.entry().latest().editable());
        reload().setDisable(detail == null);
        state.setText(
                v == null
                        ? "Nuovo modello di marcatura · bozza non salvata."
                        : v.label()
                                + " · "
                                + v.stateLabel()
                                + (editable ? " · modificabile" : " · sola lettura"));
        headerTitle.set(
                v == null
                        ? "Nuovo modello"
                        : detail.entry().code() + " · " + v.name() + " · " + v.label());
        usages.reset(detail != null);
        if (detail == null) versions.reset(false);
        else versions.show(detail.versions());
        feedback("", false);
    }

    Draft draft() {
        return content.content(
                code.getText(), name.getText(), description.getText(), active.isSelected());
    }

    List<String> fingerprint() {
        var result =
                new ArrayList<>(
                        List.of(
                                code.getText(),
                                name.getText(),
                                description.getText(),
                                "" + active.isSelected()));
        result.addAll(content.fingerprint());
        return List.copyOf(result);
    }

    void dispose() {
        versions.dispose();
        usages.dispose();
        content.dispose();
    }
}
