package com.orodent.ecos.ton.features.production.view;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;
import com.orodent.ecos.ton.TonModule;
import com.orodent.ecos.ton.features.production.service.ProductionFixture;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.web.WebView;
import javafx.stage.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public final class ProductionUiProbe {
    Stage stage;
    ModuleSession session;
    ProductionFixture fixture;
    ConnectionProvider db;
    final List<Throwable> errors = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        try {
            new ProductionUiProbe().run();
            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    void run() throws Exception {
        String url =
                "jdbc:derby:memory:production_ui_" + UUID.randomUUID().toString().replace("-", "");
        db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        var workers = Executors.newFixedThreadPool(3);
        fixture = new ProductionFixture(db, workers);
        Platform.startup(() -> {});
        Platform.setImplicitExit(false);
        try {
            fx(
                    () -> {
                        Thread.currentThread()
                                .setUncaughtExceptionHandler(
                                        (t, e) -> {
                                            errors.add(e);
                                            e.printStackTrace();
                                        });
                        session =
                                new TonModule()
                                        .open(
                                                new ModuleContext(
                                                        db,
                                                        workers,
                                                        new ClientiCsvImportService(db, workers),
                                                        new VenditeCsvImportService(db, workers),
                                                        () -> {}));
                        stage = new Stage();
                        stage.setScene(new Scene(session.root(), 1720, 1040));
                        stage.getScene()
                                .getStylesheets()
                                .add(
                                        Path.of("../orodent-app/src/main/resources/css/global.css")
                                                .toUri()
                                                .toString());
                        stage.show();
                        button("ton-nav-production").fire();
                        button("ton-tech-productions-button").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-productions-new") != null
                                    && !button("ton-productions-new").isDisabled());
            fx(
                    () -> {
                        button("ton-productions-new").fire();
                        layout();
                        ((ComboBox<?>) node("ton-production-line"))
                                .getSelectionModel()
                                .selectFirst();
                        return null;
                    });
            await(() -> node("ton-production-color-" + fixture.product.id()) != null);
            fx(
                    () -> {
                        ((ToggleButton) node("ton-production-color-" + fixture.product.id()))
                                .fire();
                        return null;
                    });
            await(
                    () ->
                            view().quantities.getItems().size() == 6
                                    && !view().diameter.getItems().isEmpty());
            fx(
                    () -> {
                        field(14).setText("16");
                        for (var r : view().quantities.getItems()) {
                            if (r.article.id() == fixture.articles.get(18)) r.quantity.set(32);
                            if (r.article.id() == fixture.articles.get(20)) r.quantity.set(16);
                        }
                        view().notes.setText("Istruzioni reali <test>");
                        assertFalse(view().prepare.isDisabled());
                        screenshot("target/production-quantities.png");
                        return null;
                    });
            fx(
                    () -> {
                        view().tabs.getSelectionModel().select(1);
                        layout();
                        var yellow =
                                view().lookupAll(".powder-tint").stream()
                                        .filter(
                                                n ->
                                                        n instanceof Label label
                                                                && label.getText()
                                                                        .equals("ZPEX4.M YELLOW"))
                                        .map(Label.class::cast)
                                        .findFirst()
                                        .orElseThrow();
                        assertEquals(
                                javafx.scene.paint.Color.web("#fff1b8"),
                                yellow.getBackground().getFills().getFirst().getFill());
                        screenshot("target/production-weighing.png");
                        view().tabs.getSelectionModel().select(2);
                        return null;
                    });
            await(
                    () ->
                            preview() != null
                                    && preview().getEngine().getDocument() != null
                                    && body().contains("MODELLO UTENTE"));
            fx(
                    () -> {
                        assertTrue(body().contains("kg/cm²"));
                        assertTrue(body().contains("64"));
                        assertFalse(body().contains("P300"));
                        assertFalse(body().contains("83,3"));
                        assertTrue(body().contains("Istruzioni reali <test>"));
                        screenshot("target/production-user-document.png");
                        view().save.fire();
                        return null;
                    });
            await(() -> !view().save.isDisabled() && feedback().contains("Bozza salvata"));
            assertEquals(1, count("PIANI_PRODUZIONE"));
            assertEquals(0, count("PIANI_DOCUMENTI"));
            fx(
                    () -> {
                        view().back.fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-productions-table") instanceof TableView<?> t
                                    && t.getItems().size() == 1);
            fx(
                    () -> {
                        var t = (TableView<?>) node("ton-productions-table");
                        t.getSelectionModel().selectFirst();
                        t.fireEvent(doubleClick());
                        return null;
                    });
            await(
                    () ->
                            node("ton-production-quantities") != null
                                    && view().quantities.getItems().size() == 6
                                    && !view().save.isDisabled());
            fx(
                    () -> {
                        assertEquals("16", field(14).getText());
                        assertEquals("32", field(18).getText());
                        Platform.runLater(() -> answer(ButtonType.OK));
                        view().prepare.fire();
                        return null;
                    });
            await(
                    () ->
                            feedback().contains("foglio operativo conservato")
                                    && !view().duplicate.isDisabled());
            assertEquals(1, count("PIANI_DOCUMENTI"));
            assertEquals(0, count("PRODUZIONI"));
            assertEquals(0, count("PRODUZIONE_RIGHE"));
            var saved = fixture.service.load(1).join();
            assertEquals(64, saved.plan().count());
            assertTrue(saved.document().contains("MODELLO UTENTE"));
            assertTrue(saved.document().contains("Istruzioni reali &lt;test&gt;"));
            assertFalse(saved.document().contains("P300"));
            fx(
                    () -> {
                        assertTrue(view().locked.get());
                        assertTrue(field(14).isDisabled());
                        screenshot("target/production-prepared.png");
                        view().tabs.getSelectionModel().selectFirst();
                        stage.setWidth(1000);
                        stage.setHeight(760);
                        return null;
                    });
            Thread.sleep(250);
            fx(
                    () -> {
                        screenshot("target/production-compact.png");
                        assertTrue(
                                view().quantities.getHeight() >= 100,
                                "Quantity table remains usable");
                        assertTrue(
                                view().duplicate.getParent().getBoundsInParent().getHeight() > 35,
                                "Prepared actions remain visible");
                        stage.setWidth(1720);
                        stage.setHeight(1040);
                        view().duplicate.fire();
                        return null;
                    });
            await(
                    () ->
                            !view().locked.get()
                                    && view().quantities.getItems().size() == 6
                                    && !view().save.isDisabled());
            fx(
                    () -> {
                        assertEquals("16", field(14).getText());
                        Platform.runLater(() -> answer(ButtonType.OK));
                        view().clear.fire();
                        assertEquals("0", field(14).getText());
                        assertTrue(view().prepare.isDisabled());
                        Platform.runLater(() -> answer(ButtonType.CANCEL));
                        view().back.fire();
                        assertNotNull(node("ton-production-quantities"));
                        return null;
                    });
            fx(
                    () -> {
                        Platform.runLater(() -> answer(ButtonType.OK));
                        button("ton-nav-technical").fire();
                        button("ton-tech-products-button").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-products-table") instanceof TableView<?> t
                                    && !t.getItems().isEmpty());
            fx(
                    () -> {
                        var table = (TableView<?>) node("ton-products-table");
                        table.getSelectionModel().selectFirst();
                        table.fireEvent(doubleClick());
                        return null;
                    });
            await(() -> node("ton-product-tabs") != null);
            fx(
                    () -> {
                        var tabs = (TabPane) node("ton-product-tabs");
                        assertEquals(
                                java.util.List.of(
                                        "Anagrafica",
                                        "Articoli",
                                        "Specifiche di produzione",
                                        "Revisioni"),
                                tabs.getTabs().stream().map(Tab::getText).toList());
                        tabs.getSelectionModel().select(2);
                        return null;
                    });
            await(
                    () ->
                            node("ton-product-spec-version") instanceof ComboBox<?> b
                                    && !b.getItems().isEmpty());
            fx(
                    () -> {
                        var selected =
                                (com.orodent.ecos.ton.features.catalog.model.CatalogModels
                                                .Specification)
                                        ((ComboBox<?>) node("ton-product-spec-version")).getValue();
                        assertEquals(fixture.product.id(), selected.productId());
                        assertTrue(selected.preferred());
                        assertTrue(node("ton-product-spec-composition").isDisabled());
                        screenshot("target/product-shared-production-specifications.png");
                        assertFalse(button("ton-product-spec-calculation-rules").isDisabled());
                        button("ton-product-spec-calculation-rules").fire();
                        assertNotNull(node("ton-cubature-rules-page"));
                        assertEquals(
                                "TON › Cubaggio › Regole di cubatura",
                                ((Label) node("ton-breadcrumb")).getText());
                        screenshot("target/cubature-rules-from-specifications.png");
                        button("ton-nav-technical").fire();
                        button("ton-tech-models-button").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-models-table") instanceof TableView<?> t
                                    && !t.getItems().isEmpty());
            fx(
                    () -> {
                        var table = (TableView<?>) node("ton-models-table");
                        table.getSelectionModel().selectFirst();
                        table.fireEvent(doubleClick());
                        return null;
                    });
            await(() -> node("ton-model-tabs") != null);
            fx(
                    () -> {
                        var tabs = (TabPane) node("ton-model-tabs");
                        tabs.getSelectionModel()
                                .select(
                                        tabs.getTabs().stream()
                                                .filter(t -> t.getText().equals("Utilizzi"))
                                                .findFirst()
                                                .orElseThrow());
                        return null;
                    });
            await(
                    () ->
                            node("ton-model-usages-table") instanceof TableView<?> t
                                    && !t.getItems().isEmpty());
            fx(
                    () -> {
                        var table = (TableView<?>) node("ton-model-usages-table");
                        assertEquals(1, table.getItems().size());
                        var usage =
                                (com.orodent.ecos.ton.features.diskmodels.model.DiskModels.Usage)
                                        table.getItems().getFirst();
                        assertEquals(fixture.product.id(), usage.productId());
                        assertTrue(
                                table.getColumns().stream()
                                        .noneMatch(c -> c.getText().equals("Articolo")));
                        screenshot("target/model-product-usages.png");
                        table.getSelectionModel().selectFirst();
                        assertSharedUsages("ton-model-usages", "ton-model-tabs");
                        table.fireEvent(doubleClick());
                        assertNotNull(node("ton-model-editor"));
                        var row =
                                table.lookupAll(".table-row-cell").stream()
                                        .filter(
                                                n ->
                                                        n instanceof TableRow<?> tr
                                                                && !tr.isEmpty()
                                                                && tr.getIndex() == 0)
                                        .findFirst()
                                        .orElseThrow();
                        row.fireEvent(doubleClick());
                        return null;
                    });
            await(() -> node("ton-product-editor") != null);
            fx(
                    () -> {
                        assertEquals(
                                fixture.product.code(),
                                ((TextField) node("ton-product-code")).getText());
                        return null;
                    });
            fx(
                    () -> {
                        button("ton-nav-technical").fire();
                        button("ton-tech-compositions-button").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-compositions-table") instanceof TableView<?> t
                                    && !t.getItems().isEmpty());
            fx(
                    () -> {
                        var table = (TableView<?>) node("ton-compositions-table");
                        table.getSelectionModel().selectFirst();
                        table.fireEvent(doubleClick());
                        return null;
                    });
            await(() -> node("ton-composition-tabs") != null);
            fx(
                    () -> {
                        var tabs = (TabPane) node("ton-composition-tabs");
                        tabs.getSelectionModel()
                                .select(
                                        tabs.getTabs().stream()
                                                .filter(t -> t.getText().equals("Utilizzi"))
                                                .findFirst()
                                                .orElseThrow());
                        return null;
                    });
            await(
                    () ->
                            node("ton-composition-usages-table") instanceof TableView<?> t
                                    && !t.getItems().isEmpty());
            fx(
                    () -> {
                        assertSharedUsages("ton-composition-usages", "ton-composition-tabs");
                        var table = (TableView<?>) node("ton-composition-usages-table");
                        var usage =
                                (com.orodent.ecos.ton.features.compositions.model.Compositions
                                                .Usage)
                                        table.getItems().getFirst();
                        assertEquals(fixture.product.id(), usage.productId());
                        screenshot("target/composition-product-usages.png");
                        table.getSelectionModel().selectFirst();
                        table.fireEvent(
                                new javafx.scene.input.KeyEvent(
                                        javafx.scene.input.KeyEvent.KEY_PRESSED,
                                        "",
                                        "",
                                        javafx.scene.input.KeyCode.ENTER,
                                        false,
                                        false,
                                        false,
                                        false));
                        return null;
                    });
            await(() -> node("ton-product-editor") != null);
            fx(
                    () -> {
                        assertEquals(
                                fixture.product.code(),
                                ((TextField) node("ton-product-code")).getText());
                        return null;
                    });
            assertTrue(errors.isEmpty(), errors::toString);
        } finally {
            fx(
                    () -> {
                        session.close();
                        stage.close();
                        return null;
                    });
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
            Platform.exit();
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException ignored) {
            }
        }
    }

    private void assertSharedUsages(String id, String tabsId) {
        layout();
        assertTrue(
                node(id + "-table").getParent()
                        instanceof
                        com.orodent.ecos.ton.features.technical.view.TechnicalRecordsView<?>);
        assertNull(node(id + "-open"));
        assertNull(node(id + "-previous"));
        assertNull(node(id + "-next"));
        var table = (TableView<?>) node(id + "-table");
        var scroll =
                (ScrollPane)
                        ((TabPane) node(tabsId)).getSelectionModel().getSelectedItem().getContent();
        assertTrue(table.getHeight() > scroll.getViewportBounds().getHeight() * .65);
        var title = node(id + "-title").localToScene(node(id + "-title").getBoundsInLocal());
        var refresh = node(id + "-refresh").localToScene(node(id + "-refresh").getBoundsInLocal());
        assertEquals(title.getCenterY(), refresh.getCenterY(), 2);
        assertEquals(table.localToScene(table.getBoundsInLocal()).getMaxX(), refresh.getMaxX(), 3);
    }

    ProductionView view() {
        return (ProductionView) Objects.requireNonNull(node("ton-production-view"));
    }

    TextField field(int height) {
        view().quantities
                .scrollTo(
                        view().quantities.getItems().stream()
                                .filter(r -> r.article.id() == fixture.articles.get(height))
                                .findFirst()
                                .orElseThrow());
        layout();
        return (TextField)
                Objects.requireNonNull(node("ton-production-qty-" + fixture.articles.get(height)));
    }

    String feedback() {
        return view().lookupAll(".production-feedback").stream()
                .map(n -> ((Label) n).getText())
                .findFirst()
                .orElse("");
    }

    WebView preview() {
        return (WebView) node("ton-production-document-webview");
    }

    String body() {
        return String.valueOf(preview().getEngine().executeScript("document.body.innerText"));
    }

    int count(String table) {
        return db.withConnection(
                c -> {
                    try (var q = c.createStatement();
                            var r = q.executeQuery("SELECT COUNT(*) FROM TON." + table)) {
                        r.next();
                        return r.getInt(1);
                    }
                });
    }

    Node node(String id) {
        return stage.getScene().getRoot().lookup("#" + id);
    }

    Button button(String id) {
        return (Button) Objects.requireNonNull(node(id), id);
    }

    void layout() {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    void answer(ButtonType type) {
        for (var window : List.copyOf(Window.getWindows()))
            if (window instanceof Stage s
                    && s.isShowing()
                    && s.getScene().getRoot() instanceof DialogPane pane) {
                var b = (Button) pane.lookupButton(type);
                if (b != null) {
                    b.fire();
                    return;
                }
            }
        throw new AssertionError("Dialog not found");
    }

    void screenshot(String path) throws Exception {
        layout();
        var snap = stage.getScene().snapshot(null);
        var out =
                new java.awt.image.BufferedImage(
                        (int) snap.getWidth(),
                        (int) snap.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++)
            for (int x = 0; x < out.getWidth(); x++)
                out.setRGB(x, y, snap.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(out, "png", Path.of(path).toFile());
    }

    <T> T fx(Callable<T> task) throws Exception {
        var future =
                new FutureTask<T>(
                        () -> {
                            if (stage != null) layout();
                            return task.call();
                        });
        Platform.runLater(future);
        return future.get(25, TimeUnit.SECONDS);
    }

    void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (System.nanoTime() < deadline) {
            if (fx(condition::getAsBoolean)) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Production UI timed out: " + fx(() -> feedback()));
    }

    MouseEvent doubleClick() {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                5,
                5,
                5,
                5,
                MouseButton.PRIMARY,
                2,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                null);
    }
}
