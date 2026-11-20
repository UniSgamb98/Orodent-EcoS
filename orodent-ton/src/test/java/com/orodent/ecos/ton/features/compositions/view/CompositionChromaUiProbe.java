package com.orodent.ecos.ton.features.compositions.view;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;
import com.orodent.ecos.ton.TonModule;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels;
import com.orodent.ecos.ton.features.catalog.service.CatalogService;
import com.orodent.ecos.ton.features.compositions.model.Compositions.*;
import com.orodent.ecos.ton.features.compositions.service.CompositionService;
import com.orodent.ecos.ton.features.powders.model.PowderModel;
import com.orodent.ecos.ton.features.powders.service.PowderService;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public final class CompositionChromaUiProbe {
    Stage stage;
    ModuleSession session;
    final List<Throwable> errors = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        try {
            new CompositionChromaUiProbe().run();
            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    void run() throws Exception {
        String url = "jdbc:derby:memory:chroma_ui_" + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        var workers = Executors.newFixedThreadPool(3);
        var catalog = new CatalogService(db, workers);
        var line =
                (CatalogModels.Line)
                        catalog.create(
                                        new CatalogModels.LineDraft(
                                                "ZRMST", "Thor", "Multistrato", "ZR", "", true))
                                .join()
                                .entry();
        var product =
                (CatalogModels.Product)
                        catalog.create(
                                        CatalogModels.ProductDraft.configured(
                                                line.id(), "A2", "", true))
                                .join()
                                .entry();
        var powders = new PowderService(db, workers);
        List<Long> refs = new ArrayList<>();
        for (var color :
                List.of(
                        PowderModel.PowderColor.BIANCA,
                        PowderModel.PowderColor.GRIGIA,
                        PowderModel.PowderColor.GIALLA,
                        PowderModel.PowderColor.ROSA)) {
            var powder =
                    powders.create(
                                    new PowderModel.IdentityDraft(
                                            "POLVERE-" + color,
                                            color.label(),
                                            color,
                                            true,
                                            "Tosoh"),
                                    new PowderModel.TechnicalDraft(
                                            null,
                                            null,
                                            3,
                                            "",
                                            List.of(
                                                    new PowderModel.Oxide(
                                                            "Y2O3", new BigDecimal("5")))),
                                    true)
                            .join();
            refs.add(powder.latest().id());
        }
        var service = new CompositionService(db, workers);
        var comp =
                service.create(
                                new Draft(
                                        product.id(),
                                        "",
                                        List.of(
                                                new Layer(
                                                        1,
                                                        "Dentina",
                                                        List.of(
                                                                new Ingredient(
                                                                        refs.get(0),
                                                                        new BigDecimal("50")),
                                                                new Ingredient(
                                                                        refs.get(1),
                                                                        new BigDecimal("1")),
                                                                new Ingredient(
                                                                        refs.get(2),
                                                                        new BigDecimal("29.4")),
                                                                new Ingredient(
                                                                        refs.get(3),
                                                                        new BigDecimal("19.6")))),
                                                new Layer(
                                                        2,
                                                        "Incisale",
                                                        List.of(
                                                                new Ingredient(
                                                                        refs.get(0),
                                                                        new BigDecimal("100")))))),
                                false)
                        .join();
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
                        stage.setScene(new Scene(session.root(), 1760, 1040));
                        stage.getScene()
                                .getStylesheets()
                                .add(
                                        Path.of("../orodent-app/src/main/resources/css/global.css")
                                                .toUri()
                                                .toString());
                        stage.show();
                        button("ton-compositions-button").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-compositions-table") instanceof TableView<?> t
                                    && t.getItems().size() == 1);
            fx(
                    () -> {
                        open();
                        return null;
                    });
            await(() -> node("ton-composition-editor") != null);
            fx(
                    () -> {
                        tabs().getSelectionModel().select(1);
                        layout();
                        assertNull(node("ton-composition-ppm-enabled"));
                        assertNull(node("ton-composition-powder-search"));
                        assertNull(node("ton-composition-ingredient-1-tint"));
                        assertNull(node("ton-composition-ingredient-4-tint"));
                        assertEquals("1000", field("ton-composition-ppm").getText());
                        assertEquals("29,4", text("ton-composition-ingredient-2-final"));
                        assertEquals("19,6", text("ton-composition-ingredient-3-final"));
                        List<String> colors = new ArrayList<>();
                        for (int n = 1; n <= 4; n++) {
                            var box =
                                    (ComboBox<?>)
                                            node("ton-composition-ingredient-" + n + "-powder");
                            colors.add(((PowderRef) box.getValue()).color());
                        }
                        assertEquals(List.of("BIANCA", "GIALLA", "ROSA", "GRIGIA"), colors);
                        field("ton-composition-ppm").setText("600");
                        assertEquals("50", field("ton-composition-ingredient-1-final").getText());
                        assertEquals("17,4", text("ton-composition-ingredient-2-final"));
                        field("ton-composition-ingredient-1-final").setText("70");
                        var yellow = field("ton-composition-ingredient-2-tint");
                        yellow.setText("70");
                        assertSame(yellow, field("ton-composition-ingredient-2-tint"));
                        assertEquals("20,3", text("ton-composition-ingredient-2-final"));
                        field("ton-composition-ingredient-3-tint").setText("30");
                        assertEquals("8,7", text("ton-composition-ingredient-3-final"));
                        assertTrue(text("ton-composition-layer-total").contains("100%"));
                        field("ton-composition-ingredient-2-final").setText("14,5");
                        assertEquals("50", yellow.getText());
                        assertTrue(text("ton-composition-layer-total").contains("Tinta 80%"));
                        assertTrue(
                                node("ton-composition-layer-total")
                                        .getStyleClass()
                                        .contains("ton-comp-invalid"));
                        assertEquals("8,7", text("ton-composition-ingredient-3-final"));
                        assertEquals("600", field("ton-composition-ppm").getText());
                        field("ton-composition-ingredient-3-final").setText("14,5");
                        assertEquals("50", text("ton-composition-ingredient-3-tint"));
                        assertTrue(text("ton-composition-layer-total").contains("Tinta 100%"));
                        assertFalse(
                                node("ton-composition-layer-total")
                                        .getStyleClass()
                                        .contains("ton-comp-invalid"));
                        assertTrue(text("ton-composition-layer-total").contains("100%"));
                        button("ton-composition-layer-select-2").fire();
                        assertEquals("0", field("ton-composition-ppm").getText());
                        button("ton-composition-layer-select-1").fire();
                        assertEquals("600", field("ton-composition-ppm").getText());
                        field("ton-composition-ppm").setText("2001");
                        assertTrue(text("ton-composition-layer-total").contains("2000"));
                        field("ton-composition-ppm").setText("600");
                        layout();
                        var powderBox = (ComboBox<?>) node("ton-composition-ingredient-2-powder");
                        assertEquals(
                                field("ton-composition-ingredient-2-final").getHeight(),
                                powderBox.getHeight(),
                                0.1);
                        assertEquals(
                                36, button("ton-composition-ingredient-2-remove").getWidth(), 0.1);
                        assertEquals(
                                36, button("ton-composition-ingredient-2-remove").getHeight(), 0.1);
                        assertEquals(
                                node("ton-composition-layer-heading").localToScene(0, 0).getY(),
                                node("ton-composition-layer-total").localToScene(0, 0).getY(),
                                15);
                        screenshot("target/composition-chroma-wide.png");
                        button("ton-composition-save").fire();
                        return null;
                    });
            await(() -> text("ton-composition-feedback").contains("salvata"));
            var saved = service.load(comp.revision().id()).join();
            assertEquals(
                    0,
                    new BigDecimal("70")
                            .compareTo(
                                    saved
                                            .revision()
                                            .data()
                                            .layers()
                                            .getFirst()
                                            .ingredients()
                                            .stream()
                                            .filter(i -> i.powderRevisionId() == refs.get(0))
                                            .findFirst()
                                            .orElseThrow()
                                            .mass()));
            fx(
                    () -> {
                        button("ton-composition-back").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-compositions-table") instanceof TableView<?> t
                                    && t.getItems().size() == 1);
            fx(
                    () -> {
                        open();
                        return null;
                    });
            await(() -> node("ton-composition-editor") != null);
            fx(
                    () -> {
                        tabs().getSelectionModel().select(1);
                        layout();
                        assertNull(node("ton-composition-ppm-enabled"));
                        assertEquals("600", field("ton-composition-ppm").getText());
                        assertEquals("50", field("ton-composition-ingredient-2-tint").getText());
                        assertEquals("14,5", field("ton-composition-ingredient-2-final").getText());
                        Platform.runLater(() -> answer());
                        button("ton-composition-approve").fire();
                        return null;
                    });
            await(() -> text("ton-composition-status").contains("Approvata"));
            fx(
                    () -> {
                        tabs().getSelectionModel().select(1);
                        layout();
                        assertTrue(field("ton-composition-ingredient-2-tint").isDisabled());
                        assertTrue(field("ton-composition-ingredient-2-final").isDisabled());
                        assertTrue(field("ton-composition-ppm").isDisabled());
                        screenshot("target/composition-chroma-approved.png");
                        stage.setWidth(1050);
                        stage.setHeight(780);
                        return null;
                    });
            Thread.sleep(200);
            fx(
                    () -> {
                        screenshot("target/composition-chroma-compact.png");
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

    void answer() {
        for (var window : List.copyOf(javafx.stage.Window.getWindows()))
            if (window.isShowing() && window.getScene().getRoot() instanceof DialogPane pane) {
                var b = (Button) pane.lookupButton(ButtonType.YES);
                if (b != null) {
                    b.fire();
                    return;
                }
            }
        throw new AssertionError("Confirmation not found");
    }

    void open() {
        var t = (TableView<?>) node("ton-compositions-table");
        t.getSelectionModel().selectFirst();
        t.fireEvent(
                new MouseEvent(
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
                        null));
    }

    TabPane tabs() {
        return (TabPane) node("ton-composition-tabs");
    }

    Node node(String id) {
        return stage.getScene().getRoot().lookup("#" + id);
    }

    Button button(String id) {
        return (Button) Objects.requireNonNull(node(id), id);
    }

    TextField field(String id) {
        return (TextField) Objects.requireNonNull(node(id), id);
    }

    String text(String id) {
        var n = node(id);
        return n instanceof Label l ? l.getText() : n instanceof TextField f ? f.getText() : "";
    }

    void layout() {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    <T> T fx(Callable<T> action) throws Exception {
        var f =
                new FutureTask<T>(
                        () -> {
                            if (stage != null) layout();
                            return action.call();
                        });
        Platform.runLater(f);
        return f.get(25, TimeUnit.SECONDS);
    }

    void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (System.nanoTime() < end) {
            if (fx(condition::getAsBoolean)) return;
            Thread.sleep(50);
        }
        throw new AssertionError("UI timed out: " + fx(() -> text("ton-composition-feedback")));
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
}
