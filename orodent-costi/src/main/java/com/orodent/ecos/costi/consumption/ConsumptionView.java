package com.orodent.ecos.costi.consumption;

import com.orodent.ecos.costi.consumption.Consumption.*;
import com.orodent.ecos.costi.products.model.ProductCosts.*;
import com.orodent.ecos.costi.view.*;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

public final class ConsumptionView extends CostsShell {
    public final DatePicker from = new DatePicker(LocalDate.now().minusMonths(6)),
            through = new DatePicker(LocalDate.now().minusDays(1)),
            productionDate = new DatePicker(LocalDate.now());
    public final CheckBox includeArrivals = new CheckBox("Includi arrivi previsti");
    public final Spinner<Integer> horizon = new Spinner<>(1, 365, 30);
    public final ComboBox<Basis> basis = new ComboBox<>(), planBasis = new ComboBox<>();
    public final ComboBox<CalendarMode> calendar = new ComboBox<>();
    public final ComboBox<Metric> metric = new ComboBox<>();
    public final Button refresh = button("Carica / aggiorna", "consumption-refresh"),
            export = button("Esporta CSV", "consumption-export"),
            exportDaily = button("Esporta serie giornaliere", "consumption-export-daily"),
            add = button("+ Aggiungi articolo", "consumption-add"),
            calculate = button("Calcola fabbisogno", "consumption-calculate"),
            minimum = button("Imposta scorta minima", "consumption-minimum"),
            exportPlanDetails =
                    button("Esporta dettaglio piano", "consumption-export-plan-details");
    public final PowderChart chart = new PowderChart();
    public final TableView<Balance> balances = new TableView<>(), planned = new TableView<>();
    public final TableView<Contribution> contributions = new TableView<>();
    public final TabPane tabs = new TabPane();
    public final VBox legends = new VBox(5), planRows = new VBox(8);
    public final Map<String, CheckBox> colors = new LinkedHashMap<>();
    public final Map<Long, CheckBox> powders = new LinkedHashMap<>();
    private final Label feedback = label("Caricamento…"),
            autonomy = label("Calcola un ordine per vedere l'autonomia residua"),
            summary = label("");
    private final TextArea warnings = new TextArea();
    private final List<PlanInput> inputs = new ArrayList<>();
    private List<Choice> choices = List.of();
    private Runnable planChanged = () -> {};

    public record Choice(long article, long specification, String text) {
        @Override
        public String toString() {
            return text;
        }
    }

    private record PlanInput(ComboBox<Choice> choice, TextField quantity) {}

    public ConsumptionView() {
        super("Consumi e fabbisogni");
        setId("consumption-module");
        Label title = label("Consumi e fabbisogni");
        title.getStyleClass().add("costs-title");
        HBox heading = new HBox(15, title, export, exportDaily);
        for (DatePicker picker : List.of(from, through, productionDate)) {
            picker.setConverter(
                    new javafx.util.StringConverter<LocalDate>() {
                        private final java.time.format.DateTimeFormatter format =
                                java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")
                                        .withResolverStyle(java.time.format.ResolverStyle.STRICT);

                        public String toString(LocalDate date) {
                            return date == null ? "" : format.format(date);
                        }

                        public LocalDate fromString(String text) {
                            return text == null || text.isBlank()
                                    ? null
                                    : LocalDate.parse(text, format);
                        }
                    });
            picker.getEditor().setText(picker.getConverter().toString(picker.getValue()));
        }
        from.setId("consumption-from");
        through.setId("consumption-through");
        horizon.setId("consumption-horizon");
        basis.getItems().setAll(Basis.values());
        basis.setValue(Basis.PRODUZIONI);
        basis.setId("consumption-basis");
        planBasis.getItems().setAll(Basis.PRODUZIONI, Basis.VENDITE);
        planBasis.setValue(Basis.PRODUZIONI);
        planBasis.setId("consumption-plan-basis");
        calendar.getItems().setAll(CalendarMode.values());
        calendar.setValue(CalendarMode.CALENDARIO);
        calendar.setId("consumption-calendar");
        metric.getItems().setAll(Metric.values());
        metric.setValue(Metric.CONSUMO);
        metric.setId("consumption-metric");
        horizon.setPrefWidth(90);
        from.setPrefWidth(145);
        through.setPrefWidth(145);
        FlowPane controls =
                new FlowPane(
                        9,
                        8,
                        label("Storico dal"),
                        from,
                        label("al"),
                        through,
                        label("Previsione, giorni"),
                        horizon,
                        calendar,
                        includeArrivals,
                        refresh);
        FlowPane selection = new FlowPane(12, 8, label("Base"), basis, label("Grafico"), metric);
        for (String color : List.of("BIANCA", "GIALLA", "ROSA", "GRIGIA", "ALTRE")) {
            var check =
                    new CheckBox(
                            switch (color) {
                                case "BIANCA" -> "Bianche";
                                case "GIALLA" -> "Gialle";
                                case "GRIGIA" -> "Grigie";
                                case "ROSA" -> "Rosa";
                                default -> "Altre";
                            });
            check.setSelected(true);
            check.setId("consumption-color-" + color);
            colors.put(color, check);
            selection.getChildren().add(check);
        }
        ScrollPane legendScroll = new ScrollPane(legends);
        legendScroll.setFitToWidth(true);
        legendScroll.setPrefWidth(220);
        legendScroll.setPrefHeight(280);
        legendScroll.setMinWidth(160);
        HBox graph = new HBox(14, chart, legendScroll);
        HBox.setHgrow(chart, Priority.ALWAYS);
        graph.setMinHeight(270);
        setup(balances);
        balances.setId("consumption-balances");
        balances.setPrefHeight(250);
        VBox global = new VBox(10, selection, summary, graph, balances, minimum);
        global.setPadding(new Insets(12, 0, 0, 0));
        setup(planned);
        planned.setId("consumption-plan-balances");
        planned.setPrefHeight(240);
        autonomy.setId("consumption-autonomy");
        autonomy.getStyleClass().add("costs-detail-title");
        column(contributions, "Riga piano", 90, c -> String.valueOf(c.row()));
        column(contributions, "Articolo", 180, Contribution::article);
        column(contributions, "Configurazione", 110, c -> String.valueOf(c.specification()));
        column(contributions, "Strato", 75, c -> String.valueOf(c.layer()));
        column(contributions, "Polvere", 160, Contribution::powder);
        column(contributions, "Necessari kg", 120, c -> number(c.kg()));
        contributions.setPrefHeight(180);
        TitledPane breakdown =
                new TitledPane(
                        "Contributo delle singole righe del piano",
                        new VBox(8, contributions, exportPlanDetails));
        breakdown.setExpanded(false);
        VBox simulation =
                new VBox(
                        12,
                        new HBox(
                                10,
                                label("Consumo di riferimento per l’autonomia"),
                                planBasis,
                                label("Data produzione"),
                                productionDate),
                        label("Articoli, configurazioni approvate e quantità da produrre"),
                        planRows,
                        new HBox(10, add, calculate),
                        autonomy,
                        label(
                                "Autonomia basata sulla fonte selezionata nella vista globale. Le"
                                        + " quantità sono sottratte solo nella simulazione."),
                        planned,
                        breakdown);
        simulation.setPadding(new Insets(12, 0, 0, 0));
        for (Tab t :
                List.of(
                        new Tab("Consumo globale", global),
                        new Tab("Simula produzione", simulation))) {
            t.setClosable(false);
            tabs.getTabs().add(t);
        }
        tabs.setId("consumption-tabs");
        warnings.setEditable(false);
        warnings.setWrapText(true);
        warnings.setPrefRowCount(3);
        warnings.setMaxHeight(100);
        warnings.setId("consumption-warnings");
        warnings.getStyleClass().add("costs-muted");
        feedback.setId("consumption-feedback");
        VBox body = new VBox(12, heading, controls, tabs, warnings, feedback);
        body.setPadding(new Insets(18, 24, 18, 24));
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        setCenter(scroll);
        setBottom(
                label(
                        "Quantità in kg · Produzioni teoriche / vendite ricostruite · Disponibilità"
                                + " dichiarate manualmente"));
        calculate.getStyleClass().remove("secondary-button");
        calculate.getStyleClass().add("primary-button");
        add.setOnAction(e -> addLine());
    }

    public void choices(Snapshot snapshot) {
        var result = new ArrayList<Choice>();
        for (Article a : snapshot.catalogue().articles())
            if (a.active())
                for (Specification s :
                        snapshot
                                .catalogue()
                                .specifications()
                                .getOrDefault(a.id(), List.of())
                                .stream()
                                .sorted(
                                        Comparator.comparing(
                                                s -> !Objects.equals(a.preferredId(), s.id())))
                                .toList())
                    if (snapshot.approved().contains(s.id()))
                        result.add(
                                new Choice(
                                        a.id(),
                                        s.id(),
                                        a.code()
                                                + " · configurazione v"
                                                + s.version()
                                                + (Objects.equals(a.preferredId(), s.id())
                                                        ? " · predefinita"
                                                        : "")));
        choices = List.copyOf(result);
        inputs.clear();
        planRows.getChildren().clear();
        add.setDisable(choices.isEmpty());
    }

    public void onPlanChanged(Runnable action) {
        planChanged = action;
    }

    private void addLine() {
        if (choices.isEmpty()) return;
        var combo = new ComboBox<Choice>();
        combo.getItems().setAll(choices);
        combo.getSelectionModel().selectFirst();
        combo.setPrefWidth(420);
        var quantity = new TextField("1");
        quantity.setPrefWidth(100);
        quantity.setPromptText("Quantità");
        var input = new PlanInput(combo, quantity);
        inputs.add(input);
        Button remove = new Button("Rimuovi");
        HBox row = new HBox(10, combo, quantity, remove);
        planRows.getChildren().add(row);
        remove.setOnAction(
                e -> {
                    inputs.remove(input);
                    planRows.getChildren().remove(row);
                    planChanged.run();
                });
        combo.setOnAction(e -> planChanged.run());
        quantity.textProperty().addListener((o, b, a) -> planChanged.run());
        planChanged.run();
    }

    public List<PlanLine> plan() {
        if (inputs.isEmpty()) throw new IllegalArgumentException("Aggiungi almeno un articolo.");
        var result = new ArrayList<PlanLine>();
        for (PlanInput input : inputs) {
            int q;
            try {
                q = Integer.parseInt(input.quantity.getText().strip());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Inserisci una quantità intera per ogni articolo.");
            }
            var c = input.choice.getValue();
            if (c == null) throw new IllegalArgumentException("Seleziona una configurazione.");
            result.add(new PlanLine(c.article, c.specification, q));
        }
        return List.copyOf(result);
    }

    public boolean hasChoices() {
        return !choices.isEmpty();
    }

    public boolean hasPlan() {
        return !inputs.isEmpty();
    }

    public void legend(Snapshot snapshot, Runnable changed) {
        legends.getChildren().clear();
        powders.clear();
        for (Powder p : snapshot.powders()) {
            CheckBox check = new CheckBox(p.code());
            check.setSelected(true);
            check.setId("consumption-powder-" + p.id());
            check.setTooltip(new Tooltip(p.name() + " · " + p.color()));
            check.setTextFill(PowderChart.color(p));
            check.setOnAction(e -> changed.run());
            powders.put(p.id(), check);
            legends.getChildren().add(check);
        }
    }

    public boolean visible(Powder p) {
        String color = colors.containsKey(p.color()) ? p.color() : "ALTRE";
        return colors.get(color).isSelected()
                && powders.containsKey(p.id())
                && powders.get(p.id()).isSelected();
    }

    public void warnings(List<String> values) {
        warnings.setText(String.join("\n", values));
    }

    public void feedback(String text) {
        feedback.setText(text);
    }

    public void summary(String text) {
        summary.setText(text);
    }

    public void autonomy(String text) {
        autonomy.setText(text);
    }

    private static void setup(TableView<Balance> table) {
        table.setFixedCellSize(38);
        column(table, "Polvere", 140, b -> b.powder().code());
        column(table, "Base", 100, b -> b.basis().toString());
        column(table, "Stimati alla data kg", 110, b -> number(b.current()));
        column(table, "Necessari kg", 100, b -> number(b.required()));
        column(table, "Residuo kg", 100, b -> number(b.residual()));
        column(
                table,
                "Autonomia prima → dopo",
                180,
                b -> number(b.beforeDays()) + " → " + number(b.afterDays()) + " giorni");
        column(
                table,
                "Scorta minima",
                150,
                b ->
                        b.powder().settings() == null
                                ? "Non impostata"
                                : number(b.powder().settings().value())
                                        + " "
                                        + b.powder().settings().unit()
                                        + " · "
                                        + number(b.minimumKg())
                                        + " kg");
        column(table, "Da acquistare kg", 120, b -> number(b.buy()));
        column(table, "Dichiarati kg", 110, b -> number(b.powder().stock()));
        column(table, "Prima carenza", 110, b -> Objects.toString(b.firstShortage(), "—"));
        column(table, "Data scorte", 105, b -> Objects.toString(b.powder().stockDate(), "—"));
        column(table, "Media kg/giorno", 120, b -> number(b.rate()));
        column(table, "Giorni alla minima", 130, b -> number(b.daysToMinimum()));
    }

    private static <T> void column(
            TableView<T> table, String title, double width, Function<T, String> mapper) {
        var c = new TableColumn<T, String>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(v -> new ReadOnlyObjectWrapper<>(mapper.apply(v.getValue())));
        table.getColumns().add(c);
    }

    private static String number(java.math.BigDecimal value) {
        return value == null ? "Non determinabile" : PriceFormats.number(value, 0, 3);
    }

    private static Label label(String text) {
        var label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    private static Button button(String text, String id) {
        var button = new Button(text);
        button.setId(id);
        button.getStyleClass().add("secondary-button");
        return button;
    }
}
