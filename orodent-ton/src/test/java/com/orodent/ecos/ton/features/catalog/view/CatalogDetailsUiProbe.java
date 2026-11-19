package com.orodent.ecos.ton.features.catalog.view;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;
import com.orodent.ecos.ton.TonModule;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;
import com.orodent.ecos.ton.features.catalog.service.CatalogService;
import com.orodent.ecos.ton.features.powders.model.PowderModel;
import com.orodent.ecos.ton.features.powders.service.PowderService;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Exercises the real module and an in-memory database, independently of other FX lifecycles. */
public final class CatalogDetailsUiProbe {
    private Stage stage;
    private ModuleSession session;
    private final List<Throwable> errors = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        try {
            new CatalogDetailsUiProbe().run();
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    private void run() throws Exception {
        String url =
                "jdbc:derby:memory:catalog_details_"
                        + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        ExecutorService workers = Executors.newFixedThreadPool(3);
        var catalogues = new CatalogService(db, workers);
        Line line =
                (Line)
                        catalogues
                                .create(
                                        new LineDraft(
                                                "ZRMST", "Thor", "Multistrato", "ZR", "", true))
                                .join()
                                .entry();
        for (int i = 0; i < 55; i++)
            catalogues
                    .create(ProductDraft.configured(line.id(), String.format("A%02d", i), "", true))
                    .join();
        var products =
                catalogues
                        .catalogue(
                                CatalogKind.PRODUCTS,
                                new Filter("", true, line.id(), null, null, null))
                        .join();
        var lastProduct = products.rows().getLast();
        var compositions =
                new com.orodent.ecos.ton.features.compositions.service.CompositionService(
                        db, workers);
        var models =
                new com.orodent.ecos.ton.features.diskmodels.service.DiskModelService(db, workers);
        for (var product : products.rows())
            compositions
                    .create(
                            new com.orodent.ecos.ton.features.compositions.model.Compositions.Draft(
                                    product.id(), "", List.of()),
                            false)
                    .join();
        for (int i = 0; i < 55; i++)
            models.create(
                            new com.orodent.ecos.ton.features.diskmodels.model.DiskModels.Draft(
                                    String.format("MODEL-%02d", i),
                                    new BigDecimal("98"),
                                    new BigDecimal("100"),
                                    BigDecimal.ONE,
                                    BigDecimal.ZERO,
                                    BigDecimal.ZERO,
                                    "",
                                    List.of(new BigDecimal("100")),
                                    List.of()),
                            i == 54)
                    .join();

        catalogues
                .create(
                        new ArticleDraft(
                                "ZR9812MSTA54",
                                "Thor A54 98 × 12",
                                lastProduct.id(),
                                new BigDecimal("98"),
                                new BigDecimal("12"),
                                "",
                                true))
                .join();
        for (int i = 0; i < 54; i++) {
            catalogues
                    .create(
                            new ArticleDraft(
                                    String.format("ZZ-ART%02d", i),
                                    "Altro formato " + i,
                                    lastProduct.id(),
                                    new BigDecimal("98"),
                                    BigDecimal.valueOf(20 + i),
                                    "",
                                    true))
                    .join();
        }
        var powders = new PowderService(db, workers);
        var target =
                powders.create(
                                new PowderModel.IdentityDraft(
                                        "Z-TARGET",
                                        "Polvere target",
                                        PowderModel.PowderColor.BIANCA,
                                        true,
                                        "Tosoh"),
                                new PowderModel.TechnicalDraft(
                                        null,
                                        null,
                                        3,
                                        "",
                                        List.of(
                                                new PowderModel.Oxide(
                                                        "Y2O3", new BigDecimal("5.150")))),
                                true)
                        .join();
        db.withTransaction(
                c -> {
                    try (var insert =
                            c.prepareStatement(
                                    "INSERT INTO TON.POLVERI(codice,nome,colore,fornitore) VALUES"
                                            + " (?,?,'BIANCA','Tosoh')")) {
                        for (int i = 0; i < 54; i++) {
                            insert.setString(1, String.format("P%03d", i));
                            insert.setString(2, "Polvere " + i);
                            insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                    return null;
                });
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
                                                        () -> {}));
                        stage = new Stage();
                        stage.setScene(new Scene(session.root(), 1600, 1000));
                        stage.getScene()
                                .getStylesheets()
                                .add(
                                        Path.of("../orodent-app/src/main/resources/css/global.css")
                                                .toUri()
                                                .toString());
                        stage.show();
                        button("ton-powders-button").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-powders-table") instanceof TableView<?> table
                                    && table.getItems().size() == 55);
            fx(
                    () -> {
                        assertNull(node("ton-powders-previous"));
                        assertNull(node("ton-powders-next"));
                        assertNull(node("ton-powder-open"));
                        assertEquals("Aggiorna", button("ton-powders-refresh").getText());
                        TextField search = (TextField) node("ton-powders-search");
                        search.setText("P00");
                        search.getOnAction().handle(new javafx.event.ActionEvent());
                        return null;
                    });
            await(() -> ((TableView<?>) node("ton-powders-table")).getItems().size() == 10);
            fx(
                    () -> {
                        TextField search = (TextField) node("ton-powders-search");
                        search.clear();
                        search.getOnAction().handle(new javafx.event.ActionEvent());
                        return null;
                    });
            await(() -> ((TableView<?>) node("ton-powders-table")).getItems().size() == 55);
            fx(
                    () -> {
                        assertNoCounters();
                        screenshot("target/powders-single-list.png");
                        TableView<?> table = (TableView<?>) node("ton-powders-table");
                        table.getSelectionModel().selectLast();
                        table.fireEvent(doubleClick());
                        return null;
                    });
            await(() -> node("ton-powder-editor") != null);
            fx(
                    () -> {
                        assertTrue(
                                ((Label) node("ton-breadcrumb"))
                                        .getText()
                                        .endsWith("Polveri › Z-TARGET"));
                        assertEquals(
                                target.powder().code(),
                                ((TextField) node("ton-powder-code")).getText());
                        ((TabPane) node("ton-powder-tabs")).getSelectionModel().select(2);
                        layout();
                        assertTrue(((TextField) node("ton-oxide-percent-0")).isDisabled());
                        screenshot("target/powder-shared-palette.png");
                        button("ton-overview-button").fire();
                        button("ton-lines-button").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-lines-table") instanceof TableView<?> table
                                    && !table.getItems().isEmpty());
            fx(
                    () -> {
                        TableView<?> table = (TableView<?>) node("ton-lines-table");
                        table.getSelectionModel().selectFirst();
                        table.fireEvent(doubleClick());
                        return null;
                    });
            await(() -> node("ton-line-editor") != null);
            fx(
                    () -> {
                        assertTrue(
                                ((Label) node("ton-breadcrumb"))
                                        .getText()
                                        .endsWith("Linee › ZRMST · Thor"));
                        assertNull(node("ton-line-heading"));
                        assertEquals("← Torna alle linee", button("ton-line-back").getText());
                        assertEquals("Aggiorna dati", button("ton-line-reload").getText());
                        assertEquals(
                                "Multistrato", ((TextField) node("ton-line-technology")).getText());
                        assertRefreshOnRight("line");
                        screenshot("target/line-shared-palette.png");
                        ((TabPane) node("ton-line-tabs")).getSelectionModel().select(1);
                        return null;
                    });
            await(
                    () ->
                            node("ton-line-children-table") instanceof TableView<?> table
                                    && table.getItems().size() == 55);
            fx(
                    () -> {
                        assertNull(node("ton-line-children-open"));
                        assertNull(node("ton-line-children-next"));
                        assertNull(node("ton-line-children-previous"));
                        assertTrue(
                                stage.getScene().getRoot().lookupAll(".label").stream()
                                        .noneMatch(
                                                n ->
                                                        n instanceof Label l
                                                                && l.getText()
                                                                        .startsWith(
                                                                                "Gli elementi"
                                                                                    + " vengono"
                                                                                    + " creati")));
                        assertEvenColumns((TableView<?>) node("ton-line-children-table"));
                        screenshot("target/line-products-single-list.png");
                        TableView<?> table = (TableView<?>) node("ton-line-children-table");
                        table.scrollTo(54);
                        layout();
                        TableRow<?> row =
                                table.lookupAll(".table-row-cell").stream()
                                        .filter(
                                                n ->
                                                        n instanceof TableRow<?> r
                                                                && r.getIndex() == 54
                                                                && !r.isEmpty())
                                        .map(n -> (TableRow<?>) n)
                                        .findFirst()
                                        .orElseThrow();
                        row.fireEvent(doubleClick());
                        return null;
                    });
            await(() -> node("ton-product-editor") != null);
            fx(
                    () -> {
                        assertEquals("ZRMSTA54", ((TextField) node("ton-product-code")).getText());
                        assertTrue(
                                ((Label) node("ton-breadcrumb"))
                                        .getText()
                                        .endsWith("Prodotti tecnici › ZRMSTA54 · Thor A54"));
                        assertNull(node("ton-product-heading"));
                        assertRefreshOnRight("product");
                        assertParentBesideStatus("product");
                        screenshot("target/product-shared-palette.png");
                        ((TabPane) node("ton-product-tabs")).getSelectionModel().select(1);
                        return null;
                    });
            await(
                    () ->
                            node("ton-product-children-table") instanceof TableView<?> table
                                    && table.getItems().size() == 55);
            fx(
                    () -> {
                        assertNull(node("ton-product-children-open"));
                        assertNull(node("ton-product-children-next"));
                        assertNull(node("ton-product-children-previous"));
                        assertNoCounters();
                        assertTrue(
                                stage.getScene().getRoot().lookupAll(".label").stream()
                                        .noneMatch(
                                                n ->
                                                        n instanceof Label l
                                                                && l.getText()
                                                                        .startsWith(
                                                                                "Gli elementi"
                                                                                    + " vengono"
                                                                                    + " creati")));
                        screenshot("target/product-articles-single-list.png");
                        TableView<?> table = (TableView<?>) node("ton-product-children-table");
                        layout();
                        assertEvenColumns(table);
                        TableRow<?> row =
                                table.lookupAll(".table-row-cell").stream()
                                        .filter(n -> n instanceof TableRow<?> r && !r.isEmpty())
                                        .map(n -> (TableRow<?>) n)
                                        .findFirst()
                                        .orElseThrow();
                        row.fireEvent(doubleClick());
                        return null;
                    });
            await(() -> node("ton-article-editor") != null);
            fx(
                    () -> {
                        assertTrue(
                                ((Label) node("ton-breadcrumb"))
                                        .getText()
                                        .contains("Articoli › ZR9812MSTA54"));
                        assertNull(node("ton-article-heading"));
                        assertRefreshOnRight("article");
                        assertParentBesideStatus("article");
                        screenshot("target/article-shared-palette.png");
                        button("ton-article-open-parent").fire();
                        return null;
                    });
            await(() -> node("ton-product-editor") != null);
            fx(
                    () -> {
                        button("ton-product-open-parent").fire();
                        return null;
                    });
            await(() -> node("ton-line-editor") != null);
            fx(
                    () -> {
                        button("ton-line-back").fire();
                        return null;
                    });
            await(() -> node("ton-lines-table") != null);
            fx(
                    () -> {
                        assertNoCounters();
                        assertNoFooter("lines");
                        screenshot("target/lines-list-palette.png");
                        button("ton-nav-technical").fire();
                        button("ton-tech-products-button").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-products-table") instanceof TableView<?> t
                                    && t.getItems().size() == 55);
            fx(
                    () -> {
                        assertNoCounters();
                        screenshot("target/products-list-palette.png");
                        assertNoFooter("products");
                        return null;
                    });
            fx(
                    () -> {
                        button("ton-nav-technical").fire();
                        button("ton-tech-articles-button").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-articles-table") instanceof TableView<?> t
                                    && t.getItems().size() == 55);
            fx(
                    () -> {
                        assertNoCounters();
                        screenshot("target/articles-list-palette.png");
                        assertNoFooter("articles");
                        return null;
                    });
            fx(
                    () -> {
                        button("ton-nav-technical").fire();
                        button("ton-tech-lines-button").fire();
                        return null;
                    });
            await(() -> node("ton-lines-table") != null);
            verifyTechnicalLists();
            fx(
                    () -> {
                        button("ton-nav-technical").fire();
                        button("ton-tech-lines-button").fire();
                        return null;
                    });
            await(() -> node("ton-lines-table") != null);
            fx(
                    () -> {
                        button("ton-line-new").fire();
                        return null;
                    });
            await(() -> node("ton-line-editor") != null);
            fx(
                    () -> {
                        assertTrue(
                                ((Label) node("ton-breadcrumb"))
                                        .getText()
                                        .endsWith("Linee › Nuova linea"));
                        stage.setWidth(1000);
                        stage.setHeight(720);
                        return null;
                    });
            Thread.sleep(200);
            fx(
                    () -> {
                        screenshot("target/line-shared-palette-compact.png");
                        return null;
                    });
            assertTrue(errors.isEmpty(), () -> errors.toString());
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
            } catch (SQLException expected) {
            }
        }
    }

    private void verifyTechnicalLists() throws Exception {
        fx(
                () -> {
                    button("ton-nav-technical").fire();
                    button("ton-tech-compositions-button").fire();
                    return null;
                });
        await(
                () ->
                        node("ton-compositions-table") instanceof TableView<?> table
                                && table.getItems().size() == 55);
        fx(
                () -> {
                    assertNoCounters();
                    assertEvenColumns((TableView<?>) node("ton-compositions-table"));
                    screenshot("target/compositions-list-palette.png");
                    var view =
                            (com.orodent.ecos.ton.features.compositions.view.CompositionsView)
                                    node("ton-compositions-view");
                    view.list().results().error("Errore di caricamento simulato");
                    assertTrue(node("ton-compositions-status").isVisible());
                    assertEquals(
                            "Errore di caricamento simulato",
                            ((Label) node("ton-compositions-status")).getText());
                    button("ton-compositions-refresh").fire();
                    return null;
                });
        await(() -> ((Label) node("ton-compositions-status")).getText().isEmpty());
        fx(
                () -> {
                    assertNoFooter("compositions");
                    return null;
                });
        fx(
                () -> {
                    TextField search = (TextField) node("ton-composition-search");
                    search.setText("ZRMSTA54");
                    search.getOnAction().handle(new javafx.event.ActionEvent());
                    return null;
                });
        await(() -> ((TableView<?>) node("ton-compositions-table")).getItems().size() == 1);
        fx(
                () -> {
                    var table = (TableView<?>) node("ton-compositions-table");
                    table.getSelectionModel().selectFirst();
                    table.fireEvent(doubleClick());
                    return null;
                });
        await(() -> node("ton-composition-editor") != null);
        fx(
                () -> {
                    button("ton-composition-back").fire();
                    return null;
                });
        await(
                () ->
                        node("ton-compositions-table") instanceof TableView<?> table
                                && table.getItems().size() == 1);
        fx(
                () -> {
                    button("ton-nav-technical").fire();
                    button("ton-tech-models-button").fire();
                    return null;
                });
        await(
                () ->
                        node("ton-models-table") instanceof TableView<?> table
                                && table.getItems().size() == 55);
        fx(
                () -> {
                    assertNoCounters();
                    assertEvenColumns((TableView<?>) node("ton-models-table"));
                    screenshot("target/disk-models-list-palette.png");
                    assertNoFooter("models");
                    return null;
                });
        fx(
                () -> {
                    ((ComboBox<?>) node("ton-model-filter")).getSelectionModel().select(2);
                    return null;
                });
        await(() -> ((TableView<?>) node("ton-models-table")).getItems().size() == 1);
        fx(
                () -> {
                    ((ComboBox<?>) node("ton-model-filter")).getSelectionModel().selectFirst();
                    return null;
                });
        await(() -> ((TableView<?>) node("ton-models-table")).getItems().size() == 55);
        fx(
                () -> {
                    TextField search = (TextField) node("ton-model-search");
                    search.setText("MODEL-54");
                    search.getOnAction().handle(new javafx.event.ActionEvent());
                    return null;
                });
        await(() -> ((TableView<?>) node("ton-models-table")).getItems().size() == 1);
        fx(
                () -> {
                    var table = (TableView<?>) node("ton-models-table");
                    table.getSelectionModel().selectFirst();
                    table.fireEvent(doubleClick());
                    return null;
                });
        await(() -> node("ton-model-editor") != null);
        fx(
                () -> {
                    assertTrue(
                            ((Label) node("ton-breadcrumb"))
                                    .getText()
                                    .endsWith("Modelli disco › MODEL-54"));
                    assertNull(node("ton-model-heading"));
                    assertNull(node("ton-model-version"));
                    assertEquals("← Torna ai modelli", button("ton-model-back").getText());
                    assertRefreshOnRight("model");
                    assertTrue(node("ton-model-diameter").isDisabled());
                    screenshot("target/disk-model-detail-approved.png");
                    button("ton-model-copy").fire();
                    return null;
                });
        await(
                () ->
                        node("ton-model-editor") != null
                                && ((Label) node("ton-model-status")).getText().startsWith("v2"));
        fx(
                () -> {
                    assertFalse(node("ton-model-diameter").isDisabled());
                    screenshot("target/disk-model-detail-draft.png");
                    ((TabPane) node("ton-model-tabs")).getSelectionModel().select(4);
                    return null;
                });
        await(
                () ->
                        node("ton-model-versions-table") instanceof TableView<?> t
                                && t.getItems().size() == 2);
        fx(
                () -> {
                    var table = (TableView<?>) node("ton-model-versions-table");
                    table.getSelectionModel().selectLast();
                    table.fireEvent(doubleClick());
                    return null;
                });
        await(() -> node("ton-model-diameter") != null && node("ton-model-diameter").isDisabled());
        fx(
                () -> {
                    assertTrue(((Label) node("ton-model-status")).getText().startsWith("v1"));
                    button("ton-model-back").fire();
                    return null;
                });
        await(
                () ->
                        node("ton-models-table") instanceof TableView<?> table
                                && table.getItems().size() == 1);
        fx(
                () -> {
                    stage.setWidth(1000);
                    stage.setHeight(720);
                    return null;
                });
        Thread.sleep(150);
        fx(
                () -> {
                    screenshot("target/disk-models-list-compact.png");
                    stage.setWidth(1600);
                    stage.setHeight(1000);
                    return null;
                });
    }

    private void assertNoFooter(String plural) {
        assertNull(node("ton-" + plural + "-next"));
        assertNull(node("ton-" + plural + "-previous"));
        String singular =
                switch (plural) {
                    case "lines" -> "line";
                    case "products" -> "product";
                    case "articles" -> "article";
                    default -> null;
                };
        assertNull(
                node(
                        "ton-"
                                + (singular == null ? plural : singular)
                                + (singular == null ? "-open" : "-edit")));
    }

    private void assertNoCounters() {
        assertTrue(
                stage.getScene().getRoot().lookupAll(".label").stream()
                        .noneMatch(
                                n ->
                                        n instanceof Label label
                                                && (label.getText()
                                                                .matches(
                                                                        "[0-9]+[–-][0-9]+ di"
                                                                                + " [0-9]+")
                                                        || label.getText()
                                                                .equals("Nessun collegamento"))));
    }

    private void assertRefreshOnRight(String kind) {
        layout();
        var button = node("ton-" + kind + "-reload");
        var editor = node("ton-" + kind + "-editor");
        assertTrue(
                button.localToScene(button.getBoundsInLocal()).getMaxX()
                        > editor.localToScene(editor.getBoundsInLocal()).getMaxX() - 40);
    }

    private void assertParentBesideStatus(String kind) {
        var button = node("ton-" + kind + "-open-parent");
        var status = node("ton-" + kind + "-status");
        assertSame(status.getParent(), button.getParent());
        assertFalse(button.isDisabled());
        assertTrue(button.getBoundsInParent().getMinX() > status.getBoundsInParent().getMaxX());
    }

    private void assertEvenColumns(TableView<?> table) {
        double min =
                table.getColumns().stream().mapToDouble(TableColumn::getWidth).min().orElseThrow();
        double max =
                table.getColumns().stream().mapToDouble(TableColumn::getWidth).max().orElseThrow();
        double sum = table.getColumns().stream().mapToDouble(TableColumn::getWidth).sum();
        assertTrue(max - min <= 2, "Columns should share the width evenly: " + min + ".." + max);
        assertTrue(
                sum >= table.getWidth() - 25 && sum <= table.getWidth() + 2,
                "Columns must fill the table: " + sum + " / " + table.getWidth());
    }

    private MouseEvent doubleClick() {
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

    private Node node(String id) {
        return stage.getScene().getRoot().lookup("#" + id);
    }

    private Button button(String id) {
        return (Button) Objects.requireNonNull(node(id), id);
    }

    private void layout() {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    private void screenshot(String path) throws Exception {
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

    private <T> T fx(Callable<T> task) throws Exception {
        var future =
                new FutureTask<T>(
                        () -> {
                            if (stage != null && stage.getScene() != null) layout();
                            return task.call();
                        });
        Platform.runLater(future);
        return future.get(20, TimeUnit.SECONDS);
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (fx(condition::getAsBoolean)) return;
            Thread.sleep(40);
        }
        throw new AssertionError("UI operation timed out");
    }
}
