package com.orodent.ecos.ton.features.cubing.view;

import com.orodent.ecos.database.CubatureRuleCatalogue.*;
import com.orodent.ecos.database.CubatureRuleVersions.*;
import com.orodent.ecos.ton.features.technical.view.*;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

final class CubatureRuleEditor extends TechnicalDetailView {
    private static final String ID = "ton-cubature-rules";
    final TextField code = TechnicalUi.input(ID + "-code", "Codice identificativo"),
            name = TechnicalUi.input(ID + "-name", "Nome della regola");
    final TextArea description = new TextArea();
    final CheckBox active = new CheckBox("Regola attiva");
    final Button cancel = TechnicalUi.button("Annulla modifiche", ID + "-cancel", false),
            save = TechnicalUi.button("Salva bozza", ID + "-save", true),
            approve = TechnicalUi.button("Salva e approva", ID + "-approve", true),
            copy = TechnicalUi.button("Crea nuova versione", ID + "-copy", true);
    final CubatureFormulaPane formulas = new CubatureFormulaPane();
    final TechnicalRecordsView<Version> versions =
            new TechnicalRecordsView<>(
                    ID + "-versions",
                    "Versioni della regola",
                    "Ogni salvataggio conserva la revisione precedente. Doppio clic per"
                        + " consultarla.");
    private final VBox general;
    private final Label state = TechnicalUi.label("", "muted-label");

    CubatureRuleEditor() {
        super(ID, "← Torna alle regole di cubatura");
        description.setId(ID + "-description");
        description.setWrapText(true);
        description.setPrefRowCount(3);
        active.setId(ID + "-active");
        general =
                new VBox(
                        16,
                        TechnicalUi.fields(
                                TechnicalUi.field("Codice *", code),
                                TechnicalUi.field("Nome *", name)),
                        TechnicalUi.field("Descrizione della regola", description),
                        active);
        addSheet("Dati generali", general, true);
        addSheet("Formule", formulas, true);
        addSheet("Versioni", versions, true);
        TechnicalUi.column(versions.table(), "Versione", 100, v -> "v" + v.number());
        TechnicalUi.column(versions.table(), "Revisione", 100, v -> "r" + v.revision());
        TechnicalUi.column(versions.table(), "Stato", 200, Version::stateLabel);
        TechnicalUi.column(
                versions.table(),
                "Salvata il",
                200,
                v -> v.createdAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        status(true, state);
        actions(cancel, save, approve, copy);
    }

    void show(Detail detail) {
        Version selected = detail == null ? null : detail.selected();
        boolean editable = selected == null || selected.editable();
        code.setText(selected == null ? "" : selected.code());
        name.setText(selected == null ? "" : selected.name());
        description.setText(selected == null ? "" : selected.description());
        active.setSelected(selected == null || selected.active());
        formulas.source.setText(selected == null ? "" : selected.formulas());
        general.setDisable(!editable);
        code.setDisable(
                detail != null
                        && (detail.rule().used()
                                || detail.versions().stream()
                                        .anyMatch(v -> !v.state().equals("BOZZA"))));
        formulas.editable(editable);
        TechnicalUi.visible(save, editable);
        TechnicalUi.visible(approve, editable);
        TechnicalUi.visible(copy, !editable);
        copy.setDisable(detail != null && detail.versions().stream().anyMatch(Version::editable));
        reload().setDisable(detail == null);
        state.setText(
                selected == null
                        ? "Nuova regola · bozza non salvata."
                        : selected.label()
                                + " · "
                                + selected.stateLabel()
                                + (editable ? " · modificabile" : " · sola lettura")
                                + (selected.state().equals("STORICA")
                                        ? ". Crea una nuova versione per definire le formule."
                                        : ""));
        headerTitle.set(
                selected == null
                        ? "Nuova regola"
                        : selected.code() + " · " + selected.name() + " · " + selected.label());
        if (detail == null) versions.reset(false);
        else versions.show(detail.versions());
        feedback("", false);
    }

    List<String> fingerprint() {
        return List.of(
                code.getText(),
                name.getText(),
                description.getText(),
                "" + active.isSelected(),
                formulas.source.getText());
    }

    Draft draft() {
        return new Draft(
                code.getText(), name.getText(), description.getText(), active.isSelected());
    }

    void dispose() {
        versions.dispose();
        formulas.dispose();
    }
}
