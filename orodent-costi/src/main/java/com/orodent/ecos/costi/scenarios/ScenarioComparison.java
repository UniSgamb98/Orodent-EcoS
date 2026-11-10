package com.orodent.ecos.costi.scenarios;

import static com.orodent.ecos.costi.scenarios.ScenarioPresentation.*;
import static com.orodent.ecos.costi.scenarios.ScenarioTables.*;

import com.orodent.ecos.costi.scenarios.Scenarios.*;

import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.math.BigDecimal;
import java.util.*;

public final class ScenarioComparison extends BorderPane {
    public record Display(
            String name,
            BigDecimal before,
            BigDecimal after,
            BigDecimal delta,
            BigDecimal percent,
            String coverage,
            Row row,
            Group group) {}

    public final ComboBox<Weight> weight = new ComboBox<>();
    private final ComboBox<String> order = new ComboBox<>();
    public final CheckBox changedOnly = new CheckBox("Solo variati");
    public final Button export = new Button("Esporta questa vista CSV"),
            exportArticles = new Button("Esporta tutti gli articoli CSV"),
            exportDetails = new Button("Esporta dettaglio articolo CSV");
    public final TableView<Display> table = new TableView<>();
    private final HBox crumbs = new HBox(8);
    private final Label context = new Label(), coverage = new Label();
    private final ScenarioImpactRows impactRows = new ScenarioImpactRows();
    private final Label baseMean = new Label(),
            scenarioMean = new Label(),
            deltaMean = new Label(),
            summaryCaption = new Label();
    private final TitledPane details = new TitledPane();
    private Analysis analysis;
    private String lineKey;
    private long productId;
    private List<Display> current = List.of();

    public ScenarioComparison() {
        weight.getItems().setAll(Weight.values());
        weight.setValue(Weight.ARTICLES);
        weight.setId("scenarios-weight");
        weight.setOnAction(e -> render());
        order.getItems().setAll("Nome", "Delta € decrescente", "Delta % decrescente");
        order.setValue("Nome");
        order.setOnAction(e -> render());
        changedOnly.setSelected(false);
        changedOnly.setOnAction(e -> render());
        context.setWrapText(true);
        coverage.setWrapText(true);
        weight.setTooltip(
                new Tooltip(
                        "La ponderazione cambia le medie dei gruppi e del riepilogo. I costi dei"
                                + " singoli articoli restano invariati."));
        summaryCaption.setWrapText(true);
        summaryCaption.setId("scenarios-comparison-summary");
        context.getStyleClass().add("impact-caption");
        coverage.getStyleClass().add("impact-caption");
        col(table, "Linea / prodotto / articolo", 210, Display::name);
        col(table, "Base media €", 120, r -> ScenarioDetails.n(r.before()));
        col(table, "Scenario media €", 130, r -> ScenarioDetails.n(r.after()));
        col(table, "Δ medio €", 105, r -> ScenarioDetails.n(r.delta()));
        col(table, "Δ % costo medio", 125, r -> ScenarioDetails.n(r.percent()));
        col(table, "Copertura del confronto", 270, Display::coverage);
        table.setId("scenarios-comparison-table");
        table.setPrefHeight(280);
        table.setMinHeight(180);
        table.setRowFactory(
                t -> {
                    var row = new TableRow<Display>();
                    row.itemProperty()
                            .addListener(
                                    (o, b, a) ->
                                            row.setTooltip(
                                                    a == null ? null : new Tooltip(a.coverage())));
                    row.setOnMouseClicked(
                            e -> {
                                if (e.getClickCount() == 2 && !row.isEmpty()) drill(row.getItem());
                            });
                    return row;
                });
        Button drill = new Button("Apri gruppo / dettaglio →");
        drill.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        drill.setOnAction(
                e -> {
                    var selected = table.getSelectionModel().getSelectedItem();
                    if (selected != null) drill(selected);
                });
        Button help = new Button("ⓘ Come leggere il confronto");
        help.setOnAction(
                e ->
                        message(
                                getScene().getWindow(),
                                "Leggere il confronto",
                                "Base e Scenario sono i costi per articolo, in euro. Le due barre"
                                    + " usano la stessa scala per tutti i risultati filtrati, anche"
                                    + " fra pagine. Clic sul nome per scendere da linea a prodotto"
                                    + " e articolo.\n\n"
                                    + "I riquadri in alto riassumono tutto l'ambito visualizzato,"
                                    + " non la sola pagina. Ogni articolo pesa allo stesso modo"
                                    + " oppure secondo le quantità vendute o prodotte nel periodo"
                                    + " della fotografia. Non si fa la media delle medie dei"
                                    + " gruppi.\n\n"
                                    + "Entrambi i costi devono essere calcolabili. Nelle medie"
                                    + " storiche servono volumi positivi: i risultati senza peso"
                                    + " sono esclusi. La copertura indica quanti articoli"
                                    + " partecipano al calcolo.\n\n"
                                    + "La variazione percentuale misura quanto cambia il costo"
                                    + " medio, non la media delle percentuali dei singoli articoli."
                                    + " Se la base è zero, la percentuale non è calcolabile.\n\n"
                                    + "Verde: costo in diminuzione. Arancione: costo in aumento. I"
                                    + " valori mancanti restano sconosciuti. Prezzi arrotondati"
                                    + " solo a video; precisione completa nei dettagli e nei"
                                    + " CSV."));
        setTop(
                new VBox(
                        10,
                        crumbs,
                        new FlowPane(
                                10,
                                8,
                                new Label("Media"),
                                weight,
                                new Label("Ordine"),
                                order,
                                changedOnly,
                                help),
                        context));
        var metrics =
                new FlowPane(
                        12,
                        12,
                        metric("COSTO BASE · €/articolo", baseMean, "scenarios-mean-base"),
                        metric(
                                "COSTO SCENARIO · €/articolo",
                                scenarioMean,
                                "scenarios-mean-scenario"),
                        metric("VARIAZIONE DEL COSTO MEDIO", deltaMean, "scenarios-mean-delta"));
        scenarioMean.getStyleClass().add("impact-scenario-text");
        details.setText("Mostra dettagli del confronto");
        details.setId("scenarios-comparison-details");
        details.setContent(new VBox(10, drill, table));
        details.setExpanded(false);
        details.expandedProperty()
                .addListener(
                        (o, b, a) ->
                                details.setText(
                                        a
                                                ? "Nascondi dettagli del confronto"
                                                : "Mostra dettagli del confronto"));
        var content =
                new VBox(
                        14,
                        metrics,
                        summaryCaption,
                        impactRows,
                        coverage,
                        details,
                        new FlowPane(10, 8, export, exportArticles, exportDetails));
        content.setStyle("-fx-padding: 14 0 8 0;");
        var scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        setCenter(scroll);
        setStyle("-fx-padding: 14;");
    }

    public void show(Analysis analysis) {
        this.analysis = analysis;
        render();
    }

    public List<Display> rows() {
        return current;
    }

    public String level() {
        return productId != 0 ? "ARTICOLI" : lineKey != null ? "PRODOTTI" : "LINEE";
    }

    public void line(Row row) {
        lineKey = Objects.toString(row.before().product().lineId(), "none");
        productId = 0;
        render();
    }

    public void product(Row row) {
        lineKey = Objects.toString(row.before().product().lineId(), "none");
        productId = row.before().product().id();
        render();
    }

    private void drill(Display selected) {
        if (selected.row() != null) {
            ScenarioDetails.show(getScene().getWindow(), selected.row());
            return;
        }
        if (lineKey == null) {
            lineKey = selected.group().key();
            productId = 0;
        } else productId = selected.group().productId();
        render();
    }

    private void render() {
        if (analysis == null) return;
        crumbs.getChildren().clear();
        Button all = new Button("Tutte le linee");
        all.setOnAction(
                e -> {
                    lineKey = null;
                    productId = 0;
                    render();
                });
        crumbs.getChildren().add(all);
        List<Row> scope = analysis.rows();
        if (lineKey != null) {
            scope =
                    scope.stream()
                            .filter(
                                    r ->
                                            Objects.toString(r.before().product().lineId(), "none")
                                                    .equals(lineKey))
                            .toList();
            String line =
                    scope.isEmpty()
                            ? "Linea non disponibile"
                            : Objects.toString(
                                    scope.getFirst().before().product().lineCode(), "Senza linea");
            Button back = new Button("› " + line);
            back.setOnAction(
                    e -> {
                        productId = 0;
                        render();
                    });
            crumbs.getChildren().add(back);
        }
        if (productId != 0) {
            scope = scope.stream().filter(r -> r.before().product().id() == productId).toList();
            crumbs.getChildren()
                    .add(
                            new Label(
                                    "› "
                                            + (scope.isEmpty()
                                                    ? "Prodotto non disponibile"
                                                    : scope.getFirst().before().product().code())));
        }
        var rows = new ArrayList<Display>();
        if (productId != 0) {
            for (Row row : scope)
                rows.add(
                        new Display(
                                row.before().article() == null
                                        ? row.before().product().code() + " · senza articoli"
                                        : row.before().article().code(),
                                row.before().total(),
                                row.after().total(),
                                row.delta(),
                                row.percent(),
                                row.status(),
                                row,
                                null));
        } else {
            var subset =
                    new Analysis(
                            analysis.draft(),
                            analysis.before(),
                            analysis.after(),
                            scope,
                            analysis.problems());
            for (Group group :
                    new ScenarioEngine().groups(subset, weight.getValue(), lineKey == null, null))
                rows.add(
                        new Display(
                                group.name(),
                                group.before(),
                                group.after(),
                                group.delta(),
                                group.percent(),
                                group.comparable()
                                        + "/"
                                        + group.articles()
                                        + " comparabili · "
                                        + group.weighted()
                                        + " con peso · peso totale "
                                        + ScenarioDetails.n(group.weight()),
                                null,
                                group));
        }
        if (changedOnly.isSelected())
            rows.removeIf(r -> r.delta() != null && r.delta().signum() == 0);
        Comparator<Display> sorter =
                order.getValue().equals("Delta € decrescente")
                        ? Comparator.comparing(
                                Display::delta, Comparator.nullsLast(Comparator.reverseOrder()))
                        : order.getValue().equals("Delta % decrescente")
                                ? Comparator.comparing(
                                        Display::percent,
                                        Comparator.nullsLast(Comparator.reverseOrder()))
                                : Comparator.comparing(Display::name);
        rows.sort(sorter);
        current = List.copyOf(rows);
        table.getItems().setAll(rows);
        table.getColumns().get(1).setText(productId != 0 ? "Base €/articolo" : "Base media €/art.");
        table.getColumns()
                .get(2)
                .setText(productId != 0 ? "Scenario €/articolo" : "Scenario media €/art.");
        var visibleArticles = new HashSet<Long>();
        var visibleGroups = new HashSet<String>();
        for (Display d : current) {
            if (d.row() != null && d.row().articleId() != null)
                visibleArticles.add(d.row().articleId());
            if (d.group() != null) visibleGroups.add(d.group().key());
        }
        var visibleScope =
                scope.stream()
                        .filter(
                                r ->
                                        productId != 0
                                                ? visibleArticles.contains(r.articleId())
                                                : visibleGroups.contains(
                                                        lineKey == null
                                                                ? Objects.toString(
                                                                        r.before()
                                                                                .product()
                                                                                .lineId(),
                                                                        "none")
                                                                : Long.toString(
                                                                        r.before().product().id())))
                        .toList();
        Group totals = new ScenarioEngine().summarize(visibleScope, weight.getValue());
        baseMean.setText(money(totals.before()));
        scenarioMean.setText(money(totals.after()));
        deltaMean.setText(delta(totals.delta()) + " · " + percent(totals.percent()));
        deltaMean.getStyleClass().removeAll("impact-neutral", "impact-increase", "impact-decrease");
        deltaMean.getStyleClass().add(tone(totals.delta()));
        precise(baseMean, totals.before(), "€/articolo");
        precise(scenarioMean, totals.after(), "€/articolo");
        deltaMean.setTooltip(
                new Tooltip(
                        "Differenza: "
                                + ScenarioDetails.n(totals.delta())
                                + " €\nVariazione del costo medio: "
                                + ScenarioDetails.n(totals.percent())
                                + "%\n"
                                + "Calcolo sugli articoli, non sulla media delle medie dei"
                                + " gruppi."));
        summaryCaption.setText(
                weight.getValue()
                        + " · "
                        + totals.comparable()
                        + " articoli su "
                        + totals.articles()
                        + " confrontabili"
                        + (weight.getValue() == Weight.ARTICLES
                                ? ""
                                : " · " + totals.weighted() + " con volumi")
                        + " · Ambito visualizzato"
                        + (changedOnly.isSelected() ? " · Solo variati" : "")
                        + (totals.before() == null
                                ? "\nMedia non disponibile: costi completi o volumi insufficienti."
                                : ""));
        String period =
                date(analysis.draft().base().from())
                        + " – "
                        + date(analysis.draft().base().through());
        context.setText(
                "Fonte: "
                        + analysis.draft().selection().label()
                        + " · Fotografia "
                        + timestamp(analysis.before().capturedAt())
                        + " · Volumi "
                        + period);
        context.setTooltip(
                new Tooltip(
                        "Le medie usano solo articoli con entrambi i costi calcolabili.\n"
                            + "Vendite: quantità positive. Produzioni: quantità confermate. Peso"
                            + " zero escluso.\n"
                            + "Δ%: variazione del costo medio, non media delle variazioni"
                            + " percentuali.\n"
                            + "I prezzi sono arrotondati solo a video. Nei dettagli e nei CSV resta"
                            + " la precisione completa."));
        coverage.setText(String.join("\n", analysis.problems()));
        coverage.setVisible(!analysis.problems().isEmpty());
        coverage.setManaged(coverage.isVisible());
        impactRows.show(
                rows,
                selected -> {
                    table.getSelectionModel().select(selected);
                    drill(selected);
                });
    }
}
