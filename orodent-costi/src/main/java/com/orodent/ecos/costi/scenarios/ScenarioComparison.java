package com.orodent.ecos.costi.scenarios;

import static com.orodent.ecos.costi.scenarios.ScenarioTables.*;

import com.orodent.ecos.costi.scenarios.Scenarios.*;

import javafx.scene.chart.*;
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
    private final BarChart<String, Number> chart =
            new BarChart<>(new CategoryAxis(), new NumberAxis());
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
        chart.setAnimated(false);
        chart.setPrefHeight(220);
        chart.setMinHeight(170);
        chart.setMaxHeight(250);
        ((NumberAxis) chart.getYAxis()).setLabel("€/articolo");
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
        drill.setOnAction(
                e -> {
                    var selected = table.getSelectionModel().getSelectedItem();
                    if (selected != null) drill(selected);
                });
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
                                drill),
                        context));
        VBox content =
                new VBox(
                        10,
                        chart,
                        coverage,
                        table,
                        new FlowPane(10, 8, export, exportArticles, exportDetails));
        VBox.setVgrow(table, Priority.ALWAYS);
        setCenter(content);
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
        context.setText(
                "Fonte: "
                        + analysis.draft().selection().label()
                        + " · Fotografia "
                        + analysis.before().capturedAt()
                        + "\nVolumi: "
                        + analysis.draft().base().from()
                        + " – "
                        + analysis.draft().base().through()
                        + " · Solo vendite positive e produzioni confermate. I risultati incompleti"
                        + " non entrano nelle medie.\n"
                        + "Δ% = variazione del costo medio (non media delle percentuali). Peso zero"
                        + " escluso; nessun volume inventato.");
        long comparable =
                scope.stream()
                        .filter(
                                r ->
                                        r.before().total() != null
                                                && r.after().total() != null
                                                && r.articleId() != null)
                        .count();
        coverage.setText(
                comparable
                        + " / "
                        + scope.stream().filter(r -> r.articleId() != null).count()
                        + " articoli con entrambi i costi calcolabili · Grafico: primi 12 risultati"
                        + " calcolabili.\n"
                        + String.join("\n", analysis.problems()));
        var before = new XYChart.Series<String, Number>();
        before.setName("Base");
        var after = new XYChart.Series<String, Number>();
        after.setName("Scenario");
        rows.stream()
                .filter(r -> r.before() != null && r.after() != null)
                .limit(12)
                .forEach(
                        r -> {
                            before.getData().add(new XYChart.Data<>(r.name(), r.before()));
                            after.getData().add(new XYChart.Data<>(r.name(), r.after()));
                        });
        chart.getData().setAll(before, after);
    }
}
