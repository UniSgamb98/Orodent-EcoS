package com.orodent.ecos.ton.features.products.controller;

import com.orodent.ecos.ton.features.products.model.ProductDraft;
import com.orodent.ecos.ton.features.products.model.ProductPage;
import com.orodent.ecos.ton.features.products.model.TonProduct;
import com.orodent.ecos.ton.features.products.service.ProductService;
import com.orodent.ecos.ton.features.products.view.ProductsView;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.util.Duration;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class ProductsController implements com.orodent.ecos.ton.navigation.FeatureController {
    private final ProductsView view;
    private final ProductService service;
    private final PauseTransition debounce = new PauseTransition(Duration.millis(250));
    private final SimpleBooleanProperty saving = new SimpleBooleanProperty();
    private final ChangeListener<String> searchListener;
    private final ChangeListener<TonProduct> selectionListener;
    private CompletableFuture<ProductPage> load;
    private TonProduct original;
    private int offset;
    private volatile boolean disposed;

    public ProductsController(ProductsView view, ProductService service) {
        this.view = view; this.service = service;
        searchListener = (observable, before, after) -> debounce.playFromStart();
        selectionListener = (observable, before, after) -> view.editButton().setDisable(saving.get() || after == null);
        view.searchField().textProperty().addListener(searchListener);
        view.table().getSelectionModel().selectedItemProperty().addListener(selectionListener);
        debounce.setOnFinished(event -> reload(0));
        view.searchField().setOnAction(event -> reload(0));
        view.includeInactive().setOnAction(event -> reload(0));
        view.refreshButton().setOnAction(event -> reload(offset));
        view.previousButton().setOnAction(event -> reload(Math.max(0, offset - ProductService.PAGE_SIZE)));
        view.nextButton().setOnAction(event -> reload(offset + ProductService.PAGE_SIZE));
        view.editButton().setOnAction(event -> editSelected());
        view.newButton().setOnAction(event -> { if (canLeave()) edit(null); });
        view.cancelButton().setOnAction(event -> { if (canLeave()) view.showEditor(original); });
        view.saveButton().setOnAction(event -> save());
        view.editButton().setDisable(true);
    }

    public void loadInitialData() { reload(0); }
    public ReadOnlyBooleanProperty savingProperty() { return saving; }

    private void reload(int requestedOffset) {
        if (disposed) return;
        debounce.stop();
        if (load != null) load.cancel(false);
        offset = requestedOffset;
        view.showLoading();
        CompletableFuture<ProductPage> future = service.findPage(view.searchField().getText(),
                view.includeInactive().isSelected(), offset);
        load = future;
        future.whenComplete((page, error) -> {
            if (disposed) return;
            Platform.runLater(() -> {
                if (disposed || load != future) return;
                load = null;
                if (error != null) { view.showLoadError(message(error)); return; }
                if (page.products().isEmpty() && page.total() > 0 && offset > 0) {
                    reload((int) ((page.total() - 1) / ProductService.PAGE_SIZE) * ProductService.PAGE_SIZE);
                    return;
                }
                view.showPage(page);
            });
        });
    }

    private void editSelected() {
        TonProduct product = view.table().getSelectionModel().getSelectedItem();
        if (product != null && canLeave()) edit(product);
    }

    private void edit(TonProduct product) {
        original = product; view.showEditor(product); view.codeField().requestFocus();
    }

    public boolean canLeave() {
        if (saving.get()) return false;
        if (!isDirty()) return true;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Ci sono modifiche non salvate. Vuoi scartarle?", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Modifiche al prodotto"); alert.setHeaderText("Modifiche non salvate");
        if (view.getScene() != null) alert.initOwner(view.getScene().getWindow());
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    private boolean isDirty() {
        String code = view.codeField().getText().strip().toUpperCase(Locale.ROOT);
        String description = view.descriptionField().getText().strip();
        return !code.equals(original == null ? "" : original.code())
                || !description.equals(original == null ? "" : original.description())
                || view.activeField().isSelected() != (original == null || original.active());
    }

    private void save() {
        if (disposed || saving.get()) return;
        ProductDraft draft;
        try { draft = new ProductDraft(view.codeField().getText(), view.descriptionField().getText(), view.activeField().isSelected()); }
        catch (IllegalArgumentException invalid) { view.showFeedback(invalid.getMessage(), true); return; }
        saving.set(true); view.setSaving(true);
        CompletableFuture<TonProduct> command;
        try { command = original == null ? service.create(draft) : service.update(original, draft); }
        catch (RuntimeException failure) {
            saving.set(false); view.setSaving(false); view.showFeedback(message(failure), true); return;
        }
        // Writes finish with commit/rollback. Closing a page never cancels or replays them.
        command.whenComplete((product, error) -> {
            if (disposed) {
                if (error != null) System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                        "Salvataggio TON terminato con errore dopo la chiusura della pagina", error);
                return;
            }
            Platform.runLater(() -> {
                if (disposed) return;
                saving.set(false); view.setSaving(false);
                if (error != null) { view.showFeedback(message(error), true); return; }
                original = product; view.showEditor(product); view.showFeedback("Prodotto salvato.", false); reload(0);
            });
        });
    }

    private static String message(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        return failure.getMessage() == null ? "Operazione non riuscita. Riprova dopo aver verificato la connessione." : failure.getMessage();
    }

    public void dispose() {
        disposed = true; debounce.stop(); debounce.setOnFinished(null);
        if (load != null) load.cancel(false);
        load = null;
        view.searchField().textProperty().removeListener(searchListener);
        view.table().getSelectionModel().selectedItemProperty().removeListener(selectionListener);
        view.searchField().setOnAction(null); view.includeInactive().setOnAction(null);
        for (var button : java.util.List.of(view.refreshButton(), view.previousButton(), view.nextButton(),
                view.editButton(), view.newButton(), view.cancelButton(), view.saveButton())) button.setOnAction(null);
    }
}
