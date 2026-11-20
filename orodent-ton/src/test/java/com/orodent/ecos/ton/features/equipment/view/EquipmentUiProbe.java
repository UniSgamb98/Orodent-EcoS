package com.orodent.ecos.ton.features.equipment.view;

import static com.orodent.ecos.ton.features.equipment.model.Equipment.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;
import com.orodent.ecos.ton.TonModule;
import com.orodent.ecos.ton.features.equipment.service.EquipmentService;
import com.orodent.ecos.ton.features.technical.view.TonListView;
import com.orodent.ecos.ton.navigation.*;

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

public final class EquipmentUiProbe {
    Stage stage;
    ModuleSession session;
    final List<Throwable> errors = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        try {
            new EquipmentUiProbe().run();
            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    void run() throws Exception {
        String url =
                "jdbc:derby:memory:equipment_ui_" + UUID.randomUUID().toString().replace("-", "");
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
                                "INSERT INTO TON.FORNI(codice,nome) VALUES ('F01','Forno"
                                    + " esistente')");
                        q.executeUpdate(
                                "INSERT INTO TON.CICLI_COTTURA(numero,forno_id,data_prevista)"
                                    + " VALUES ('C01',1,CURRENT_DATE)");
                    }
                    return null;
                });
        var workers = Executors.newFixedThreadPool(3);
        var service = new EquipmentService(db, workers);
        service.save(
                        Kind.PRESSES,
                        null,
                        new Draft("P200", "Pressa 200", new BigDecimal("200"), "", true))
                .join();
        service.save(
                        Kind.MOULDS,
                        null,
                        new Draft("S99", "Stampo 99", new BigDecimal("99"), "", true))
                .join();
        assertEquals(TonSection.EQUIPMENT, TonFeature.FURNACES.section());
        assertFalse(TonFeature.in(TonSection.FIRING).contains(TonFeature.FURNACES));
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
                        button("ton-nav-equipment").fire();
                        assertNotNull(node("ton-equipment-page"));
                        button("ton-tech-presses-button").fire();
                        return null;
                    });
            await(() -> rows("presses") == 1);
            fx(
                    () -> {
                        assertInstanceOf(TonListView.class, node("ton-presses-list"));
                        assertTrue(
                                text("ton-breadcrumb")
                                        .contains("Macchinari e attrezzature › Presse"));
                        assertEquals("Aggiorna", button("ton-presses-refresh").getText());
                        button("ton-presses-new").fire();
                        assertTrue(button("ton-presses-reload").isDisabled());
                        field("ton-presses-code").setText("P300");
                        field("ton-presses-name").setText("Pressa 300");
                        field("ton-presses-dimension").setText("250");
                        button("ton-presses-save").fire();
                        return null;
                    });
            await(() -> rows("presses") == 2);
            fx(
                    () -> {
                        open("presses", "P300");
                        return null;
                    });
            await(() -> visible("ton-presses-editor"));
            fx(
                    () -> {
                        assertEquals("250", field("ton-presses-dimension").getText());
                        field("ton-presses-dimension").setText("0");
                        button("ton-presses-save").fire();
                        assertTrue(text("ton-presses-feedback-editor").contains("positiva"));
                        field("ton-presses-dimension").setText("250,5");
                        button("ton-presses-save").fire();
                        return null;
                    });
            await(() -> rows("presses") == 2);
            fx(
                    () -> {
                        field("ton-presses-search").setText("P300");
                        field("ton-presses-search").fireEvent(new javafx.event.ActionEvent());
                        return null;
                    });
            await(() -> rows("presses") == 1);
            fx(
                    () -> {
                        open("presses", "P300");
                        return null;
                    });
            await(() -> visible("ton-presses-editor"));
            fx(
                    () -> {
                        assertEquals("250,5", field("ton-presses-dimension").getText());
                        ((CheckBox) node("ton-presses-active")).fire();
                        button("ton-presses-save").fire();
                        return null;
                    });
            await(() -> rows("presses") == 0);
            fx(
                    () -> {
                        ((CheckBox) node("ton-presses-archived")).fire();
                        return null;
                    });
            await(() -> rows("presses") == 1);
            fx(
                    () -> {
                        field("ton-presses-search").clear();
                        field("ton-presses-search").fireEvent(new javafx.event.ActionEvent());
                        return null;
                    });
            await(() -> rows("presses") == 2);
            fx(
                    () -> {
                        layout();
                        var table = table("presses");
                        double width = table.getColumns().getFirst().getWidth();
                        for (var column : table.getColumns())
                            assertEquals(width, column.getWidth(), 2);
                        screenshot("target/equipment-presses.png");
                        button("ton-presses-new").fire();
                        field("ton-presses-code").setText("DA-SCARTARE");
                        Platform.runLater(() -> answer(ButtonType.NO));
                        button("ton-nav-equipment").fire();
                        assertTrue(visible("ton-presses-editor"));
                        assertEquals("DA-SCARTARE", field("ton-presses-code").getText());
                        Platform.runLater(() -> answer(ButtonType.YES));
                        button("ton-nav-equipment").fire();
                        button("ton-tech-moulds-button").fire();
                        return null;
                    });
            await(() -> rows("moulds") == 1);
            fx(
                    () -> {
                        assertInstanceOf(TonListView.class, node("ton-moulds-list"));
                        button("ton-moulds-new").fire();
                        field("ton-moulds-code").setText("S100");
                        field("ton-moulds-name").setText("Stampo 100");
                        field("ton-moulds-dimension").setText("100");
                        button("ton-moulds-save").fire();
                        return null;
                    });
            await(() -> rows("moulds") == 2);
            fx(
                    () -> {
                        screenshot("target/equipment-moulds.png");
                        button("ton-nav-equipment").fire();
                        button("ton-tech-furnaces-button").fire();
                        return null;
                    });
            await(() -> rows("furnaces") == 1);
            fx(
                    () -> {
                        assertInstanceOf(TonListView.class, node("ton-furnaces-list"));
                        open("furnaces", "F01");
                        return null;
                    });
            await(() -> visible("ton-furnaces-editor"));
            fx(
                    () -> {
                        assertNull(node("ton-furnaces-dimension"));
                        field("ton-furnaces-name").setText("Forno esistente aggiornato");
                        button("ton-furnaces-save").fire();
                        return null;
                    });
            await(() -> rows("furnaces") == 1);
            fx(
                    () -> {
                        screenshot("target/equipment-furnaces.png");
                        stage.setWidth(1050);
                        stage.setHeight(780);
                        return null;
                    });
            fx(
                    () -> {
                        screenshot("target/equipment-compact.png");
                        return null;
                    });
            assertEquals(
                    "Forno esistente aggiornato", service.load(Kind.FURNACES, 1).join().name());
            db.withConnection(
                    c -> {
                        try (var q = c.createStatement();
                                var r =
                                        q.executeQuery(
                                                "SELECT forno_id FROM TON.CICLI_COTTURA WHERE"
                                                    + " numero='C01'")) {
                            assertTrue(r.next());
                            assertEquals(1, r.getLong(1));
                        }
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

    void answer(ButtonType type) {
        for (var window : List.copyOf(Window.getWindows()))
            if (window.isShowing() && window.getScene().getRoot() instanceof DialogPane pane) {
                var button = (Button) pane.lookupButton(type);
                if (button != null) {
                    button.fire();
                    return;
                }
            }
        throw new AssertionError("Confirmation not found");
    }

    int rows(String kind) {
        var n = node("ton-" + kind + "-table");
        return n instanceof TableView<?> t
                        && t.isVisible()
                        && visible("ton-" + kind + "-list")
                        && !t.isDisabled()
                ? t.getItems().size()
                : -1;
    }

    @SuppressWarnings("unchecked")
    TableView<Entry> table(String kind) {
        return (TableView<Entry>) node("ton-" + kind + "-table");
    }

    void open(String kind, String code) {
        var t = table(kind);
        t.getSelectionModel()
                .select(
                        t.getItems().stream()
                                .filter(e -> e.code().equals(code))
                                .findFirst()
                                .orElseThrow());
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

    Node node(String id) {
        return stage.getScene().getRoot().lookup("#" + id);
    }

    boolean visible(String id) {
        var n = node(id);
        return n != null && n.isVisible() && n.isManaged();
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
        throw new AssertionError("UI timed out");
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
