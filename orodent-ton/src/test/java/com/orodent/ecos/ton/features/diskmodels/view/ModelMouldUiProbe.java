package com.orodent.ecos.ton.features.diskmodels.view;

import static com.orodent.ecos.ton.features.diskmodels.model.DiskModels.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;
import com.orodent.ecos.ton.TonModule;
import com.orodent.ecos.ton.features.diskmodels.service.DiskModelService;
import com.orodent.ecos.ton.features.equipment.model.Equipment;
import com.orodent.ecos.ton.features.equipment.service.EquipmentService;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public final class ModelMouldUiProbe {
    Stage stage;
    ModuleSession session;
    final List<Throwable> errors = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        try {
            new ModelMouldUiProbe().run();
            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    void run() throws Exception {
        String url =
                "jdbc:derby:memory:model_mould_ui_" + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "INSERT INTO"
                                    + " TON.MODELLI_DISCO(codice,numero_versione,diametro_pressatura_mm,pressione_kg_cm2,grammi_per_mm,sovramateriale_superiore_mm,sovramateriale_inferiore_mm,stato,approvato_il)"
                                    + " VALUES"
                                    + " ('LEGACY',1,99.25,1500,25,1.5,0.5,'APPROVATA',CURRENT_TIMESTAMP)");
                        q.executeUpdate("INSERT INTO TON.MODELLO_STRATI VALUES (1,1,100)");
                    }
                    return null;
                });
        var workers = Executors.newFixedThreadPool(3);
        var equipment = new EquipmentService(db, workers);
        var models = new DiskModelService(db, workers);
        for (String[] value : new String[][] {{"S99", "99"}, {"S992", "99.2"}, {"S988", "98.8"}})
            equipment
                    .save(
                            Equipment.Kind.MOULDS,
                            null,
                            new Equipment.Draft(
                                    value[0], value[0], new BigDecimal(value[1]), "", true))
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
                        button("ton-models-button").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-models-table") instanceof TableView<?> t
                                    && t.getItems().size() == 1);
            fx(
                    () -> {
                        button("ton-model-new").fire();
                        return null;
                    });
            await(() -> reference() != null && reference().getItems().size() == 3);
            fx(
                    () -> {
                        assertNull(node("ton-model-diameter"));
                        assertNull(reference().getValue());
                        field("ton-model-code").setText("MODEL-STAMPI");
                        field("ton-model-article-diameter").setText("98");
                        field("ton-model-pressure").setText("1500");
                        field("ton-model-grams").setText("25");
                        field("ton-model-top").setText("1,5");
                        field("ton-model-bottom").setText("0,5");
                        reference()
                                .setValue(
                                        reference().getItems().stream()
                                                .filter(m -> m.code().equals("S99"))
                                                .findFirst()
                                                .orElseThrow());
                        assertAligned(
                                "ton-model-article-diameter",
                                "ton-model-reference-mould",
                                "ton-model-grams",
                                "ton-model-remove-diameter");
                        assertEquals("×", button("ton-model-remove-diameter").getText());
                        assertNotNull(node("ton-model-diameter-headings"));
                        sheet("Sovramateriali");
                        for (int row = 1; row <= 2; row++) {
                            button("ton-model-range-add").fire();
                            field("ton-model-range-" + row + "-from")
                                    .setText(row == 1 ? "10" : "15");
                            field("ton-model-range-" + row + "-to").setText(row == 1 ? "15" : "30");
                            field("ton-model-range-" + row + "-top").setText("1,5");
                            field("ton-model-range-" + row + "-bottom").setText("0,5");
                        }
                        button("ton-model-range-add").fire();
                        button("ton-model-range-remove-3").fire();
                        assertNotNull(node("ton-model-range-headings"));
                        assertEquals(
                                4, node("ton-model-range-headings").lookupAll(".label").size());
                        for (int row = 1; row <= 2; row++) {
                            assertAligned(
                                    "ton-model-range-" + row + "-from",
                                    "ton-model-range-" + row + "-to",
                                    "ton-model-range-" + row + "-top",
                                    "ton-model-range-" + row + "-bottom",
                                    "ton-model-range-remove-" + row);
                            assertTrue(
                                    node("ton-model-range-row-" + row)
                                            .getStyleClass()
                                            .contains("ton-model-input-row"));
                        }
                        screenshot("target/model-ranges-compact.png");
                        sheet("Parametri");
                        screenshot("target/model-mould-parameters.png");
                        sheet("Stampi ammissibili");
                        layout();
                        assertEquals(3, table().getItems().size());
                        assertTrue(node("ton-model-diameter-group") instanceof TabPane);
                        assertTrue(
                                table().getHeight() < 180,
                                "Table must size to its three real moulds");
                        assertNoEmptyScrolling();
                        assertTrue(
                                stage.getScene().getRoot().lookupAll(".label").stream()
                                        .filter(n -> n instanceof Label)
                                        .map(n -> ((Label) n).getText())
                                        .noneMatch(
                                                t ->
                                                        t.startsWith("Riferimento:")
                                                                || t.startsWith(
                                                                        "Stime geometriche")));
                        assertTrue(check("S99").isSelected());
                        assertTrue(check("S99").isDisabled());
                        assertFalse(check("S992").isSelected());
                        assertFalse(check("S988").isSelected());
                        assertEquals("+0,4044", cell("S992", 4));
                        assertEquals("+0,10111", cell("S992", 5));
                        assertEquals("+0,4044", cell("S992", 6));
                        check("S992").fire();
                        layout();
                        assertTrue(check("S992").isSelected());
                        assertFalse(check("S988").isSelected());
                        assertEquals("25", field("ton-model-grams").getText());
                        button("ton-model-save").fire();
                        return null;
                    });
            await(() -> text("ton-model-feedback").contains("Bozza salvata"));
            var saved = models.catalogue("MODEL-STAMPI", Filter.ALL).join().rows().getFirst();
            var draft = models.load(saved.id()).join().revision();
            assertEquals(
                    List.of("S992"),
                    draft.data().moulds().admitted().stream().map(Mould::code).toList());
            fx(
                    () -> {
                        sheet("Stampi ammissibili");
                        screenshot("target/model-mould-comparison.png");
                        Platform.runLater(this::answer);
                        button("ton-model-approve").fire();
                        return null;
                    });
            await(() -> text("ton-model-status").contains("Approvata"));
            fx(
                    () -> {
                        assertTrue(reference().isDisabled());
                        sheet("Stampi ammissibili");
                        layout();
                        assertEquals(2, table().getItems().size());
                        assertTrue(check("S992").isDisabled());
                        return null;
                    });
            var old = equipment.list(Equipment.Kind.MOULDS, "S992", false).join().getFirst();
            equipment
                    .save(
                            Equipment.Kind.MOULDS,
                            old,
                            new Equipment.Draft(
                                    old.code(), old.name(), new BigDecimal("100"), "", true))
                    .join();
            fx(
                    () -> {
                        button("ton-model-reload").fire();
                        return null;
                    });
            await(
                    () ->
                            text("ton-model-status").contains("Approvata")
                                    && !button("ton-model-reload").isDisabled());
            fx(
                    () -> {
                        sheet("Stampi ammissibili");
                        assertEquals("+0,4044", cell("S992", 6));
                        button("ton-model-copy").fire();
                        return null;
                    });
            await(() -> text("ton-model-status").startsWith("v2"));
            await(() -> reference().getItems().size() == 3);
            fx(
                    () -> {
                        sheet("Stampi ammissibili");
                        layout();
                        assertEquals("Dati da aggiornare", cell("S992", 7));
                        check("S992").fire();
                        layout();
                        assertFalse(check("S992").isSelected());
                        assertEquals("+2,0304", cell("S992", 6));
                        check("S992").fire();
                        sheet("Parametri");
                        button("ton-model-add-diameter").fire();
                        field("ton-model-article-diameter-2").setText("95");
                        field("ton-model-grams-2").setText("20");
                        var ref95 = (ComboBox<Mould>) node("ton-model-reference-mould-2");
                        assertNull(ref95.getValue());
                        ref95.setValue(
                                ref95.getItems().stream()
                                        .filter(m -> m.code().equals("S988"))
                                        .findFirst()
                                        .orElseThrow());
                        button("ton-model-add-diameter").fire();
                        button("ton-model-remove-diameter-3").fire();
                        assertAligned(
                                "ton-model-article-diameter-2",
                                "ton-model-reference-mould-2",
                                "ton-model-grams-2",
                                "ton-model-remove-diameter-2");
                        assertEquals(
                                3, node("ton-model-diameter-headings").lookupAll(".label").size());
                        assertEquals(
                                0, node("ton-model-diameter-row-2").lookupAll(".label").size());
                        screenshot("target/model-diameters-parameters.png");
                        sheet("Stampi ammissibili");
                        var groups = (TabPane) node("ton-model-diameter-group");
                        assertEquals(2, groups.getTabs().size());
                        assertTrue(groups.getTabs().get(0).getText().contains("98"));
                        assertTrue(groups.getTabs().get(1).getText().contains("95"));
                        groups.getSelectionModel().select(1);
                        layout();
                        assertTrue(check("S988").isSelected());
                        assertFalse(check("S99").isSelected());
                        assertFalse(check("S992").isSelected());
                        check("S99").fire();
                        groups.getSelectionModel().select(0);
                        layout();
                        assertTrue(check("S99").isSelected());
                        assertTrue(check("S992").isSelected());
                        assertFalse(check("S988").isSelected());
                        assertNoEmptyScrolling();
                        screenshot("target/model-diameters-comparison.png");
                        return null;
                    });
            await(
                    () -> {
                        var tabs = (TabPane) node("ton-model-diameter-group");
                        var headers = tabs.lookup(".headers-region");
                        if (headers == null) return false;
                        var clip = headers.getClip();
                        double right =
                                tabs.lookupAll(".tab").stream()
                                        .mapToDouble(n -> n.getBoundsInParent().getMaxX())
                                        .max()
                                        .orElse(Double.MAX_VALUE);
                        return clip == null || clip.getBoundsInLocal().getMaxX() + 1 >= right;
                    });
            fx(
                    () -> {
                        assertNoEmptyScrolling();
                        screenshot("target/model-diameters-comparison.png");
                        return null;
                    });
            db.withTransaction(
                    c -> {
                        try (var q =
                                c.prepareStatement(
                                        "INSERT INTO TON.STAMPI(codice,nome,diametro_mm) VALUES"
                                                + " (?,?,99)")) {
                            for (int i = 0; i < 30; i++) {
                                q.setString(1, String.format("EXTRA-%02d", i));
                                q.setString(2, "Stampo aggiuntivo " + i);
                                q.addBatch();
                            }
                            q.executeBatch();
                        }
                        return null;
                    });
            fx(
                    () -> {
                        button("ton-model-moulds-refresh").fire();
                        return null;
                    });
            await(() -> table().getItems().size() == 33);
            fx(
                    () -> {
                        layout();
                        var scroll = activeSheetScroll();
                        assertTrue(
                                scroll.getContent().getLayoutBounds().getHeight()
                                        > scroll.getViewportBounds().getHeight(),
                                "Real mould rows should require scrolling when numerous");
                        screenshot("target/model-moulds-many.png");
                        Platform.runLater(this::answer);
                        button("ton-model-approve").fire();
                        return null;
                    });
            await(
                    () ->
                            text("ton-model-status").startsWith("v2")
                                    && text("ton-model-status").contains("Approvata"));
            var latest =
                    models.load(
                                    models.catalogue("MODEL-STAMPI", Filter.ALL)
                                            .join()
                                            .rows()
                                            .getFirst()
                                            .id())
                            .join()
                            .revision();
            assertEquals(2, latest.data().diameters().size());
            assertEquals(
                    0,
                    new BigDecimal("20").compareTo(latest.data().gramsFor(new BigDecimal("95"))));
            assertEquals(
                    0,
                    new BigDecimal("25").compareTo(latest.data().gramsFor(new BigDecimal("98"))));
            assertEquals(
                    List.of("S99"),
                    latest
                            .data()
                            .forArticleDiameter(new BigDecimal("95"))
                            .moulds()
                            .admitted()
                            .stream()
                            .map(Mould::code)
                            .toList());
            assertEquals(
                    List.of("S992"),
                    latest
                            .data()
                            .forArticleDiameter(new BigDecimal("98"))
                            .moulds()
                            .admitted()
                            .stream()
                            .map(Mould::code)
                            .toList());
            fx(
                    () -> {
                        sheet("Stampi ammissibili");
                        assertTrue(field("ton-model-article-diameter").isDisabled());
                        assertTrue(field("ton-model-grams-2").isDisabled());
                        stage.setWidth(1050);
                        stage.setHeight(780);
                        return null;
                    });
            fx(
                    () -> {
                        screenshot("target/model-mould-compact.png");
                        button("ton-model-back").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-models-table") instanceof TableView<?> t
                                    && t.getItems().size() == 2);
            fx(
                    () -> {
                        var list = (TableView<Summary>) node("ton-models-table");
                        list.getSelectionModel()
                                .select(
                                        list.getItems().stream()
                                                .filter(s -> s.code().equals("LEGACY"))
                                                .findFirst()
                                                .orElseThrow());
                        list.fireEvent(doubleClick());
                        return null;
                    });
            await(() -> text("ton-model-status").contains("Diametro storico"));
            fx(
                    () -> {
                        assertTrue(reference().isDisabled());
                        assertNull(reference().getValue());
                        assertTrue(reference().getPromptText().contains("99,25"));
                        sheet("Stampi ammissibili");
                        assertEquals(0, table().getItems().size());
                        return null;
                    });
            assertTrue(errors.isEmpty(), errors::toString);
        } finally {
            fx(
                    () -> {
                        if (session != null) session.close();
                        if (stage != null) stage.close();
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

    ScrollPane activeSheetScroll() {
        return (ScrollPane)
                ((TabPane) node("ton-model-tabs"))
                        .getSelectionModel()
                        .getSelectedItem()
                        .getContent();
    }

    void assertNoEmptyScrolling() {
        layout();
        var scroll = activeSheetScroll();
        assertTrue(
                scroll.getContent().getLayoutBounds().getHeight()
                        <= scroll.getViewportBounds().getHeight() + 1,
                "A few moulds must not force scrolling over blank space: "
                        + scroll.getContent().getLayoutBounds()
                        + " vs "
                        + scroll.getViewportBounds());
    }

    void assertAligned(String... ids) {
        layout();
        double y = node(ids[0]).localToScene(0, 0).getY();
        for (var id : ids) {
            var n = node(id);
            assertEquals(34, ((javafx.scene.layout.Region) n).getHeight(), 0.75, id);
            assertEquals(y, n.localToScene(0, 0).getY(), 0.75, id);
        }
    }

    void sheet(String name) {
        var tabs = (TabPane) node("ton-model-tabs");
        tabs.getSelectionModel()
                .select(
                        tabs.getTabs().stream()
                                .filter(t -> t.getText().equals(name))
                                .findFirst()
                                .orElseThrow());
        layout();
    }

    @SuppressWarnings("unchecked")
    ComboBox<Mould> reference() {
        return (ComboBox<Mould>) node("ton-model-reference-mould");
    }

    TableView<?> table() {
        return (TableView<?>) node("ton-model-moulds-table");
    }

    String cell(String code, int column) {
        for (int i = 0; i < table().getItems().size(); i++)
            if (table().getColumns()
                    .get(1)
                    .getCellObservableValue(i)
                    .getValue()
                    .toString()
                    .startsWith(code + " ·"))
                return Objects.toString(
                        table().getColumns().get(column).getCellObservableValue(i).getValue(), "");
        throw new AssertionError("Missing mould " + code);
    }

    CheckBox check(String code) {
        layout();
        return table().lookupAll(".check-box").stream()
                .filter(n -> ("Ammetti stampo " + code).equals(n.getAccessibleText()))
                .map(n -> (CheckBox) n)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing checkbox " + code));
    }

    void answer() {
        for (var window : List.copyOf(Window.getWindows()))
            if (window.isShowing() && window.getScene().getRoot() instanceof DialogPane pane) {
                var b = (Button) pane.lookupButton(ButtonType.YES);
                if (b != null) {
                    b.fire();
                    return;
                }
            }
        throw new AssertionError("Confirmation not found");
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
        return n instanceof Label l ? l.getText() : "";
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
        throw new AssertionError("UI timed out: " + fx(() -> text("ton-model-feedback")));
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
