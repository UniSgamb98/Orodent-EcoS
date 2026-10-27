package com.orodent.ecos.ton.features.products.view;

import com.orodent.ecos.ton.features.products.model.ProductPage;
import com.orodent.ecos.ton.features.products.model.TonProduct;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class ProductsView extends HBox {
    private final TextField search = new TextField();
    private final CheckBox includeInactive = new CheckBox("Mostra anche archiviati");
    private final TableView<TonProduct> table = new TableView<>();
    private final Label result = new Label();
    private final Label loadError = new Label();
    private final Button refresh = button("Aggiorna", "ton-products-refresh", "nav-button");
    private final Button previous = button("←", "ton-products-previous", "nav-button");
    private final Button next = button("→", "ton-products-next", "nav-button");
    private final Button edit = button("Modifica selezionato", "ton-product-edit", "secondary-button");
    private final Button create = button("Nuovo prodotto", "ton-product-new", "secondary-button");
    private final TextField code = new TextField();
    private final TextArea description = new TextArea();
    private final CheckBox active = new CheckBox("Prodotto attivo");
    private final Label editorTitle = new Label("Nuovo prodotto");
    private final Label feedback = new Label();
    private final Button save = button("Salva", "ton-product-save", "primary-button");
    private final Button cancel = button("Annulla modifiche", "ton-product-cancel", "secondary-button");
    private final VBox editor = new VBox(12);

    public ProductsView() {
        setId("ton-products-view");
        setSpacing(24);
        setPadding(new Insets(24));
        search.setId("ton-products-search");
        search.setPromptText("Cerca codice o descrizione");
        search.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(search, Priority.ALWAYS);
        includeInactive.setId("ton-products-include-inactive");
        HBox filters = new HBox(10, search, refresh);
        filters.setAlignment(Pos.CENTER_LEFT);
        TableColumn<TonProduct, String> codeColumn = column("Codice", TonProduct::code, 150);
        TableColumn<TonProduct, String> descriptionColumn = column("Descrizione", TonProduct::description, 330);
        TableColumn<TonProduct, String> statusColumn = column("Stato", p -> p.active() ? "Attivo" : "Archiviato", 95);
        codeColumn.setMinWidth(110); codeColumn.setMaxWidth(180);
        descriptionColumn.setMinWidth(120);
        statusColumn.setMinWidth(90); statusColumn.setMaxWidth(110);
        table.getColumns().setAll(java.util.List.of(codeColumn, descriptionColumn, statusColumn));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setId("ton-products-table");
        table.setPlaceholder(new Label("Nessun prodotto trovato."));
        table.setMinHeight(180);
        VBox.setVgrow(table, Priority.ALWAYS);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox pages = new HBox(8, result, spacer, previous, next);
        pages.setAlignment(Pos.CENTER_LEFT);
        previous.setAccessibleText("Pagina precedente"); next.setAccessibleText("Pagina successiva");
        loadError.setId("ton-products-load-error");
        loadError.setWrapText(true);
        loadError.getStyleClass().add("ton-error-feedback");
        loadError.setVisible(false); loadError.setManaged(false);
        VBox catalogue = new VBox(12, filters, includeInactive, table, loadError, pages, edit);
        catalogue.setMinWidth(320);
        HBox.setHgrow(catalogue, Priority.ALWAYS);

        editorTitle.getStyleClass().add("card-title");
        code.setId("ton-product-code");
        code.setPromptText("Esempio: ZR A2");
        code.setAccessibleText("Codice del prodotto tecnico");
        code.setMaxWidth(Double.MAX_VALUE);
        description.setId("ton-product-description");
        description.setPromptText("Descrizione del prodotto tecnico");
        description.setAccessibleText("Descrizione del prodotto tecnico");
        description.setWrapText(true);
        description.setPrefRowCount(3);
        description.setPrefHeight(95);
        active.setId("ton-product-active"); active.setSelected(true);
        feedback.setId("ton-product-feedback"); feedback.setWrapText(true);
        editor.getStyleClass().add("info-card");
        editor.getChildren().setAll(editorTitle, new Label("Codice *"), code,
                new Label("Descrizione"), description, active, save, cancel, feedback);
        editor.setPrefWidth(300); editor.setMinWidth(260); editor.setMaxWidth(340);
        VBox form = new VBox(12, create, editor);
        form.setMinWidth(260); form.setPrefWidth(300);
        getChildren().addAll(catalogue, form);
    }

    private static TableColumn<TonProduct, String> column(String title, java.util.function.Function<TonProduct, String> value, int width) {
        TableColumn<TonProduct, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        column.setPrefWidth(width); column.setSortable(false);
        return column;
    }

    private static Button button(String text, String id, String style) {
        Button button = new Button(text); button.setId(id); button.getStyleClass().add(style); return button;
    }

    public void showLoading() {
        result.setText("Caricamento…"); table.setDisable(true); refresh.setDisable(true);
        table.setPlaceholder(new Label("Caricamento prodotti…"));
        previous.setDisable(true); next.setDisable(true);
        loadError.setVisible(false); loadError.setManaged(false);
    }

    public void showPage(ProductPage page) {
        table.setItems(FXCollections.observableArrayList(page.products()));
        table.setPlaceholder(new Label("Nessun prodotto trovato."));
        table.setDisable(false); refresh.setDisable(false);
        result.setText(page.total() == 0 ? "0 prodotti" : (page.offset() + 1) + "–"
                + (page.offset() + page.products().size()) + " di " + page.total());
        previous.setDisable(page.offset() == 0); next.setDisable(!page.hasNext());
    }

    public void showLoadError(String message) {
        result.setText("Elenco non aggiornato"); table.setDisable(false); refresh.setDisable(false);
        table.setPlaceholder(new Label("Elenco non disponibile. Premi Aggiorna per riprovare."));
        loadError.setText(message); loadError.setVisible(true); loadError.setManaged(true);
    }

    public void showEditor(TonProduct product) {
        editorTitle.setText(product == null ? "Nuovo prodotto" : "Modifica " + product.code());
        code.setText(product == null ? "" : product.code());
        description.setText(product == null ? "" : product.description());
        active.setSelected(product == null || product.active());
        feedback.setText("");
    }

    public void setSaving(boolean saving) {
        editor.setDisable(saving); create.setDisable(saving); edit.setDisable(saving || table.getSelectionModel().getSelectedItem() == null);
        save.setText(saving ? "Salvataggio…" : "Salva");
    }

    public void showFeedback(String message, boolean error) {
        feedback.setText(message);
        feedback.getStyleClass().remove("ton-error-feedback");
        if (error) feedback.getStyleClass().add("ton-error-feedback");
    }

    public TextField searchField() { return search; }
    public CheckBox includeInactive() { return includeInactive; }
    public TableView<TonProduct> table() { return table; }
    public Button refreshButton() { return refresh; }
    public Button previousButton() { return previous; }
    public Button nextButton() { return next; }
    public Button editButton() { return edit; }
    public Button newButton() { return create; }
    public TextField codeField() { return code; }
    public TextArea descriptionField() { return description; }
    public CheckBox activeField() { return active; }
    public Button saveButton() { return save; }
    public Button cancelButton() { return cancel; }
}
