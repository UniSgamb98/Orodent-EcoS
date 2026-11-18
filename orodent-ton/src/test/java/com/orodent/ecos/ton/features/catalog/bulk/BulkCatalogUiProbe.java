package com.orodent.ecos.ton.features.catalog.bulk;

import static com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;
import com.orodent.ecos.ton.TonModule;
import com.orodent.ecos.ton.features.catalog.bulk.model.BulkCatalogModels.*;
import com.orodent.ecos.ton.features.catalog.bulk.model.BulkCatalogModels.Parent;
import com.orodent.ecos.ton.features.catalog.bulk.service.BulkCatalogService;
import com.orodent.ecos.ton.features.catalog.bulk.view.*;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.service.CatalogService;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Separate JVM keeps JavaFX lifecycle independent from TON's other graphical fixtures. */
public final class BulkCatalogUiProbe {
    Stage stage;
    ModuleSession session;
    ConnectionProvider db;
    String url;
    final List<Throwable> errors = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        try {
            new BulkCatalogUiProbe().run();
            System.exit(0);
        } catch (Throwable error) {
            error.printStackTrace();
            System.exit(1);
        }
    }

    private void run() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        url = "jdbc:derby:memory:bulk_ui_" + UUID.randomUUID().toString().replace("-", "");
        AtomicInteger fxSql = new AtomicInteger(), homes = new AtomicInteger();
        db =
                () -> {
                    if (Platform.isFxApplicationThread()) fxSql.incrementAndGet();
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        ExecutorService workers = Executors.newFixedThreadPool(3);
        var service = new CatalogService(db, workers);
        Line line =
                (Line)
                        service.create(new LineDraft("MST", "Thor", "", "ZR", "", true))
                                .join()
                                .entry();
        Line otherLine =
                (Line)
                        service.create(new LineDraft("ZST", "Linea ZS", "", "ZS", "", true))
                                .join()
                                .entry();
        Product old =
                (Product)
                        service.create(ProductDraft.configured(line.id(), "A3", "", true))
                                .join()
                                .entry();
        Product zs =
                (Product)
                        service.create(ProductDraft.configured(otherLine.id(), "A1", "", true))
                                .join()
                                .entry();
        service.create(
                        new ArticleDraft(
                                "ZR9816MSTA3",
                                "Esistente",
                                old.id(),
                                new BigDecimal("98"),
                                new BigDecimal("16"),
                                "Storico intatto",
                                true))
                .join();
        var bulk = new BulkCatalogService(db, workers);
        var otherParent =
                bulk.parentsByIds(CatalogKind.LINES, List.of(otherLine.id())).join().getFirst();
        var otherProducts = new ArrayList<Seed>();
        for (int i = 0; i < 50; i++)
            otherProducts.add(new Seed(i, otherParent, "ZSTB" + i, "B" + i, "", "", true));
        bulk.create(bulk.preview(new Request(CatalogKind.PRODUCTS, "", otherProducts)).join())
                .join();
        Platform.startup(() -> {});
        Platform.setImplicitExit(false);
        try {
            fx(
                    () -> {
                        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> errors.add(e));
                        session =
                                new TonModule()
                                        .open(
                                                new ModuleContext(
                                                        db,
                                                        workers,
                                                        new ClientiCsvImportService(db, workers),
                                                        new VenditeCsvImportService(db, workers),
                                                        homes::incrementAndGet));
                        stage = new Stage();
                        stage.setScene(new Scene(session.root(), 1600, 1050));
                        stage.getScene()
                                .getStylesheets()
                                .add(
                                        Path.of("../orodent-app/src/main/resources/css/global.css")
                                                .toUri()
                                                .toString());
                        stage.show();
                        return null;
                    });
            fx(
                    () -> {
                        stage.setWidth(1900);
                        stage.setHeight(1050);
                        return null;
                    });
            Thread.sleep(250);
            fx(
                    () -> {
                        stage.setWidth(1120);
                        stage.setHeight(800);
                        return null;
                    });
            Thread.sleep(250);
            fx(
                    () -> {
                        assertNoOverlaps(stage.getScene().getRoot().lookupAll(".ton-feature-card"));
                        stage.setWidth(1600);
                        stage.setHeight(1050);
                        button("ton-lines-button").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-lines-table") instanceof TableView<?> t
                                    && !t.getItems().isEmpty());
            fx(
                    () -> {
                        var table = (TableView<Entry>) node("ton-lines-table");
                        table.getSelectionModel()
                                .select(
                                        table.getItems().stream()
                                                .filter(e -> e.id() == line.id())
                                                .findFirst()
                                                .orElseThrow());
                        button("ton-line-edit").fire();
                        return null;
                    });
            await(() -> node("ton-line-editor") != null);
            fx(
                    () -> {
                        var tabs = (TabPane) node("ton-line-editor").lookup(".tab-pane");
                        tabs.getSelectionModel()
                                .select(
                                        tabs.getTabs().stream()
                                                .filter(
                                                        t ->
                                                                t.getText()
                                                                        .equals(
                                                                                CatalogKind.PRODUCTS
                                                                                        .title()))
                                                .findFirst()
                                                .orElseThrow());
                        return null;
                    });
            await(
                    () ->
                            button("ton-line-children-bulk") != null
                                    && !button("ton-line-children-bulk").isDisabled());
            fx(
                    () -> {
                        button("ton-line-children-bulk").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-bulk-view") != null
                                    && !node("ton-bulk-view").isDisabled()
                                    && workspace().parents().value() != null);
            fx(
                    () -> {
                        assertEquals(line.id(), workspace().parents().value().id());
                        assertEquals(
                                "TON › Dati tecnici › Prodotti tecnici › Inserimento multiplo",
                                textLabel("ton-breadcrumb"));
                        for (String color : List.of("A1", "A2", "A3"))
                            ((CheckBox) node("ton-bulk-products-color-" + color)).fire();
                        return null;
                    });
            await(
                    () ->
                            enabled("ton-bulk-products-create")
                                    && textLabel("ton-bulk-products-count-new").equals("2"));
            assertEquals("1", fx(() -> textLabel("ton-bulk-products-count-existing")));
            fx(
                    () -> {
                        Platform.runLater(() -> button("ton-bulk-products-rule").fire());
                        return null;
                    });
            await(() -> dialog() != null);
            fx(
                    () -> {
                        assertTrue(dialog().lookup("#ton-bulk-rule-add-line").isDisabled());
                        ((ComboBox<String>) dialog().lookup("#ton-bulk-rule-separator"))
                                .setValue("_");
                        assertEquals(
                                "MST_A1",
                                ((Label) dialog().lookup("#ton-bulk-rule-example")).getText());
                        snapshotDialog("target/ton-bulk-rule-products.png");
                        ((Button) dialog().lookup("#ton-bulk-rule-apply")).fire();
                        return null;
                    });
            await(() -> textLabel("ton-bulk-products-count-invalid").equals("1"));
            fx(
                    () -> {
                        include("products", 2).fire();
                        return null;
                    });
            await(() -> enabled("ton-bulk-products-create"));
            fx(
                    () -> {
                        snapshot("target/ton-bulk-products.png");
                        button("ton-bulk-products-create").fire();
                        assertTrue(button("ton-home-button").isDisabled());
                        return null;
                    });
            await(
                    () ->
                            !node("ton-bulk-view").isDisabled()
                                    && textLabel("ton-bulk-products-feedback")
                                            .contains("Creati 2"));
            List<Product> mst =
                    service
                            .page(
                                    CatalogKind.PRODUCTS,
                                    new Filter("", false, line.id(), null, null, null),
                                    0)
                            .join()
                            .rows()
                            .stream()
                            .map(Product.class::cast)
                            .toList();
            assertEquals(3, mst.size());
            Product p1 = mst.stream().filter(p -> p.color().equals("A1")).findFirst().orElseThrow();
            Product p2 = mst.stream().filter(p -> p.color().equals("A2")).findFirst().orElseThrow();
            assertEquals("MST_A1", p1.code());
            fx(
                    () -> {
                        button("ton-bulk-mode-articles").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-bulk-select-product-" + p1.id()) != null
                                    && node("ton-bulk-select-product-" + p2.id()) != null);
            fx(
                    () -> {
                        for (Product p : List.of(p1, p2, old))
                            ((CheckBox) node("ton-bulk-select-product-" + p.id())).fire();
                        return null;
                    });
            await(
                    () ->
                            enabled("ton-bulk-articles-create")
                                    && textLabel("ton-bulk-articles-count-total").equals("15")
                                    && textLabel("ton-bulk-articles-count-existing").equals("1"));
            await(() -> ((BulkCatalogView) node("ton-bulk-view")).isFitToHeight());
            fx(
                    () -> {
                        assertWideFits();
                        return null;
                    });
            fx(
                    () -> {
                        assertEquals("MST_A1", p1.code());
                        assertEquals(
                                "ZR9812MSTA1",
                                workspace().table().getItems().getFirst().seed().code());
                        assertNull(node("ton-bulk-articles-generate"));
                        assertNull(node("ton-bulk-articles-code-example"));
                        assertNull(node("ton-bulk-articles-line-filter-clear"));
                        assertNull(node("ton-bulk-articles-line-filter-search"));
                        assertTrue(workspace().lookupAll(".bulk-context").isEmpty());
                        assertTrue(workspace().lookupAll(".bulk-product-tag").isEmpty());
                        assertEquals(
                                "Seleziona tutto",
                                button("ton-bulk-articles-parents-all").getText());
                        assertTrue(
                                ((ScrollPane) node("ton-bulk-articles-parents")).getPrefHeight()
                                        >= 300);
                        var lines = (ComboBox<Parent>) node("ton-bulk-articles-line-filter");
                        lines.setValue(
                                lines.getItems().stream()
                                        .filter(p -> p.id() == otherLine.id())
                                        .findFirst()
                                        .orElseThrow());
                        assertTrue(workspace().parents().selected().isEmpty());
                        assertFalse(enabled("ton-bulk-articles-create"));
                        return null;
                    });
            await(
                    () ->
                            node("ton-bulk-select-product-" + zs.id()) != null
                                    && textLabel("ton-bulk-articles-count-total").equals("0"));
            fx(
                    () -> {
                        button("ton-bulk-articles-parents-all").fire();
                        return null;
                    });
            await(
                    () ->
                            enabled("ton-bulk-articles-create")
                                    && workspace().parents().selected().size() == 51
                                    && textLabel("ton-bulk-articles-count-total").equals("255"));
            fx(
                    () -> {
                        assertWideFits();
                        assertTrue(
                                workspace().table().getItems().stream()
                                        .map(BulkWorkspace.Row::seed)
                                        .allMatch(
                                                seed -> seed.parent().lineId() == otherLine.id()));
                        var lines = (ComboBox<Parent>) node("ton-bulk-articles-line-filter");
                        lines.setValue(
                                lines.getItems().stream()
                                        .filter(p -> p.id() == line.id())
                                        .findFirst()
                                        .orElseThrow());
                        return null;
                    });
            await(
                    () ->
                            node("ton-bulk-select-product-" + p1.id()) != null
                                    && textLabel("ton-bulk-articles-count-total").equals("0"));
            fx(
                    () -> {
                        button("ton-bulk-articles-parents-all").fire();
                        return null;
                    });
            await(
                    () ->
                            enabled("ton-bulk-articles-create")
                                    && textLabel("ton-bulk-articles-count-total").equals("15"));
            fx(
                    () -> {
                        button("ton-bulk-articles-parents-clear").fire();
                        return null;
                    });
            await(() -> textLabel("ton-bulk-articles-count-total").equals("0"));
            fx(
                    () -> {
                        for (Product product : List.of(p1, p2, old))
                            ((CheckBox) node("ton-bulk-select-product-" + product.id())).fire();
                        return null;
                    });
            await(
                    () ->
                            enabled("ton-bulk-articles-create")
                                    && textLabel("ton-bulk-articles-count-total").equals("15"));
            fx(
                    () -> {
                        assertFalse(workspace().table().isEditable());
                        assertTrue(
                                workspace().table().getColumns().stream()
                                        .filter(
                                                c ->
                                                        List.of(
                                                                        "Codice articolo",
                                                                        "Ø mm",
                                                                        "Altezza mm")
                                                                .contains(c.getText()))
                                        .noneMatch(TableColumn::isEditable));
                        assertTrue(workspace().table().lookupAll(".text-field").isEmpty());
                        return null;
                    });
            fx(
                    () -> {
                        include("articles", 4).fire();
                        field("ton-bulk-articles-heights").setText("12; x");
                        assertFalse(enabled("ton-bulk-articles-create"));
                        return null;
                    });
            await(() -> textLabel("ton-bulk-articles-feedback").contains("misure separate"));
            fx(
                    () -> {
                        field("ton-bulk-articles-heights").setText("12; 14; 16; 18; 20; 22");
                        return null;
                    });
            await(
                    () ->
                            enabled("ton-bulk-articles-create")
                                    && textLabel("ton-bulk-articles-count-total").equals("18"));
            fx(
                    () -> {
                        var rows = workspace().table().getItems();
                        assertEquals("ZR9812MSTA1", rows.getFirst().seed().code());
                        assertFalse(rows.get(4).seed().selected());
                        field("ton-bulk-articles-heights").setText("12; 14; 16; 18; 20");
                        return null;
                    });
            await(
                    () ->
                            enabled("ton-bulk-articles-create")
                                    && textLabel("ton-bulk-articles-count-total").equals("15"));
            fx(
                    () -> {
                        include("articles", 4).fire();
                        return null;
                    });
            await(
                    () ->
                            enabled("ton-bulk-articles-create")
                                    && textLabel("ton-bulk-articles-count-new").equals("14"));
            fx(
                    () -> {
                        Platform.runLater(() -> button("ton-bulk-articles-rule").fire());
                        return null;
                    });
            await(() -> dialog() != null);
            fx(
                    () -> {
                        dialog().lookupAll(".button").stream()
                                .filter(
                                        n ->
                                                n instanceof Button b
                                                        && "Rimuovi Altezza"
                                                                .equals(b.getAccessibleText()))
                                .map(Button.class::cast)
                                .findFirst()
                                .orElseThrow()
                                .fire();
                        ((Button) dialog().lookup("#ton-bulk-rule-apply")).fire();
                        return null;
                    });
            await(() -> textLabel("ton-bulk-articles-count-invalid").equals("15"));
            fx(
                    () -> {
                        assertFalse(enabled("ton-bulk-articles-create"));
                        assertTrue(textLabel("ton-bulk-articles-notice").contains("composizione"));
                        Platform.runLater(() -> button("ton-bulk-articles-rule").fire());
                        return null;
                    });
            await(() -> dialog() != null);
            fx(
                    () -> {
                        ((Button) dialog().lookup("#ton-bulk-rule-add-height")).fire();
                        dialog().lookupAll(".button").stream()
                                .filter(
                                        n ->
                                                n instanceof Button b
                                                        && "Sposta Altezza prima"
                                                                .equals(b.getAccessibleText()))
                                .map(Button.class::cast)
                                .findFirst()
                                .orElseThrow()
                                .fire();
                        ((Button) dialog().lookup("#ton-bulk-rule-apply")).fire();
                        return null;
                    });
            await(
                    () ->
                            enabled("ton-bulk-articles-create")
                                    && textLabel("ton-bulk-articles-count-new").equals("14"));
            fx(
                    () -> {
                        assertEquals(
                                p1.id(),
                                workspace().table().getItems().getFirst().seed().parent().id());
                        Platform.runLater(() -> answer(ButtonType.NO));
                        button("ton-home-button").fire();
                        assertEquals(0, homes.get());
                        return null;
                    });
            await(() -> enabled("ton-bulk-articles-create"));
            fx(
                    () -> {
                        stage.setHeight(500);
                        return null;
                    });
            await(() -> !((BulkCatalogView) node("ton-bulk-view")).isFitToHeight());
            fx(
                    () -> {
                        stage.setHeight(1050);
                        return null;
                    });
            await(() -> ((BulkCatalogView) node("ton-bulk-view")).isFitToHeight());
            fx(
                    () -> {
                        assertWideFits();
                        snapshot("target/ton-bulk-articles.png");
                        stage.setWidth(1050);
                        return null;
                    });
            await(() -> workspace().getWidth() < 1040);
            fx(
                    () -> {
                        assertNoOverlaps(workspace().lookupAll(".bulk-card"));
                        var cards =
                                workspace().lookupAll(".bulk-card").stream()
                                        .map(n -> n.localToScene(n.getBoundsInLocal()))
                                        .sorted(
                                                Comparator.comparingDouble(
                                                        javafx.geometry.Bounds::getMinY))
                                        .toList();
                        assertEquals(2, cards.size());
                        assertTrue(cards.get(1).getMinY() > cards.get(0).getMaxY());
                        ((ScrollPane) node("ton-bulk-view")).setVvalue(1);
                        snapshot("target/ton-bulk-compact.png");
                        button("ton-bulk-articles-create").fire();
                        return null;
                    });
            await(
                    () ->
                            !node("ton-bulk-view").isDisabled()
                                    && textLabel("ton-bulk-articles-feedback")
                                            .contains("Creati 14"));
            db.withConnection(
                    c -> {
                        try (var q = c.createStatement();
                                var r =
                                        q.executeQuery(
                                                "SELECT prodotto_id FROM TON.ARTICOLI WHERE"
                                                        + " codice='ZR9812MSTA1'")) {
                            assertTrue(r.next());
                            assertEquals(p1.id(), r.getLong(1));
                        }
                        try (var q = c.createStatement();
                                var r =
                                        q.executeQuery(
                                                "SELECT COUNT(*) FROM"
                                                        + " TON.CONFIGURAZIONI_ARTICOLO")) {
                            r.next();
                            assertEquals(0, r.getInt(1));
                        }
                        return null;
                    });
            fx(
                    () -> {
                        button("ton-home-button").fire();
                        assertEquals(1, homes.get());
                        return null;
                    });
            assertEquals(0, fxSql.get(), "JDBC must stay off the FX thread");
            assertTrue(errors.isEmpty(), errors::toString);
        } catch (Exception | AssertionError error) {
            try {
                fx(
                        () -> {
                            snapshot("target/ton-bulk-failure.png");
                            return null;
                        });
            } catch (Exception ignored) {
            }
            throw error;
        } finally {
            fx(
                    () -> {
                        if (session != null) session.close();
                        for (Window w : List.copyOf(Window.getWindows())) w.hide();
                        return null;
                    });
            workers.shutdown();
            assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS));
            Platform.exit();
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException e) {
                assertEquals("08006", e.getSQLState());
            }
        }
    }

    private BulkWorkspace workspace() {
        return ((BulkCatalogView) node("ton-bulk-view")).workspace();
    }

    private Node node(String id) {
        return stage.getScene().lookup("#" + id);
    }

    private ButtonBase button(String id) {
        return (ButtonBase) node(id);
    }

    private TextField field(String id) {
        return (TextField) node(id);
    }

    private String textLabel(String id) {
        return ((Label) node(id)).getText();
    }

    private boolean enabled(String id) {
        return button(id) != null && !button(id).isDisabled();
    }

    private CheckBox include(String kind, int key) {
        layout();
        return (CheckBox) node("ton-bulk-" + kind + "-include-" + key);
    }

    private DialogPane dialog() {
        return Window.getWindows().stream()
                .filter(Window::isShowing)
                .map(w -> w.getScene().getRoot())
                .filter(DialogPane.class::isInstance)
                .map(DialogPane.class::cast)
                .findFirst()
                .orElse(null);
    }

    private void answer(ButtonType type) {
        DialogPane pane = dialog();
        assertNotNull(pane);
        ((Button) pane.lookupButton(type)).fire();
    }

    private <T> T fx(Callable<T> action) throws Exception {
        var f = new CompletableFuture<T>();
        Platform.runLater(
                () -> {
                    try {
                        f.complete(action.call());
                    } catch (Throwable e) {
                        f.completeExceptionally(e);
                    }
                });
        return f.get(45, TimeUnit.SECONDS);
    }

    private void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
        while (System.nanoTime() < deadline) {
            if (fx(condition)) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Timeout UI bulk; screenshot state may be inspected in target");
    }

    private void assertNoOverlaps(Set<Node> cards) {
        assertFalse(cards.isEmpty());
        var bounds =
                cards.stream().map(card -> card.localToScene(card.getBoundsInLocal())).toList();
        for (int i = 0; i < bounds.size(); i++)
            for (int j = i + 1; j < bounds.size(); j++) {
                var a = bounds.get(i);
                var b = bounds.get(j);
                boolean overlap =
                        a.getMaxX() > b.getMinX() + 2
                                && b.getMaxX() > a.getMinX() + 2
                                && a.getMaxY() > b.getMinY() + 2
                                && b.getMaxY() > a.getMinY() + 2;
                assertFalse(
                        overlap,
                        "Cards must reflow after restoring the window without a second resize");
            }
    }

    private void assertWideFits() {
        layout();
        BulkCatalogView page = (BulkCatalogView) node("ton-bulk-view");
        assertTrue(page.isFitToHeight());
        assertTrue(
                page.getContent().getLayoutBounds().getHeight()
                        <= page.getViewportBounds().getHeight() + 1);
        var frame = page.localToScene(page.getBoundsInLocal());
        var save =
                workspace().saveButton().localToScene(workspace().saveButton().getBoundsInLocal());
        var notes =
                node("ton-bulk-articles-notes")
                        .localToScene(node("ton-bulk-articles-notes").getBoundsInLocal());
        assertTrue(
                save.getMaxY() <= frame.getMaxY(),
                "Create must remain visible without scrolling the page");
        assertTrue(
                notes.getMaxY() <= frame.getMaxY(),
                "Common fields must remain visible without scrolling the page");
        assertEquals(
                "TON › Dati tecnici › Articoli › Inserimento multiplo",
                textLabel("ton-breadcrumb"));
        var back = page.back();
        var refresh = page.refresh();
        assertEquals(
                refresh.localToScene(refresh.getBoundsInLocal()).getCenterY(),
                back.localToScene(back.getBoundsInLocal()).getCenterY(),
                1);
    }

    private void layout() {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    private void snapshot(String path) throws Exception {
        layout();
        write(stage.getScene().snapshot(null), path);
    }

    private void snapshotDialog(String path) throws Exception {
        DialogPane pane = dialog();
        pane.applyCss();
        pane.layout();
        write(pane.snapshot(null, null), path);
    }

    private void write(javafx.scene.image.WritableImage image, String path) throws Exception {
        var out =
                new java.awt.image.BufferedImage(
                        (int) image.getWidth(),
                        (int) image.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++)
            for (int x = 0; x < out.getWidth(); x++)
                out.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        javax.imageio.ImageIO.write(out, "png", Path.of(path).toFile());
    }
}
