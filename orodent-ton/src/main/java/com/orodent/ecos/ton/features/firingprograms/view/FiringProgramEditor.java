package com.orodent.ecos.ton.features.firingprograms.view;

import static com.orodent.ecos.ton.features.firingprograms.model.FiringPrograms.*;

import com.orodent.ecos.ton.features.technical.view.*;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.*;

final class FiringProgramEditor extends TechnicalDetailView {
    private static final String ID = "ton-firing-programs";
    final TextField code = TechnicalUi.input(ID + "-code", "Codice programma"),
            name = TechnicalUi.input(ID + "-name", "Nome programma");
    final TextArea description = new TextArea();
    final CheckBox active = new CheckBox("Programma attivo");
    final Button cancel = TechnicalUi.button("Annulla modifiche", ID + "-cancel", false),
            save = TechnicalUi.button("Salva bozza", ID + "-save", true),
            approve = TechnicalUi.button("Salva e approva", ID + "-approve", true),
            copy = TechnicalUi.button("Crea nuova versione", ID + "-copy", true);
    final FiringCyclePane content = new FiringCyclePane();
    final TechnicalRecordsView<Snapshot> versions =
            new TechnicalRecordsView<>(
                    ID + "-versions",
                    "Versioni del programma",
                    "Ogni salvataggio conserva il ciclo e le sue durate. Doppio clic per consultare"
                            + " una revisione.");
    private final VBox general;
    private final Label state = TechnicalUi.label("", "muted-label");

    FiringProgramEditor() {
        super(ID, "← Torna ai programmi di cottura");
        getStylesheets()
                .add(getClass().getResource("/ton/css/firing-programs.css").toExternalForm());
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
        addSheet("Ciclo di sinterizzazione", content);
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

    void show(Detail detail) {
        Snapshot v = detail == null ? null : detail.selected();
        boolean editable = v == null || v.editable();
        code.setText(detail == null ? "" : detail.entry().code());
        name.setText(v == null ? "" : v.name());
        description.setText(v == null ? "" : v.notes());
        active.setSelected(v == null || v.active());
        general.setDisable(!editable);
        code.setDisable(detail != null);
        content.show(v == null ? List.of() : v.steps(), v == null);
        content.editable(editable);
        TechnicalUi.visible(save, editable);
        TechnicalUi.visible(approve, editable);
        TechnicalUi.visible(copy, !editable);
        copy.setDisable(detail != null && detail.entry().latest().editable());
        reload().setDisable(detail == null);
        state.setText(
                v == null
                        ? "Nuovo programma di cottura · bozza non salvata."
                        : v.label()
                                + " · "
                                + v.stateLabel()
                                + (editable ? " · modificabile" : " · sola lettura"));
        headerTitle.set(
                v == null
                        ? "Nuovo programma"
                        : detail.entry().code() + " · " + v.name() + " · " + v.label());
        if (detail == null) versions.reset(false);
        else versions.show(detail.versions());
        feedback("", false);
    }

    Draft draft() {
        return new Draft(
                code.getText(),
                name.getText(),
                description.getText(),
                active.isSelected(),
                content.steps());
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
        content.dispose();
    }
}
