package com.orodent.ecos.ton.features.powders.view;

import com.orodent.ecos.module.view.PowderCells;
import com.orodent.ecos.ton.features.powders.model.PowderModel.*;
import com.orodent.ecos.ton.features.technical.view.TonListView;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.*;

import java.util.List;
import java.util.function.Function;

public final class PowderListView extends TonListView<Row> {
    private final CheckBox inactive = new CheckBox("Mostra anche archiviate");

    public PowderListView() {
        super(
                "ton-powders",
                "Anagrafiche e schede tecniche versionate. Gli ossidi sono espressi in percentuale"
                        + " in peso (wt%).",
                "Cerca codice, nome o fornitore",
                "Nuova polvere");
        create().setId("ton-powder-new");
        inactive.setId("ton-powders-inactive");
        options(inactive);
        var table = table();
        table.getColumns()
                .setAll(
                        List.of(
                                powderColumn(),
                                column("Nome", r -> r.powder().name(), 200),
                                column("Fornitore", r -> r.powder().supplier(), 160),
                                column("Colore", r -> r.powder().colorLabel(), 110),
                                column(
                                        "Classe Y",
                                        r -> r.yttriaClass() == null ? "—" : r.yttriaClass() + "Y",
                                        80),
                                column("Schede tecniche", PowderListView::versions, 200),
                                column(
                                        "Polvere",
                                        r -> r.powder().active() ? "Attiva" : "Archiviata",
                                        90)));
        table.setPlaceholder(new Label("Nessuna polvere. Usa Nuova polvere per iniziare."));
    }

    private static TableColumn<Row, Row> powderColumn() {
        var column = new TableColumn<Row, Row>("Codice");
        column.setPrefWidth(110);
        PowderCells.column(
                column,
                r -> r.powder().code(),
                r -> r.powder().color() == null ? null : r.powder().color().name());
        return column;
    }

    public void showLoading() {
        loading();
    }

    public void showPage(Page page) {
        showRows(page.rows());
    }

    public void showError(String message) {
        error(message);
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

    public CheckBox inactive() {
        return inactive;
    }
}
