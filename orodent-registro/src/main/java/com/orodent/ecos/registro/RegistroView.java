package com.orodent.ecos.registro;

import com.orodent.ecos.registro.RegistroService.*;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class RegistroView extends BorderPane {
    final Button home = new Button("⌂ Home Orodent-EcoS"),
            refresh = new Button("Aggiorna"),
            apply = new Button("Applica"),
            previous = new Button("←"),
            next = new Button("→");
    final DatePicker from = new DatePicker(LocalDate.now().minusDays(7)),
            to = new DatePicker(LocalDate.now());
    final ChoiceBox<Actor> actor = new ChoiceBox<>();
    final ChoiceBox<String> kind = new ChoiceBox<>();
    final TextField customer = new TextField();
    final CheckBox automatic = new CheckBox("Aggiornamento automatico");
    final TableView<Event> table = new TableView<>();
    final Label feedback = new Label(), range = new Label();
    private final Label totalEvents = stat("Salvataggi", "registro-total-events"),
            created = stat("Creazioni", "registro-total-created"),
            modified = stat("Modifiche", "registro-total-modified"),
            deleted = stat("Eliminazioni", "registro-total-deleted");
    private final FlowPane totals = new FlowPane(12, 8, totalEvents, created, modified, deleted);
    private final VBox details = new VBox(12);
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    RegistroView() {
        setId("registro-module");
        getStyleClass().add("registro-page");
        getStylesheets().add(getClass().getResource("/registro/registro.css").toExternalForm());
        home.setId("registro-home");
        refresh.setId("registro-refresh");
        apply.setId("registro-apply");
        Label title = new Label("Registro attività CRM");
        title.getStyleClass().add("registro-title");
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        HBox header = new HBox(18, home, title, space, refresh);
        header.setAlignment(Pos.CENTER_LEFT);
        from.setId("registro-from");
        to.setId("registro-to");
        var dates =
                DateTimeFormatter.ofPattern("dd/MM/uuuu")
                        .withResolverStyle(java.time.format.ResolverStyle.STRICT);
        from.setConverter(new javafx.util.converter.LocalDateStringConverter(dates, dates));
        to.setConverter(new javafx.util.converter.LocalDateStringConverter(dates, dates));
        customer.setId("registro-customer");
        customer.setPromptText("Nome o ID cliente");
        customer.setPrefWidth(220);
        actor.setId("registro-actor");
        actor.setPrefWidth(200);
        kind.setId("registro-kind");
        kind.getItems().setAll("Tutte le azioni", "Creazione", "Modifica", "Eliminazione");
        kind.setValue("Tutte le azioni");
        automatic.setSelected(true);
        FlowPane filters =
                new FlowPane(
                        12,
                        10,
                        field("Dal", from),
                        field("Al", to),
                        field("Operatore", actor),
                        field("Azione", kind),
                        field("Cliente", customer),
                        apply,
                        automatic);
        filters.setAlignment(Pos.BOTTOM_LEFT);
        totals.setId("registro-totals");
        feedback.setWrapText(true);
        feedback.setId("registro-feedback");
        setTop(new VBox(16, header, filters, totals, feedback));
        table.setId("registro-events");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        column("Data e ora", e -> DATE.format(e.time()), 160);
        column("Operatore", Event::actor, 155);
        column(
                "Cliente",
                e ->
                        e.customers() > 1
                                ? e.customers() + " clienti"
                                : Objects.toString(
                                        e.customer(),
                                        e.customerId() == null
                                                ? "Cataloghi / impostazioni"
                                                : e.customerId()),
                210);
        column("Salvataggio", Event::description, 300);
        table.setPlaceholder(new Label("Nessuna modifica nel periodo selezionato."));
        details.setId("registro-details");
        details.getStyleClass().add("registro-details");
        details.getChildren()
                .add(new Label("Seleziona un salvataggio per vedere cosa è cambiato."));
        ScrollPane scroll = new ScrollPane(details);
        scroll.setFitToWidth(true);
        scroll.setMinWidth(320);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        SplitPane split = new SplitPane(table, scroll);
        split.setDividerPositions(.6);
        split.setMinSize(0, 0);
        setCenter(split);
        HBox footer = new HBox(12, previous, next, range);
        footer.setAlignment(Pos.CENTER_LEFT);
        setBottom(footer);
        BorderPane.setMargin(split, new Insets(16, 0, 16, 0));
    }

    private Label stat(String name, String id) {
        Label label = new Label("—\n" + name);
        label.setId(id);
        label.getStyleClass().add("registro-stat");
        return label;
    }

    private VBox field(String label, Control control) {
        return new VBox(4, new Label(label), control);
    }

    private void column(
            String title, java.util.function.Function<Event, String> text, double width) {
        var column = new TableColumn<Event, String>(title);
        column.setCellValueFactory(v -> new ReadOnlyStringWrapper(text.apply(v.getValue())));
        column.setPrefWidth(width);
        column.setSortable(false);
        table.getColumns().add(column);
    }

    Filter filter(int page) {
        try {
            if (getScene() != null) {
                from.commitValue();
                to.commitValue();
            }
        } catch (java.time.DateTimeException invalid) {
            throw new IllegalArgumentException("Inserisci date valide nei filtri.", invalid);
        }
        String k =
                switch (kind.getSelectionModel().getSelectedIndex()) {
                    case 1 -> "INSERT";
                    case 2 -> "UPDATE";
                    case 3 -> "DELETE";
                    default -> "";
                };
        return new Filter(
                from.getValue(),
                to.getValue(),
                actor.getValue() == null ? "" : actor.getValue().id(),
                k,
                customer.getText(),
                page);
    }

    void show(Page page, int index) {
        Totals t = page.totals();
        totalEvents.setText(t.events() + "\nSalvataggi");
        created.setText(t.created() + "\nCreazioni");
        modified.setText(t.modified() + "\nModifiche");
        deleted.setText(t.deleted() + "\nEliminazioni");
        String selected =
                table.getSelectionModel().getSelectedItem() == null
                        ? null
                        : table.getSelectionModel().getSelectedItem().id();
        table.getItems().setAll(page.events());
        if (selected != null)
            table.getItems().stream()
                    .filter(e -> e.id().equals(selected))
                    .findFirst()
                    .ifPresent(e -> table.getSelectionModel().select(e));
        previous.setDisable(index == 0);
        next.setDisable((index + 1L) * RegistroService.PAGE_SIZE >= t.events());
        range.setText(
                t.events() == 0
                        ? "0 salvataggi"
                        : (index * RegistroService.PAGE_SIZE + 1)
                                + "–"
                                + (index * RegistroService.PAGE_SIZE + page.events().size())
                                + " di "
                                + t.events()
                                + " salvataggi");
    }

    void showDetails(
            Event event,
            List<Change> changes,
            int page,
            java.util.function.IntConsumer showPage,
            java.util.function.Consumer<String> openCustomer) {
        details.getChildren().clear();
        if (event == null) {
            details.getChildren()
                    .add(new Label("Seleziona un salvataggio per vedere cosa è cambiato."));
            return;
        }
        Label heading = new Label(DATE.format(event.time()) + " · " + event.actor());
        heading.setWrapText(true);
        heading.getStyleClass().add("registro-detail-title");
        Label meta =
                new Label(
                        "Postazione: "
                                + Objects.toString(event.station(), "non indicata")
                                + "\nOrigine: "
                                + event.source()
                                + "\nID operatore: "
                                + Objects.toString(event.actorId(), "non indicato")
                                + "\nUtente database: "
                                + event.databaseUser()
                                + "\nID salvataggio: "
                                + event.id());
        meta.setWrapText(true);
        TitledPane technical = new TitledPane("Postazione, origine e identificativi", meta);
        technical.setExpanded(false);
        technical.setAnimated(false);
        details.getChildren().addAll(heading, technical);
        if (event.changes() > RegistroService.PAGE_SIZE) {
            Button previous = new Button("←"), next = new Button("→");
            previous.setDisable(page == 0);
            next.setDisable((page + 1L) * RegistroService.PAGE_SIZE >= event.changes());
            previous.setOnAction(e -> showPage.accept(page - 1));
            next.setOnAction(e -> showPage.accept(page + 1));
            details.getChildren()
                    .add(
                            new HBox(
                                    8,
                                    previous,
                                    next,
                                    new Label(
                                            "Modifiche "
                                                    + (page * RegistroService.PAGE_SIZE + 1)
                                                    + "–"
                                                    + (page * RegistroService.PAGE_SIZE
                                                            + changes.size())
                                                    + " di "
                                                    + event.changes())));
        }
        for (Change change : changes) {
            TitledPane card =
                    new TitledPane(
                            RegistroService.kindName(change.kind())
                                    + " · "
                                    + RegistroService.tableName(change.table()),
                            null);
            card.setExpanded(false);
            card.expandedProperty()
                    .addListener(
                            (o, a, b) -> {
                                if (b && card.getContent() == null)
                                    card.setContent(changeBody(change, openCustomer));
                            });
            card.setExpanded(changes.size() == 1);
            card.setAnimated(false);
            card.getStyleClass().add("registro-change-" + change.kind().toLowerCase(Locale.ROOT));
            details.getChildren().add(card);
        }
    }

    private VBox changeBody(Change change, java.util.function.Consumer<String> openCustomer) {
        VBox body = new VBox(10);
        Label identity =
                new Label(RegistroService.tableName(change.table()) + " · ID " + change.key());
        identity.setWrapText(true);
        body.getChildren().add(identity);
        if (change.customerId() != null) {
            Button open =
                    new Button(
                            "Apri cliente · "
                                    + Objects.toString(change.customer(), change.customerId()));
            open.setOnAction(e -> openCustomer.accept(change.customerId()));
            body.getChildren().add(open);
        }
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        for (int i = 0; i < 2; i++) {
            ColumnConstraints col = new ColumnConstraints();
            col.setPercentWidth(50);
            grid.getColumnConstraints().add(col);
        }
        grid.add(new Label("Prima"), 0, 0);
        grid.add(new Label("Dopo"), 1, 0);
        Set<String> keys = new LinkedHashSet<>(change.before().keySet());
        keys.addAll(change.after().keySet());
        int row = 1;
        var ordered = new ArrayList<>(keys);
        ordered.sort(
                Comparator.comparingInt(
                        key ->
                                key.equals("ID")
                                                || key.endsWith("_ID")
                                                || key.equals("CREATED_AT")
                                                || key.equals("UPDATED_AT")
                                                || key.equals("VERSIONE_MODIFICA")
                                        ? 1
                                        : 0));
        for (String key : ordered) {
            String before = change.before().get(key), after = change.after().get(key);
            if (change.kind().equals("UPDATE") && Objects.equals(before, after)) continue;
            Label label = new Label(RegistroService.fieldName(key));
            label.getStyleClass().add("registro-field-name");
            grid.add(label, 0, row++, 2, 1);
            grid.add(value(before, change.before().containsKey(key)), 0, row);
            grid.add(value(after, change.after().containsKey(key)), 1, row++);
        }
        body.getChildren().add(grid);
        return body;
    }

    private TextArea value(String value, boolean exists) {
        TextArea text =
                new TextArea(
                        !exists
                                ? "Elemento assente"
                                : value == null
                                        ? "Valore non impostato"
                                        : value.isEmpty() ? "(vuoto)" : value);
        text.setEditable(false);
        text.setWrapText(true);
        text.setMinWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);
        text.setPrefRowCount(value != null && value.length() > 150 ? 5 : 2);
        return text;
    }
}
