package com.orodent.ecos.costi.scenarios;

import static com.orodent.ecos.costi.scenarios.ScenarioTables.*;

import com.orodent.ecos.costi.products.model.PowderPricing;
import com.orodent.ecos.costi.products.model.ProductCosts.*;
import com.orodent.ecos.costi.scenarios.Scenarios.*;
import com.orodent.ecos.module.csv.CsvExportDialog;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public final class ScenariosController {
    private final ScenariosView view;
    private final ScenarioService service;
    private final Executor workers;
    private final Runnable home;
    private Draft draft;
    private Analysis analysis;
    private boolean dirty, busy, saving, disposed, rendering, listRendering;
    private long epoch, listEpoch, selectionEpoch;
    private List<Entry> entries = List.of();
    private Window window;
    private final EventHandler<WindowEvent> closing =
            e -> {
                if (!canLeave()) e.consume();
            };
    private final ChangeListener<Window> windowChanged = (o, b, a) -> attachWindow(a);
    private final ChangeListener<Scene> sceneChanged = (o, b, a) -> attachScene(b, a);

    public ScenariosController(
            ScenariosView view, ScenarioService service, Executor workers, Runnable home) {
        this.view = view;
        this.service = service;
        this.workers = workers;
        this.home = home;
        view.create.setOnAction(e -> create());
        view.save.setOnAction(e -> save());
        view.duplicate.setOnAction(
                e -> {
                    if (draft != null && !view.editors.pending.get()) {
                        draft = collect().duplicate();
                        dirty = true;
                        metadata();
                        calculate();
                    }
                });
        view.archive.setOnAction(e -> archive());
        view.history.setOnAction(e -> history());
        view.refresh.setOnAction(e -> refresh());
        view.scope.setOnAction(e -> scope());
        view.home.setOnAction(
                e -> {
                    if (canLeave()) home.run();
                });
        view.name.textProperty().addListener((o, b, a) -> metadataChanged());
        view.notes.textProperty().addListener((o, b, a) -> metadataChanged());
        view.archived.setOnAction(e -> list());
        view.search.textProperty().addListener((o, b, a) -> filterList());
        view.mode.setOnAction(e -> selection());
        view.incoming.setOnAction(e -> selection());
        view.editors.onApplied(
                value -> {
                    if (draft == null) return;
                    draft =
                            value.state(
                                    view.name.getText(),
                                    view.notes.getText(),
                                    value.archived(),
                                    value.selection(),
                                    value.products(),
                                    value.prices(),
                                    value.recipes(),
                                    value.models(),
                                    value.transforms());
                    dirty = true;
                    calculate();
                });
        view.editors.pending.addListener((o, b, a) -> controls());
        view.saved
                .getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (o, b, a) -> {
                            if (listRendering
                                    || a == null
                                    || disposed
                                    || busy
                                    || draft != null
                                            && draft.id().equals(a.id())
                                            && draft.version() == a.version()) return;
                            long intent = ++selectionEpoch;
                            Platform.runLater(
                                    () -> {
                                        if (disposed
                                                || busy
                                                || intent != selectionEpoch
                                                || view.saved.getSelectionModel().getSelectedItem()
                                                        != a) return;
                                        if (canLeave()) load(a, false);
                                        else restoreListSelection();
                                    });
                        });
        view.preview.compareLine.setOnAction(
                e -> {
                    Row r = view.preview.selected();
                    if (r != null) {
                        view.comparison.line(r);
                        view.steps.getSelectionModel().select(2);
                    }
                });
        view.preview.compareProduct.setOnAction(
                e -> {
                    Row r = view.preview.selected();
                    if (r != null) {
                        view.comparison.product(r);
                        view.steps.getSelectionModel().select(2);
                    }
                });
        view.comparison.export.setOnAction(e -> export(false));
        view.comparison.exportArticles.setOnAction(e -> export(true));
        view.comparison.exportDetails.setOnAction(e -> exportDetails());
        view.sceneProperty().addListener(sceneChanged);
        attachScene(null, view.getScene());
        controls();
    }

    public void start() {
        list();
    }

    private void list() {
        long intent = ++listEpoch;
        service.list(view.archived.isSelected())
                .whenComplete(
                        (rows, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (disposed || intent != listEpoch) return;
                                            if (error != null) {
                                                feedback(error);
                                                return;
                                            }
                                            entries = rows;
                                            filterList();
                                            if (draft == null && !busy && !entries.isEmpty())
                                                load(entries.getFirst(), false);
                                        }));
    }

    private void filterList() {
        String query = view.search.getText().strip().toLowerCase(Locale.ROOT);
        listRendering = true;
        try {
            view.saved
                    .getItems()
                    .setAll(
                            entries.stream()
                                    .filter(e -> e.name().toLowerCase(Locale.ROOT).contains(query))
                                    .toList());
            restoreListSelection();
        } finally {
            listRendering = false;
        }
    }

    private void restoreListSelection() {
        boolean before = listRendering;
        listRendering = true;
        try {
            view.saved.getSelectionModel().clearSelection();
            if (draft != null)
                view.saved.getItems().stream()
                        .filter(e -> e.id().equals(draft.id()))
                        .findFirst()
                        .ifPresent(e -> view.saved.getSelectionModel().select(e));
        } finally {
            listRendering = before;
        }
    }

    private Draft collect() {
        return draft.state(
                view.name.getText(),
                view.notes.getText(),
                draft.archived(),
                draft.selection(),
                draft.products(),
                draft.prices(),
                draft.recipes(),
                draft.models(),
                draft.transforms());
    }

    private void metadataChanged() {
        if (rendering || draft == null) return;
        dirty = true;
        controls();
    }

    private void metadata() {
        rendering = true;
        try {
            view.name.setText(draft.name());
            view.notes.setText(draft.notes());
            view.mode.setValue(draft.selection().mode());
            view.incoming.setSelected(draft.selection().includeOrders());
            view.from.setValue(draft.base().from());
            view.through.setValue(draft.base().through());
            view.scopeLabel.setText(
                    draft.products().size()
                            + " prodotti selezionati · tutti i relativi articoli, inclusi gli"
                            + " archiviati · solo configurazioni predefinite approvate nella"
                            + " fotografia");
            view.base.setText(
                    "Base reale · "
                            + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                                    .withZone(ZoneId.systemDefault())
                                    .format(draft.base().catalogue().capturedAt())
                            + " · "
                            + draft.selection().label()
                            + " · "
                            + draft.changes()
                            + " modifiche applicate\nBase congelata · volumi "
                            + ScenarioPresentation.date(draft.base().from())
                            + " – "
                            + ScenarioPresentation.date(draft.base().through()));
            restoreListSelection();
        } finally {
            rendering = false;
        }
        controls();
    }

    private void load(Entry entry, boolean duplicate) {
        if (busy) return;
        busy = true;
        long intent = ++epoch;
        controls();
        service.load(entry)
                .whenComplete(
                        (loaded, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (disposed || intent != epoch) return;
                                            busy = false;
                                            if (error != null) {
                                                feedback(error);
                                                controls();
                                                return;
                                            }
                                            draft = duplicate ? loaded.duplicate() : loaded;
                                            dirty = duplicate;
                                            view.editors.pending.set(false);
                                            metadata();
                                            calculate();
                                        }));
    }

    private void create() {
        if (busy || !canLeave()) return;
        Dialog<Boolean> dialog = new Dialog<>();
        if (window != null) dialog.initOwner(window);
        dialog.setTitle("Nuovo scenario dalla situazione reale");
        TextField name = new TextField("Nuovo scenario");
        name.setTextFormatter(
                new TextFormatter<String>(
                        change -> change.getControlNewText().length() <= 160 ? change : null));
        DatePicker from = new DatePicker(LocalDate.now().minusMonths(6)),
                through = new DatePicker(LocalDate.now().minusDays(1));
        from.setEditable(false);
        through.setEditable(false);
        dialog.getDialogPane()
                .setContent(
                        new VBox(
                                10,
                                new Label("Nome scenario"),
                                name,
                                new Label(
                                        "Periodo storico per vendite e produzioni, concluso entro"
                                                + " ieri"),
                                new HBox(10, from, through),
                                new Label(
                                        "La fotografia è una lettura coerente di prezzi, lotti,"
                                                + " ordini e dati tecnici.")));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(b -> b == ButtonType.OK);
        dialog.showAndWait()
                .filter(Boolean.TRUE::equals)
                .ifPresent(
                        ok -> {
                            busy = true;
                            long intent = ++epoch;
                            controls();
                            view.feedback.setText("Acquisizione della base reale…");
                            service.capture(from.getValue(), through.getValue())
                                    .whenComplete(
                                            (base, error) ->
                                                    Platform.runLater(
                                                            () -> {
                                                                if (disposed || intent != epoch)
                                                                    return;
                                                                busy = false;
                                                                if (error != null) {
                                                                    feedback(error);
                                                                    controls();
                                                                    return;
                                                                }
                                                                draft =
                                                                        Draft.create(
                                                                                base,
                                                                                name.getText());
                                                                dirty = true;
                                                                view.editors.pending.set(false);
                                                                metadata();
                                                                view.steps
                                                                        .getSelectionModel()
                                                                        .selectFirst();
                                                                calculate();
                                                            }));
                        });
    }

    private void selection() {
        if (rendering || draft == null || busy) return;
        if (view.editors.pending.get()) {
            rendering = true;
            view.mode.setValue(draft.selection().mode());
            view.incoming.setSelected(draft.selection().includeOrders());
            rendering = false;
            view.feedback.setText("Applica o annulla prima le modifiche tecniche in sospeso.");
            return;
        }
        var selected =
                new PowderPricing.Selection(
                        view.mode.getValue(),
                        view.mode.getValue() == PowderPricing.Mode.AVANZATA
                                && view.incoming.isSelected());
        if (!selected.equals(draft.selection())
                && !confirm(
                        window,
                        "Rivalutare la fotografia con "
                                + selected.label()
                                + "? I dati restano quelli già acquisiti; non vengono riletti dal"
                                + " database.")) {
            rendering = true;
            view.mode.setValue(draft.selection().mode());
            view.incoming.setSelected(draft.selection().includeOrders());
            rendering = false;
            return;
        }
        draft = collect();
        draft =
                draft.state(
                        draft.name(),
                        draft.notes(),
                        draft.archived(),
                        selected,
                        draft.products(),
                        draft.prices(),
                        draft.recipes(),
                        draft.models(),
                        draft.transforms());
        dirty = true;
        metadata();
        calculate();
    }

    private void calculate() {
        if (draft == null || disposed) return;
        draft = collect();
        busy = true;
        long intent = ++epoch;
        controls();
        view.feedback.setText("Calcolo dei confronti…");
        service.analyse(draft)
                .whenComplete(
                        (loaded, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (disposed || intent != epoch) return;
                                            busy = false;
                                            if (error != null) {
                                                feedback(error);
                                                controls();
                                                return;
                                            }
                                            analysis = loaded;
                                            view.editors.show(draft, loaded.before());
                                            view.preview.show(loaded);
                                            view.comparison.show(loaded);
                                            metadata();
                                            long complete =
                                                    loaded.rows().stream()
                                                            .filter(
                                                                    r ->
                                                                            r.before().total()
                                                                                            != null
                                                                                    && r.after()
                                                                                                    .total()
                                                                                            != null)
                                                            .count();
                                            view.feedback.setText(
                                                    "Confronti aggiornati · "
                                                            + complete
                                                            + " / "
                                                            + loaded.rows().size()
                                                            + " righe calcolabili · Nessun dato"
                                                            + " reale modificato.");
                                            controls();
                                        }));
    }

    private void save() {
        if (draft == null || busy || view.editors.pending.get()) return;
        draft = collect();
        busy = true;
        saving = true;
        long intent = ++epoch;
        controls();
        service.save(draft)
                .whenComplete(
                        (saved, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (disposed || intent != epoch) return;
                                            busy = false;
                                            saving = false;
                                            if (error != null) {
                                                feedback(error);
                                                controls();
                                                return;
                                            }
                                            draft = saved;
                                            dirty = false;
                                            if (saved.archived()) view.archived.setSelected(true);
                                            metadata();
                                            list();
                                            view.feedback.setText(
                                                    "Scenario salvato · revisione "
                                                            + saved.version()
                                                            + " · base conservata separatamente.");
                                            calculate();
                                        }));
    }

    private void archive() {
        if (draft == null || busy || view.editors.pending.get()) return;
        String action =
                draft.archived()
                        ? "Ripristinare lo scenario?"
                        : "Archiviare lo scenario conservando tutte le revisioni? Eventuali"
                                + " modifiche saranno salvate nella nuova revisione.";
        if (!confirm(window, action)) return;
        Draft d = collect();
        draft =
                d.state(
                        d.name(),
                        d.notes(),
                        !d.archived(),
                        d.selection(),
                        d.products(),
                        d.prices(),
                        d.recipes(),
                        d.models(),
                        d.transforms());
        dirty = true;
        save();
    }

    private void history() {
        if (draft == null || busy || !canLeave()) return;
        busy = true;
        long intent = ++epoch;
        controls();
        service.history(draft.id())
                .whenComplete(
                        (rows, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (disposed || intent != epoch) return;
                                            busy = false;
                                            controls();
                                            if (error != null) {
                                                feedback(error);
                                                return;
                                            }
                                            Dialog<Entry> dialog = new Dialog<>();
                                            if (window != null) dialog.initOwner(window);
                                            dialog.setTitle("Duplica da una revisione storica");
                                            ListView<Entry> list = new ListView<>();
                                            list.getItems().setAll(rows);
                                            list.setPrefSize(600, 320);
                                            dialog.getDialogPane()
                                                    .setContent(
                                                            new VBox(
                                                                    10,
                                                                    new Label(
                                                                            "La revisione scelta"
                                                                                + " verrà aperta"
                                                                                + " come nuova"
                                                                                + " bozza"
                                                                                + " indipendente."),
                                                                    list));
                                            dialog.getDialogPane()
                                                    .getButtonTypes()
                                                    .addAll(ButtonType.OK, ButtonType.CANCEL);
                                            dialog.setResultConverter(
                                                    b ->
                                                            b == ButtonType.OK
                                                                    ? list.getSelectionModel()
                                                                            .getSelectedItem()
                                                                    : null);
                                            dialog.showAndWait().ifPresent(e -> load(e, true));
                                        }));
    }

    private void refresh() {
        if (draft == null || busy || view.editors.pending.get()) return;
        Draft current = collect();
        busy = true;
        long intent = ++epoch;
        controls();
        service.capture(view.from.getValue(), view.through.getValue())
                .thenApply(base -> new ScenarioEngine().refresh(current, base))
                .whenComplete(
                        (refresh, error) ->
                                Platform.runLater(
                                        () -> {
                                            if (disposed || intent != epoch) return;
                                            busy = false;
                                            controls();
                                            if (error != null) {
                                                feedback(error);
                                                return;
                                            }
                                            if (!refresh.conflicts().isEmpty()) {
                                                message(
                                                        window,
                                                        "Nuova base non applicata",
                                                        String.join("\n", refresh.conflicts())
                                                                + "\n\n"
                                                                + "Ripristina le modifiche indicate"
                                                                + " nelle rispettive schede e"
                                                                + " riprova, oppure crea uno"
                                                                + " scenario separato. La vecchia"
                                                                + " base e tutte le modifiche sono"
                                                                + " conservate.");
                                                return;
                                            }
                                            String changes =
                                                    refresh.differences().isEmpty()
                                                            ? "Nessuna differenza nei dati"
                                                                    + " calcolabili."
                                                            : String.join(
                                                                    "\n", refresh.differences());
                                            Dialog<Boolean> dialog = new Dialog<>();
                                            if (window != null) dialog.initOwner(window);
                                            dialog.setTitle(
                                                    "Aggiorna base · verifica le differenze");
                                            TextArea text =
                                                    new TextArea(
                                                            changes
                                                                    + "\n\n"
                                                                    + "Le percentuali di aumento"
                                                                    + " saranno applicate ai nuovi"
                                                                    + " prezzi. I prezzi assoluti e"
                                                                    + " le modifiche locali saranno"
                                                                    + " conservati. Salva la bozza"
                                                                    + " per creare una nuova"
                                                                    + " revisione.");
                                            text.setEditable(false);
                                            text.setWrapText(true);
                                            text.setPrefSize(700, 350);
                                            dialog.getDialogPane().setContent(text);
                                            dialog.getDialogPane()
                                                    .getButtonTypes()
                                                    .addAll(ButtonType.OK, ButtonType.CANCEL);
                                            dialog.setResultConverter(b -> b == ButtonType.OK);
                                            dialog.showAndWait()
                                                    .filter(Boolean.TRUE::equals)
                                                    .ifPresent(
                                                            ok -> {
                                                                draft = refresh.accepted();
                                                                dirty = true;
                                                                metadata();
                                                                calculate();
                                                            });
                                        }));
    }

    private void scope() {
        if (draft == null || busy || view.editors.pending.get()) return;
        Dialog<Set<Long>> dialog = new Dialog<>();
        if (window != null) dialog.initOwner(window);
        dialog.setTitle("Prodotti coinvolti nello scenario");
        var selected = new HashSet<>(draft.products());
        TextField query = new TextField();
        query.setPromptText("Cerca linea, prodotto o colore");
        TableView<Product> table = new TableView<>();
        table.setEditable(true);
        var checked = new TableColumn<Product, Boolean>("Includi");
        checked.setPrefWidth(75);
        checked.setCellValueFactory(
                c -> {
                    var property =
                            new javafx.beans.property.SimpleBooleanProperty(
                                    selected.contains(c.getValue().id()));
                    property.addListener(
                            (o, b, a) -> {
                                if (a) selected.add(c.getValue().id());
                                else selected.remove(c.getValue().id());
                            });
                    return property;
                });
        checked.setCellFactory(CheckBoxTableCell.forTableColumn(checked));
        table.getColumns().add(checked);
        col(table, "Linea", 110, p -> Objects.toString(p.lineCode(), "—"));
        col(table, "Prodotto", 180, Product::code);
        col(table, "Colore", 100, p -> Objects.toString(p.color(), ""));
        col(table, "Stato", 100, p -> p.active() ? "Attivo" : "Archiviato");
        var all = draft.base().catalogue().products();
        table.getItems().setAll(all);
        query.textProperty()
                .addListener(
                        (o, b, a) ->
                                table.getItems()
                                        .setAll(
                                                all.stream()
                                                        .filter(
                                                                p ->
                                                                        (p.code()
                                                                                        + " "
                                                                                        + p
                                                                                                .lineCode()
                                                                                        + " "
                                                                                        + p.color())
                                                                                .toLowerCase(
                                                                                        Locale.ROOT)
                                                                                .contains(
                                                                                        a
                                                                                                .toLowerCase(
                                                                                                        Locale
                                                                                                                .ROOT)))
                                                        .toList()));
        Button yes = new Button("Includi visibili"), no = new Button("Escludi visibili");
        yes.setOnAction(
                e -> {
                    table.getItems().forEach(p -> selected.add(p.id()));
                    table.refresh();
                });
        no.setOnAction(
                e -> {
                    table.getItems().forEach(p -> selected.remove(p.id()));
                    table.refresh();
                });
        table.setPrefSize(720, 380);
        dialog.getDialogPane()
                .setContent(
                        new VBox(
                                10,
                                query,
                                new HBox(10, yes, no),
                                table,
                                new Label(
                                        "Si confrontano tutte le anagrafiche articolo di questi"
                                            + " prodotti, incluse le archiviate; ogni articolo usa"
                                            + " la sua predefinita approvata nella fotografia.")));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(b -> b == ButtonType.OK ? Set.copyOf(selected) : null);
        dialog.showAndWait()
                .ifPresent(
                        ids -> {
                            if (!draft.products().containsAll(ids)
                                    && (!draft.recipes().isEmpty() || !draft.models().isEmpty())
                                    && !confirm(
                                            window,
                                            "Le modifiche tecniche già impostate si applicheranno"
                                                + " anche ai nuovi prodotti che condividono quelle"
                                                + " composizioni o modelli. Proseguire e verificare"
                                                + " le configurazioni coinvolte?")) return;
                            Draft d = collect();
                            draft =
                                    d.state(
                                            d.name(),
                                            d.notes(),
                                            d.archived(),
                                            d.selection(),
                                            ids,
                                            d.prices(),
                                            d.recipes(),
                                            d.models(),
                                            d.transforms());
                            dirty = true;
                            metadata();
                            calculate();
                        });
    }

    private void export(boolean articles) {
        if (analysis == null || busy || view.editors.pending.get() || dirty) {
            view.feedback.setText(
                    "Salva la bozza prima di esportare, per identificare esattamente la"
                            + " revisione.");
            return;
        }
        Draft d = collect();
        var rows = articles ? ScenarioCsv.articles(analysis) : view.comparison.rows();
        CsvExportDialog.show(
                window,
                "Confronto scenario",
                articles
                        ? "Tutti gli articoli dell'ambito, inclusi risultati incompleti"
                        : "Vista corrente · " + view.comparison.level(),
                "confronto-scenario.csv",
                rows,
                ScenarioCsv.comparison(
                        d,
                        view.comparison.weight.getValue(),
                        articles ? "ARTICOLI" : view.comparison.level()),
                workers);
    }

    private void exportDetails() {
        if (analysis == null || draft == null || busy || dirty || view.editors.pending.get())
            return;
        Row selected =
                view.comparison.table.getSelectionModel().getSelectedItem() == null
                        ? view.preview.selected()
                        : view.comparison.table.getSelectionModel().getSelectedItem().row();
        if (selected == null) {
            view.feedback.setText("Seleziona un articolo nel confronto oppure nell'anteprima.");
            return;
        }
        CsvExportDialog.show(
                window,
                "Dettaglio articolo · base e scenario",
                "Quantità e costi per strato e polvere",
                "dettaglio-scenario.csv",
                ScenarioCsv.materials(selected),
                ScenarioCsv.materials(collect()),
                workers);
    }

    private void controls() {
        boolean none = draft == null, readonly = none || draft.archived();
        view.save.setDisable(
                busy
                        || none
                        || draft != null && draft.archived()
                        || !dirty
                        || view.editors.pending.get());
        view.duplicate.setDisable(busy || none || view.editors.pending.get());
        view.archive.setDisable(
                busy
                        || none
                        || draft != null && draft.version() == 0
                        || view.editors.pending.get());
        view.archive.setText(draft != null && draft.archived() ? "Ripristina" : "Archivia");
        view.history.setDisable(busy || none || draft != null && draft.version() == 0);
        view.create.setDisable(busy);
        view.archived.setDisable(busy);
        view.archived.setDisable(busy);
        view.saved.setDisable(busy);
        view.name.setDisable(busy || readonly);
        view.notes.setDisable(busy || readonly);
        view.mode.setDisable(busy || readonly || view.editors.pending.get());
        view.incoming.setDisable(
                busy
                        || readonly
                        || view.editors.pending.get()
                        || view.mode.getValue() != PowderPricing.Mode.AVANZATA);
        view.refresh.setDisable(busy || readonly || view.editors.pending.get());
        view.scope.setDisable(busy || readonly || view.editors.pending.get());
        view.from.setDisable(busy || readonly);
        view.through.setDisable(busy || readonly);
        view.editors.setDisable(busy || readonly);
        view.preview.setDisable(busy || none);
        view.comparison.setDisable(busy || none);
        view.steps.getTabs().get(2).setDisable(busy || none || view.editors.pending.get());
        for (Button b :
                List.of(
                        view.comparison.export,
                        view.comparison.exportArticles,
                        view.comparison.exportDetails))
            b.setDisable(busy || none || dirty || view.editors.pending.get());
        view.status.setText(
                none
                        ? "Nessuno scenario"
                        : (draft.archived() ? "Archiviato · " : "Bozza · ")
                                + "v"
                                + draft.version()
                                + (dirty ? " · non salvata" : " · salvata"));
        view.home.setDisable(saving);
    }

    public boolean canLeave() {
        if (saving) return false;
        if (!dirty && !view.editors.pending.get()) return true;
        return confirm(
                window,
                "Ci sono modifiche allo scenario non salvate o non applicate. Scartarle e"
                        + " proseguire?");
    }

    private void feedback(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null)
            error = error.getCause();
        view.feedback.setText(Objects.toString(error.getMessage(), "Operazione non riuscita."));
    }

    private void attachScene(Scene before, Scene after) {
        if (before != null) before.windowProperty().removeListener(windowChanged);
        if (after != null) after.windowProperty().addListener(windowChanged);
        attachWindow(after == null ? null : after.getWindow());
    }

    private void attachWindow(Window next) {
        if (window != null) window.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closing);
        window = next;
        if (window != null) window.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, closing);
    }

    public void dispose() {
        disposed = true;
        epoch++;
        listEpoch++;
        selectionEpoch++;
        attachScene(view.getScene(), null);
        view.sceneProperty().removeListener(sceneChanged);
    }
}
