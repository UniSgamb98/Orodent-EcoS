package com.orodent.ecos.costi;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.costi.products.service.ProductCostService;
import com.orodent.ecos.costi.products.view.ProductCostsView;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.*;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Tag("javafx")
class ProductCostsUiTest {
    private Stage stage;
    private ModuleSession session;
    private final List<Throwable> errors = new CopyOnWriteArrayList<>();

    @Test
    void costsAndExportsAllProductsAndGuardsUnsavedParameters() throws Exception {
        String oldLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/product-costs-ui.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        String url = "jdbc:derby:memory:costs_ui_" + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        ProductCostFixture.seed(db);
        var workers = Executors.newFixedThreadPool(2);
        var homes = new AtomicInteger();
        FxTestSupport.start();
        try {
            fx(
                    () -> {
                        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> errors.add(e));
                        session =
                                new CostiModule()
                                        .open(
                                                new ModuleContext(
                                                        db,
                                                        workers,
                                                        new ClientiCsvImportService(db, workers),
                                                        new VenditeCsvImportService(db, workers),
                                                        homes::incrementAndGet));
                        stage = new Stage();
                        stage.setTitle("Orodent-EcoS · Costi prodotti");
                        stage.setScene(new Scene(session.root(), 1440, 900));
                        stage.getScene()
                                .getStylesheets()
                                .add(
                                        Path.of("../orodent-app/src/main/resources/css/global.css")
                                                .toUri()
                                                .toString());
                        stage.show();
                        button("costi-nav-products").fire();
                        return null;
                    });
            await(
                    () ->
                            view() != null
                                    && view().articles.getItems().size() == 2
                                    && !view().commonCost.isDisabled());
            fx(
                    () -> {
                        assertNull(view().articles.getItems().getFirst().total());
                        view().commonCost.setText("12");
                        return null;
                    });
            await(() -> firstComplete() && !view().save.isDisabled());
            fx(
                    () -> {
                        assertTrue(view().exportAll.isDisabled());
                        view().save.fire();
                        return null;
                    });
            await(
                    () ->
                            !view().exportAll.isDisabled()
                                    && label("costs-products-parameter-version")
                                            .contains("revisione 1"));
            fx(
                    () -> {
                        view().specific.fire();
                        view().articleCost.setText("");
                        return null;
                    });
            await(() -> label("costs-products-feedback").contains("Anteprima sospesa"));
            fx(
                    () -> {
                        assertFalse(
                                view().articleCost.isDisabled(),
                                "An incomplete override must remain editable");
                        assertFalse(view().articles.getItems().isEmpty());
                        view().articleCost.setText("0");
                        return null;
                    });
            await(
                    () ->
                            firstComplete()
                                    && view().articles
                                            .getItems()
                                            .getFirst()
                                            .total()
                                            .toPlainString()
                                            .startsWith("36.6825"));
            fx(
                    () -> {
                        view().save.fire();
                        return null;
                    });
            await(
                    () ->
                            !view().exportAll.isDisabled()
                                    && label("costs-products-parameter-version")
                                            .contains("revisione 2"));
            fx(
                    () -> {
                        view().commonCost.setText("12.00001");
                        return null;
                    });
            await(() -> label("costs-products-feedback").contains("massimo 4 decimali"));
            fx(
                    () -> {
                        assertTrue(view().save.isDisabled());
                        assertTrue(view().exportAll.isDisabled());
                        view().cancel.fire();
                        return null;
                    });
            await(() -> firstComplete() && !view().exportAll.isDisabled());
            fx(
                    () -> {
                        assertEquals("12", view().commonCost.getText());
                        assertEquals("0", view().articleCost.getText());
                        view().search.setText("MSTA2");
                        assertEquals(1, view().products.getItems().size());
                        view().exportAll.fire();
                        return null;
                    });
            await(() -> exportPreview() != null);
            fx(
                    () -> {
                        TableView<?> preview = exportPreview();
                        assertEquals(3, preview.getItems().size());
                        assertTrue(
                                preview.getItems().stream()
                                        .map(Object::toString)
                                        .anyMatch(s -> s.contains("MSTA3")));
                        assertTrue(
                                preview.getColumns().stream()
                                        .anyMatch(
                                                c ->
                                                        c.getText()
                                                                .equals(
                                                                        "Costo totale"
                                                                            + " (EUR/articolo)")));
                        closeExport();
                        view().exportFiltered.fire();
                        return null;
                    });
            await(() -> exportPreview() != null);
            fx(
                    () -> {
                        assertEquals(2, exportPreview().getItems().size());
                        closeExport();
                        view().exportDetail.fire();
                        return null;
                    });
            await(() -> exportPreview() != null);
            fx(
                    () -> {
                        assertEquals(4, exportPreview().getItems().size());
                        closeExport();
                        view().alternatives.fire();
                        return null;
                    });
            await(() -> view().articles.getItems().size() == 3);
            fx(
                    () -> {
                        view().exportAll.fire();
                        return null;
                    });
            await(() -> exportPreview() != null);
            fx(
                    () -> {
                        assertEquals(
                                3,
                                exportPreview().getItems().size(),
                                "Global prices must use preferred configurations even when"
                                    + " alternatives are shown");
                        closeExport();
                        return null;
                    });
            fx(
                    () -> {
                        view().commonCost.setText("13");
                        Platform.runLater(() -> answer(ButtonType.CANCEL));
                        button("costi-nav-powders").fire();
                        assertNotNull(view());
                        assertEquals("13", view().commonCost.getText());
                        Platform.runLater(() -> answer(ButtonType.CANCEL));
                        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
                        assertTrue(stage.isShowing());
                        view().cancel.fire();
                        return null;
                    });
            await(() -> !view().exportAll.isDisabled());
            fx(
                    () -> {
                        button("costi-nav-powders").fire();
                        return null;
                    });
            await(() -> stage.getScene().lookup("#costi-powders-table") != null);
            fx(
                    () -> {
                        button("costi-nav-products").fire();
                        return null;
                    });
            await(
                    () ->
                            firstComplete()
                                    && label("costs-products-parameter-version")
                                            .contains("revisione 2"));
            fx(
                    () -> {
                        view().archived.fire();
                        view().commonCost.setText("13");
                        Platform.runLater(() -> Platform.runLater(() -> answer(ButtonType.CANCEL)));
                        view().products.getSelectionModel().select(1);
                        return null;
                    });
            await(
                    () ->
                            view().products.getSelectionModel().getSelectedItem() != null
                                    && view().products.getSelectionModel().getSelectedItem().id()
                                            == 1);
            fx(
                    () -> {
                        assertEquals("13", view().commonCost.getText());
                        view().cancel.fire();
                        return null;
                    });
            await(() -> !view().exportAll.isDisabled());
            fx(
                    () -> {
                        assertEquals("12", view().commonCost.getText());
                        assertEquals("0", view().articleCost.getText());
                        stage.setWidth(1100);
                        stage.setHeight(780);
                        return null;
                    });
            await(() -> stage.getWidth() == 1100 && view().getWidth() <= 1100);
            assertEquals(
                    2, new ProductCostService(db, workers).load().join().parameters(1).version());
            fx(
                    () -> {
                        session.close();
                        session = null;
                        return null;
                    });
            assertEquals(42, workers.submit(() -> 42).get(5, TimeUnit.SECONDS));
            assertEquals(0, homes.get());
            assertTrue(errors.isEmpty(), errors::toString);
        } finally {
            fx(
                    () -> {
                        if (session != null) session.close();
                        for (Window w : List.copyOf(Window.getWindows())) w.hide();
                        return null;
                    });
            workers.shutdown();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException expected) {
                assertEquals("08006", expected.getSQLState());
            }
            if (oldLog == null) System.clearProperty("derby.stream.error.file");
            else System.setProperty("derby.stream.error.file", oldLog);
        }
    }

    private boolean firstComplete() {
        return view() != null
                && !view().articles.getItems().isEmpty()
                && view().articles.getItems().getFirst().total() != null;
    }

    private ProductCostsView view() {
        return (ProductCostsView) stage.getScene().lookup("#costi-products-module");
    }

    private Button button(String id) {
        return (Button) stage.getScene().lookup("#" + id);
    }

    private String label(String id) {
        return ((Label) stage.getScene().lookup("#" + id)).getText();
    }

    private DialogPane exportPane() {
        for (Window w : Window.getWindows())
            if (w.isShowing()
                    && w != stage
                    && w.getScene() != null
                    && w.getScene().getRoot() instanceof DialogPane pane
                    && pane.lookup("#csv-export-preview") != null) return pane;
        return null;
    }

    private TableView<?> exportPreview() {
        var pane = exportPane();
        return pane == null ? null : (TableView<?>) pane.lookup("#csv-export-preview");
    }

    private void closeExport() {
        ((Button) exportPane().lookupButton(ButtonType.CLOSE)).fire();
    }

    private void answer(ButtonType type) {
        for (Window w : Window.getWindows())
            if (w.isShowing()
                    && w != stage
                    && w.getScene() != null
                    && w.getScene().getRoot() instanceof DialogPane pane) {
                ((Button) pane.lookupButton(type)).fire();
                return;
            }
        fail("Dialogo di conferma assente");
    }

    private <T> T fx(Callable<T> work) throws Exception {
        var future = new CompletableFuture<T>();
        Platform.runLater(
                () -> {
                    try {
                        future.complete(work.call());
                    } catch (Throwable e) {
                        future.completeExceptionally(e);
                    }
                });
        return future.get(15, TimeUnit.SECONDS);
    }

    private void await(Callable<Boolean> test) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (System.nanoTime() < deadline) {
            if (fx(test)) return;
            Thread.sleep(40);
        }
        fail("Timeout interfaccia costi prodotti");
    }
}
