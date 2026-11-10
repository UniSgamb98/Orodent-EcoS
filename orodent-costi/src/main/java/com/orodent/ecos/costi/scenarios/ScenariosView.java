package com.orodent.ecos.costi.scenarios;

import com.orodent.ecos.costi.products.model.PowderPricing;
import com.orodent.ecos.costi.scenarios.Scenarios.*;
import com.orodent.ecos.costi.view.CostsShell;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;

public final class ScenariosView extends CostsShell {
    public final ListView<Entry> saved = new ListView<>();
    public final TextField search = new TextField(),
            name = new TextField(),
            notes = new TextField();
    public final CheckBox archived = new CheckBox("Includi archiviati"),
            incoming = new CheckBox("Includi ordini in arrivo");
    public final Button create = new Button("＋ Nuovo scenario"),
            save = new Button("Salva bozza"),
            duplicate = new Button("Duplica"),
            archive = new Button("Archivia"),
            history = new Button("Duplica da storico…"),
            refresh = new Button("Aggiorna base…"),
            scope = new Button("Scegli prodotti…");
    public final Label status = new Label("Scegli o crea uno scenario"),
            base = new Label("La fotografia conserverà prezzi, dati tecnici e volumi."),
            scopeLabel = new Label(),
            feedback = new Label();
    public final DatePicker from = new DatePicker(LocalDate.now().minusMonths(6)),
            through = new DatePicker(LocalDate.now().minusDays(1));
    public final ComboBox<PowderPricing.Mode> mode = new ComboBox<>();
    public final ScenarioEditors editors = new ScenarioEditors();
    public final ScenarioPreview preview = new ScenarioPreview();
    public final ScenarioComparison comparison = new ScenarioComparison();
    public final TabPane steps = new TabPane();

    public ScenariosView() {
        super("Scenari");
        setId("scenarios-module");
        getStylesheets()
                .add(ScenariosView.class.getResource("/costi/scenarios.css").toExternalForm());
        saved.setId("scenarios-saved");
        search.setPromptText("Cerca scenario");
        saved.setPrefWidth(230);
        saved.setMinHeight(140);
        VBox.setVgrow(saved, Priority.ALWAYS);
        VBox list = new VBox(12, new Label("I TUOI SCENARI"), create, search, archived, saved);
        list.setId("scenarios-saved-panel");
        list.setPrefWidth(230);
        list.setMinWidth(180);
        list.setStyle("-fx-padding: 18; -fx-background-color: #f7fafc;");
        name.setPromptText("Nome dello scenario");
        name.setTextFormatter(
                new TextFormatter<String>(
                        change -> change.getControlNewText().length() <= 160 ? change : null));
        name.setTooltip(new Tooltip("Nome scenario · massimo 160 caratteri"));
        notes.setTextFormatter(
                new TextFormatter<String>(
                        change -> change.getControlNewText().length() <= 4000 ? change : null));
        name.setPrefWidth(340);
        name.setId("scenarios-name");
        notes.setPromptText("Nota di lavoro");
        notes.setId("scenarios-notes");
        save.setId("scenarios-save");
        create.setId("scenarios-new");
        create.getStyleClass().add("primary-button");
        scope.setId("scenarios-scope");
        mode.setId("scenarios-pricing-mode");
        incoming.setId("scenarios-incoming");
        save.getStyleClass().add("primary-button");
        base.setWrapText(true);
        base.setMinHeight(Region.USE_PREF_SIZE);
        base.setMaxWidth(Double.MAX_VALUE);
        feedback.setWrapText(true);
        scopeLabel.setWrapText(true);
        status.setId("scenarios-status");
        feedback.setId("scenarios-feedback");
        mode.getItems().setAll(PowderPricing.Mode.values());
        mode.setValue(PowderPricing.Mode.SEMPLICE);
        from.setEditable(false);
        through.setEditable(false);
        VBox basePane =
                new VBox(
                        15,
                        new Label("BASE REALE · fotografia immutabile"),
                        new Label(
                                "Valutazione dei dati della fotografia (nessuna rilettura"
                                        + " automatica)"),
                        new FlowPane(10, 8, mode, incoming),
                        scope,
                        scopeLabel,
                        new Label("Periodo storico per la prossima acquisizione dei volumi"),
                        new FlowPane(
                                10, 8, new Label("Dal"), from, new Label("Al"), through, refresh),
                        new Label(
                                "Le medie ponderate usano le quantità positive vendute o prodotte"
                                    + " nel periodo.\n"
                                    + "La nuova base viene applicata solo dopo conferma delle"
                                    + " differenze.\n"
                                    + "Le modifiche restano nello scenario: nessun aggiornamento"
                                    + " dei dati reali."));
        basePane.setStyle("-fx-padding: 20;");
        ScrollPane previewScroll = new ScrollPane(preview);
        previewScroll.setFitToWidth(true);
        previewScroll.setPrefWidth(320);
        previewScroll.setMinWidth(280);
        HBox editPane = new HBox(14, editors, previewScroll);
        HBox.setHgrow(editors, Priority.ALWAYS);
        editors.setMinWidth(500);
        editPane.setStyle("-fx-padding: 12;");
        for (Tab tab :
                java.util.List.of(
                        new Tab("1 · Base reale", new ScrollPane(basePane)),
                        new Tab("2 · Modifiche", editPane),
                        new Tab("3 · Confronto", comparison))) {
            tab.setClosable(false);
            steps.getTabs().add(tab);
        }
        ((ScrollPane) steps.getTabs().getFirst().getContent()).setFitToWidth(true);
        FlowPane actions = new FlowPane(10, 8, save, duplicate, archive, history);
        Button toggleList = new Button("Nascondi elenco");
        toggleList.setId("scenarios-toggle-list");
        toggleList.setTooltip(
                new Tooltip("Mostra o nasconde gli scenari salvati per dare spazio al lavoro."));
        FlowPane header = new FlowPane(12, 10, toggleList, new Label("Scenari"), name, status);
        VBox workspace = new VBox(12, header, actions, notes, base, steps, feedback);
        workspace.setMinWidth(0);
        VBox.setVgrow(steps, Priority.ALWAYS);
        workspace.setStyle("-fx-padding: 18;");
        SplitPane split = new SplitPane(list, workspace);
        split.setDividerPositions(.18);
        double[] listPosition = {.18};
        toggleList.setOnAction(
                e -> {
                    if (split.getItems().contains(list)) {
                        listPosition[0] = split.getDividerPositions()[0];
                        split.getItems().remove(list);
                        toggleList.setText("Mostra elenco scenari");
                    } else {
                        split.getItems().addFirst(list);
                        split.setDividerPositions(listPosition[0]);
                        toggleList.setText("Nascondi elenco");
                    }
                });
        setCenter(split);
        setBottom(
                new Label(
                        "Scenari indipendenti · Base e modifiche versionate · Medie esplicite ·"
                                + " Nessun movimento di magazzino"));
    }
}
