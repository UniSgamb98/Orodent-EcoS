package com.orodent.ecos.ton.features.powders.view;

import com.orodent.ecos.ton.features.powders.model.PowderModel.*;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.function.Function;

public final class PowderListView extends VBox {
    private final TextField search = new TextField();
    private final CheckBox inactive = new CheckBox("Mostra anche archiviate");
    private final TableView<Row> table = new TableView<>();
    private final Label result = new Label();
    private final Label feedback = new Label();
    private final Button create = button("Nuova polvere", "ton-powder-new", "primary-button");
    private final Button open = button("Apri scheda", "ton-powder-open", "primary-button");
    private final Button refresh = button("Ricarica", "ton-powders-refresh", "secondary-button");
    private final Button previous = button("←", "ton-powders-previous", "secondary-button");
    private final Button next = button("→", "ton-powders-next", "secondary-button");

    public PowderListView() {
        super(14);
        setId("ton-powders-list");
        getStyleClass().add("ton-powder-page");
        setMinWidth(0);
        Label heading = new Label("Polveri");
        heading.getStyleClass().add("ton-page-heading");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(12, heading, spacer, create);
        header.setAlignment(Pos.CENTER_LEFT);
        Label intro =
                new Label(
                        "Anagrafiche e schede tecniche versionate. Gli ossidi sono espressi in"
                            + " percentuale in peso (wt%).");
        intro.setWrapText(true);
        intro.getStyleClass().add("muted-label");
        search.setId("ton-powders-search");
        search.setPromptText("Cerca codice o nome");
        HBox.setHgrow(search, Priority.ALWAYS);
        inactive.setId("ton-powders-inactive");
        table.setId("ton-powders-table");
        table.setMinHeight(230);
        table.setPrefHeight(360);
        VBox.setVgrow(table, Priority.ALWAYS);
        table.getColumns()
                .setAll(
                        List.of(
                                column("Codice", r -> r.powder().code(), 120),
                                column("Nome", r -> r.powder().name(), 230),
                                column(
                                        "Classe Y",
                                        r -> r.yttriaClass() == null ? "—" : r.yttriaClass() + "Y",
                                        90),
                                column("Schede tecniche", PowderListView::versions, 220),
                                column(
                                        "Polvere",
                                        r -> r.powder().active() ? "Attiva" : "Archiviata",
                                        100)));
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("Nessuna polvere. Usa Nuova polvere per iniziare."));
        feedback.setId("ton-powders-feedback");
        feedback.setWrapText(true);
        feedback.getStyleClass().add("ton-error-feedback");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox pages = new HBox(10, result, gap, previous, next);
        pages.setAlignment(Pos.CENTER_LEFT);
        previous.setAccessibleText("Pagina precedente");
        next.setAccessibleText("Pagina successiva");
        open.setDisable(true);
        getChildren()
                .setAll(
                        header,
                        intro,
                        new HBox(10, search, refresh),
                        inactive,
                        table,
                        pages,
                        open,
                        feedback);
    }

    public void showLoading() {
        table.setDisable(true);
        refresh.setDisable(true);
        previous.setDisable(true);
        next.setDisable(true);
        result.setText("Caricamento…");
        feedback.setText("");
    }

    public void showPage(Page page) {
        table.getItems().setAll(page.rows());
        table.setDisable(false);
        refresh.setDisable(false);
        previous.setDisable(page.offset() == 0);
        next.setDisable(!page.hasNext());
        result.setText(
                page.total() == 0
                        ? "0 polveri"
                        : (page.offset() + 1)
                                + "–"
                                + (page.offset() + page.rows().size())
                                + " di "
                                + page.total());
        feedback.setText("");
    }

    public void showError(String message) {
        table.setDisable(false);
        refresh.setDisable(false);
        result.setText("Elenco non aggiornato");
        feedback.setText(message);
    }

    private static String versions(Row row) {
        String text =
                row.approvedVersion() == null
                        ? "Nessuna approvata"
                        : "v" + row.approvedVersion() + " approvata";
        return text + (row.draftVersion() == null ? "" : " · bozza v" + row.draftVersion());
    }

    private static TableColumn<Row, String> column(
            String title, Function<Row, String> value, int width) {
        TableColumn<Row, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return column;
    }

    static Button button(String title, String id, String style) {
        Button button = new Button(title);
        button.setId(id);
        button.getStyleClass().add(style);
        return button;
    }

    public TextField search() {
        return search;
    }

    public CheckBox inactive() {
        return inactive;
    }

    public TableView<Row> table() {
        return table;
    }

    public Button create() {
        return create;
    }

    public Button open() {
        return open;
    }

    public Button refresh() {
        return refresh;
    }

    public Button previous() {
        return previous;
    }

    public Button next() {
        return next;
    }
}
