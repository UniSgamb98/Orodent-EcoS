package com.orodent.ecos.statistiche.features.sales.customers.view;

import com.orodent.ecos.statistiche.features.sales.analysis.model.CustomerAnalysisItem;
import com.orodent.ecos.statistiche.features.sales.customers.model.*;
import com.orodent.ecos.statistiche.features.sales.customers.service.CustomerFollowUpCalculator;
import com.orodent.ecos.statistiche.core.components.TableColumns;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/** Owns the two customer lists and follow-up controls; filtering stays entirely in memory. */
final class CustomerFollowUpPane extends VBox {
    private final TableView<CustomerAnalysisItem> allTable;
    private final TableView<CustomerFollowUpItem> overdueTable = new TableView<>();
    private final ToggleButton all = new ToggleButton("Tutti");
    private final ToggleButton overdue = new ToggleButton("Da ricontattare");
    private final ComboBox<CustomerFrequencyBasis> basis = new ComboBox<>();
    private final ComboBox<Integer> minimumDelay = new ComboBox<>();
    private final Label reference = new Label();
    private final Label scope = new Label();
    private final FlowPane controls = new FlowPane(8, 8);
    private final CustomerFollowUpCalculator calculator = new CustomerFollowUpCalculator();
    private final NumberFormat number = NumberFormat.getNumberInstance(Locale.ITALY);
    private CustomerFollowUpData data;
    private List<CustomerAnalysisItem> customers = List.of();
    private String search = "";
    private boolean updating;

    CustomerFollowUpPane(TableView<CustomerAnalysisItem> allTable, Consumer<CustomerAnalysisItem> selected) {
        super(10);
        this.allTable = allTable;
        getStyleClass().add("customer-follow-up");
        ToggleGroup mode = new ToggleGroup();
        all.setToggleGroup(mode); overdue.setToggleGroup(mode); all.setSelected(true);
        all.getStyleClass().add("customer-list-mode"); overdue.getStyleClass().add("customer-list-mode");
        mode.selectedToggleProperty().addListener((observable, before, after) -> {
            if (after == null) { mode.selectToggle(before); return; }
            showMode();
        });
        basis.getItems().setAll(CustomerFrequencyBasis.values()); basis.setValue(CustomerFrequencyBasis.TYPICAL);
        minimumDelay.getItems().setAll(0, 3, 7, 14, 30, 60, 90); minimumDelay.setValue(0);
        basis.setAccessibleText("Frequenza da confrontare con la recenza");
        minimumDelay.setAccessibleText("Ritardo minimo in giorni");
        basis.setOnAction(event -> refresh()); minimumDelay.setOnAction(event -> refresh());
        Label basisLabel = new Label("Confronta con:"); basisLabel.setLabelFor(basis);
        Label delayLabel = new Label("Ritardo minimo (giorni):"); delayLabel.setLabelFor(minimumDelay);
        controls.getChildren().setAll(basisLabel, basis, delayLabel, minimumDelay);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getStyleClass().add("customer-follow-up-controls");
        reference.getStyleClass().add("customer-follow-up-reference");
        scope.getStyleClass().add("customer-follow-up-scope");
        reference.setWrapText(true); scope.setWrapText(true);
        reference.setMinHeight(Region.USE_PREF_SIZE); scope.setMinHeight(Region.USE_PREF_SIZE);
        configureTable();
        allTable.getSelectionModel().selectedItemProperty().addListener((observable, before, after) -> {
            if (after != null && !updating) selected.accept(after);
        });
        overdueTable.getSelectionModel().selectedItemProperty().addListener((observable, before, after) -> {
            if (after != null && !updating) selected.accept(after.customer());
        });
        HBox modes = new HBox(8, all, overdue);
        modes.getStyleClass().add("customer-list-modes");
        getChildren().setAll(modes, controls, reference, scope, allTable, overdueTable);
        VBox.setVgrow(allTable, Priority.ALWAYS); VBox.setVgrow(overdueTable, Priority.ALWAYS);
        setMinWidth(0);
        showMode();
    }

    void show(List<CustomerAnalysisItem> customers, CustomerFollowUpData data) {
        this.customers = List.copyOf(customers); this.data = data;
        reference.setText("Situazione al " + data.reference().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        refresh();
    }

    void search(String text) { search = text == null ? "" : text.trim().toLowerCase(Locale.ROOT); refresh(); }

    void select(CustomerAnalysisItem customer) {
        overdueTable.getItems().stream().filter(item -> item.customer().code().equals(customer.code())).findFirst()
                .ifPresentOrElse(item -> { overdueTable.getSelectionModel().select(item); overdueTable.scrollTo(item); },
                        () -> overdueTable.getSelectionModel().clearSelection());
    }
    void clearSelection() { overdueTable.getSelectionModel().clearSelection(); }

    private void refresh() {
        if (data == null) return;
        var selected = overdueTable.getSelectionModel().getSelectedItem();
        updating = true;
        try {
            allTable.getItems().setAll(customers.stream().filter(item -> search.isEmpty()
                    || item.code().toLowerCase(Locale.ROOT).contains(search)
                    || item.name().toLowerCase(Locale.ROOT).contains(search)).toList());
            var rows = calculator.select(data, basis.getValue(), minimumDelay.getValue(), search);
            overdueTable.getSortOrder().clear();
            overdueTable.getItems().setAll(rows);
            if (selected != null) select(selected.customer());
            overdue.setText("Da ricontattare (" + rows.size() + ")");
            all.setText("Tutti (" + allTable.getItems().size() + ")");
            long insufficient = data.items().stream().filter(item -> item.frequency().intervals() == 0).count();
            scope.setText("Acquisti dell'anno " + data.year() + " · Priorità: recenza / frequenza abituale, dalla più alta. "
                    + insufficient + " clienti senza frequenza calcolabile. Con meno di 3 intervalli lo storico è limitato.");
            overdueTable.refresh();
        } finally { updating = false; }
    }

    private void showMode() {
        boolean followUp = overdue.isSelected();
        visible(allTable, !followUp); visible(overdueTable, followUp);
        visible(controls, followUp); visible(reference, followUp); visible(scope, followUp);
    }

    private void configureTable() {
        overdueTable.getStyleClass().add("customer-follow-up-table");
        overdueTable.setPlaceholder(new Label("Nessun cliente da ricontattare con questi filtri"));
        overdueTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        var customer = column("Cliente", item -> item.customer().name(), Function.identity());
        customer.setPrefWidth(180);
        var last = column("Ultimo ordine", CustomerFollowUpItem::lastPurchase,
                (LocalDate value) -> value == null ? "—" : value.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        var recency = column("Recenza", CustomerFollowUpItem::recencyDays, (Long value) -> value + " gg");
        var frequency = column("Frequenza", item -> basis.getValue().days(item.frequency()), this::days);
        var delay = column("Ritardo", item -> item.delay(basis.getValue()), value -> "+" + days(value));
        delay.getStyleClass().add("customer-follow-up-delay");
        var history = column("Storico", item -> item.frequency().intervals(),
                (Integer value) -> value < 3 ? "Limitato (" + value + ")" : value + " intervalli");
        overdueTable.getColumns().addAll(customer, last, recency, frequency, delay, history);
        // Initial domain priority is restored whenever the filters change; users can sort numeric columns.
    }

    private String days(BigDecimal value) { return value == null ? "—" : number.format(value) + " gg"; }

    private <T extends Comparable<? super T>> TableColumn<CustomerFollowUpItem, T> column(String title,
            Function<CustomerFollowUpItem, T> value, Function<T, String> format) {
        return TableColumns.value(title, value, format);
    }
    private void visible(javafx.scene.Node node, boolean visible) { node.setVisible(visible); node.setManaged(visible); }
}
