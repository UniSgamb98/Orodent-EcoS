package com.orodent.ecos.costi;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.costi.model.PowderPrices.*;
import com.orodent.ecos.costi.service.PowderPriceService;
import com.orodent.ecos.costi.view.PowderPricesView;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Tag("javafx")
class PowderPricesUiTest {
    private Stage stage;
    private ModuleSession session;
    private final List<Throwable> asyncErrors = new CopyOnWriteArrayList<>();

    @Test
    void inlinePricesColorsAndLotsShareOneVersionedEditor() throws Exception {
        String oldLog = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/costi-ui.log");
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
        db.withTransaction(
                c -> {
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "INSERT INTO TON.POLVERI(codice,nome,colore,fornitore) VALUES"
                                    + " ('Z-PEX4M','Polvere bianca 4Y','BIANCA','Azienda Uno')");
                        q.executeUpdate(
                                "INSERT INTO TON.POLVERI(codice,nome,colore,fornitore) VALUES"
                                        + " ('PINK4M','Polvere rosa','ROSA','Azienda Due')");
                    }
                    return null;
                });
        var workers = Executors.newFixedThreadPool(2);
        var homes = new AtomicInteger();
        FxTestSupport.start();
        fx(
                () -> {
                    Thread.currentThread()
                            .setUncaughtExceptionHandler((t, e) -> asyncErrors.add(e));
                    return null;
                });
        try {
            fx(
                    () -> {
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
                        stage.setTitle("Orodent-EcoS · Costi e consumi");
                        stage.setScene(new Scene(session.root(), 1440, 900));
                        stage.getScene()
                                .getStylesheets()
                                .add(
                                        Path.of("../orodent-app/src/main/resources/css/global.css")
                                                .toUri()
                                                .toString());
                        stage.show();
                        return null;
                    });
            await(
                    () ->
                            table().getItems().size() == 2
                                    && label("costi-status").equals("Prezzo da compilare")
                                    && field("costi-inline-price-1") != null
                                    && enabled("costi-inline-price-1")
                                    && field("costi-inline-price-1").isVisible());
            fx(
                    () -> {
                        view().supplier
                                .setValue(
                                        new com.orodent.ecos.costi.view.PowderPricesView.Supplier(
                                                "Azienda Uno"));
                        return null;
                    });
            await(() -> table().getItems().size() == 1 && editable("costi-inline-price-1"));
            fx(
                    () -> {
                        assertEquals(
                                "Azienda Uno", table().getItems().getFirst().powder().supplier());
                        assertNull(stage.getScene().lookup("#costi-previous"));
                        view().supplier
                                .setValue(
                                        new com.orodent.ecos.costi.view.PowderPricesView.Supplier(
                                                null));
                        return null;
                    });
            await(() -> table().getItems().size() == 2 && editable("costi-inline-price-1"));
            fx(
                    () -> {
                        assertNull(stage.getScene().lookup("#costi-search"));
                        assertNull(stage.getScene().lookup("#costi-filter"));
                        assertNull(stage.getScene().lookup("#costi-mode-advanced"));
                        assertEquals(3, table().getColumns().size());
                        assertNull(stage.getScene().lookup("#costi-simple-price"));
                        assertNull(stage.getScene().lookup("#costi-simple-quantity"));
                        assertTrue(
                                ((CheckBox) stage.getScene().lookup("#costi-color-bianca"))
                                        .isSelected());
                        assertTrue(
                                ((CheckBox) stage.getScene().lookup("#costi-color-rosa"))
                                        .isSelected());
                        stage.getScene().getRoot().applyCss();
                        stage.getScene().getRoot().layout();
                        assertFalse(
                                stage.getScene().getRoot().lookupAll(".powder-white").isEmpty());
                        field("costi-inline-price-1").setText("92,1250");
                        assertEquals("92,1250", view().state().price());
                        button("costi-save").fire();
                        return null;
                    });
            await(() -> feedback().contains("Prezzi salvati"));
            var service = new PowderPriceService(db, workers);
            var first = service.load(1).join();
            assertEquals(1, first.version());
            assertNull(first.draft().quantity());
            fx(
                    () -> {
                        button("costi-lot-add").fire();
                        button("costi-lot-add").fire();
                        lot(0, "L-2026-041", "100", "80");
                        lot(1, "L-2026-041", "50", "100");
                        assertTrue(label("costi-total-quantity").contains("150"));
                        field("costi-inline-quantity-1").setText("200");
                        assertEquals("200", view().state().quantity());
                        assertEquals("200 kg", label("costi-available"));
                        assertTrue(label("costi-unassigned").contains("50"));
                        assertTrue(label("costi-average-price").contains("86,6667"));
                        assertEquals("92,125", field("costi-inline-price-1").getText());
                        assertEquals("100", field("costi-lot-0-quantity").getText());
                        button("costi-save").fire();
                        return null;
                    });
            await(() -> feedback().contains("revision") && feedback().contains("2"));
            var advanced = service.load(1).join();
            assertEquals(2, advanced.version());
            assertEquals(2, advanced.draft().lots().size());
            assertNotNull(advanced.draft().simplePrice());
            fx(
                    () -> {
                        DatePicker reference = (DatePicker) stage.getScene().lookup("#costi-date");
                        String savedDate = reference.getEditor().getText();
                        reference.getEditor().setText("31/02/2026");
                        button("costi-save").fire();
                        assertTrue(feedback().contains("Data"));
                        button("costi-cancel").fire();
                        assertEquals(savedDate, reference.getEditor().getText());
                        field("costi-inline-quantity-1").setText("200,1234");
                        button("costi-save").fire();
                        assertTrue(feedback().contains("decimali"));
                        button("costi-cancel").fire();
                        assertEquals("200", field("costi-inline-quantity-1").getText());
                        field("costi-inline-quantity-1").clear();
                        assertEquals("Non indicata", label("costi-available"));
                        field("costi-inline-quantity-1").setText("0");
                        assertEquals("0 kg", label("costi-available"));
                        button("costi-cancel").fire();
                        field("costi-lot-0-price").setText("80,12345");
                        button("costi-save").fire();
                        assertTrue(feedback().contains("decimali"));
                        button("costi-cancel").fire();
                        assertEquals("80", field("costi-lot-0-price").getText());
                        field("costi-lot-0-price").setText("110");
                        Platform.runLater(() -> answer(ButtonType.CANCEL));
                        ((CheckBox) stage.getScene().lookup("#costi-color-bianca")).fire();
                        assertTrue(
                                ((CheckBox) stage.getScene().lookup("#costi-color-bianca"))
                                        .isSelected());
                        Platform.runLater(() -> answer(ButtonType.CANCEL));
                        button("costi-home").fire();
                        assertEquals(0, homes.get());
                        Platform.runLater(() -> answer(ButtonType.CANCEL));
                        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
                        assertTrue(stage.isShowing());
                        return null;
                    });
            fx(
                    () -> {
                        Platform.runLater(() -> Platform.runLater(() -> answer(ButtonType.CANCEL)));
                        field("costi-inline-quantity-2").requestFocus();
                        return null;
                    });
            await(() -> table().getSelectionModel().getSelectedIndex() == 0);
            fx(
                    () -> {
                        button("costi-cancel").fire();
                        assertEquals("80", field("costi-lot-0-price").getText());
                        table().getSelectionModel().select(1);
                        return null;
                    });
            await(() -> enabled("costi-inline-price-1") && editable("costi-inline-price-2"));
            fx(
                    () -> {
                        assertEquals("", view().state().price());
                        field("costi-inline-quantity-1").requestFocus();
                        return null;
                    });
            await(() -> field("costi-lot-0-price") != null && enabled("costi-lot-0-price"));
            fx(
                    () -> {
                        assertEquals("80", field("costi-lot-0-price").getText());
                        field("costi-inline-price-1").setText("93,5");
                        assertEquals("93,5", view().state().price());
                        button("costi-cancel").fire();
                        assertEquals("92,125", field("costi-inline-price-1").getText());
                        assertTrue(editable("costi-inline-quantity-1"));
                        snapshot("target/powders-unified-wide.png");
                        ((CheckBox) stage.getScene().lookup("#costi-color-bianca")).fire();
                        return null;
                    });
            await(
                    () ->
                            table().getItems().size() == 1
                                    && table().getItems().getFirst().powder().id() == 2
                                    && editable("costi-inline-price-2"));
            fx(
                    () -> {
                        ((CheckBox) stage.getScene().lookup("#costi-color-bianca")).fire();
                        return null;
                    });
            await(
                    () ->
                            table().getItems().size() == 2
                                    && field("costi-lot-0-price") != null
                                    && editable("costi-inline-price-1"));
            fx(
                    () -> {
                        stage.setWidth(1100);
                        stage.setHeight(780);
                        return null;
                    });
            await(() -> stage.getWidth() == 1100);
            fx(
                    () -> {
                        stage.getScene().getRoot().applyCss();
                        stage.getScene().getRoot().layout();
                        assertTrue(
                                stage.getScene()
                                                .lookup("#costi-save")
                                                .localToScene(
                                                        stage.getScene()
                                                                .lookup("#costi-save")
                                                                .getBoundsInLocal())
                                                .getMaxX()
                                        <= stage.getScene().getWidth());
                        TabPane compact = (TabPane) stage.getScene().lookup("#costi-compact-tabs");
                        assertNotNull(compact);
                        compact.getSelectionModel().select(0);
                        stage.getScene().getRoot().applyCss();
                        stage.getScene().getRoot().layout();
                        assertTrue(
                                button("costi-save")
                                                .localToScene(
                                                        button("costi-save").getBoundsInLocal())
                                                .getMaxY()
                                        <= stage.getScene().getHeight());
                        field("costi-inline-price-1").setText("94");
                        assertEquals("94", view().state().price());
                        assertFalse(button("costi-save").isDisabled());
                        button("costi-cancel").fire();
                        compact.getSelectionModel().select(1);
                        snapshot("target/powders-unified-narrow.png");
                        button("costi-home").fire();
                        assertEquals(1, homes.get());
                        session.close();
                        session = null;
                        return null;
                    });
            assertEquals(
                    2,
                    service.load(1).join().version(),
                    "Module disposal must not stop shared workers or mutate quantities");
            assertTrue(asyncErrors.isEmpty(), () -> asyncErrors.toString());
        } finally {
            fx(
                    () -> {
                        if (session != null) session.close();
                        for (Window open : List.copyOf(Window.getWindows()))
                            if (open != stage) open.hide();
                        if (stage != null) stage.hide();
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

    private void lot(int i, String code, String quantity, String price) {
        field("costi-lot-" + i + "-code").setText(code);
        field("costi-lot-" + i + "-quantity").setText(quantity);
        field("costi-lot-" + i + "-price").setText(price);
    }

    private boolean enabled(String id) {
        TextField input = field(id);
        return input != null && !input.isDisabled();
    }

    private boolean editable(String id) {
        TextField input = field(id);
        return input != null && input.isEditable() && !input.isDisabled();
    }

    private TextField field(String id) {
        return (TextField) stage.getScene().lookup("#" + id);
    }

    private Button button(String id) {
        return (Button) stage.getScene().lookup("#" + id);
    }

    private String label(String id) {
        var node = stage.getScene().lookup("#" + id);
        return node instanceof Label label ? label.getText() : "";
    }

    private String feedback() {
        return label("costi-feedback");
    }

    @SuppressWarnings("unchecked")
    private TableView<Row> table() {
        return view().table;
    }

    private PowderPricesView view() {
        return (PowderPricesView) stage.getScene().lookup("#costi-module");
    }

    private void answer(ButtonType type) {
        for (Window window : Window.getWindows())
            if (window.isShowing()
                    && window != stage
                    && window.getScene() != null
                    && window.getScene().getRoot() instanceof DialogPane pane) {
                ((Button) pane.lookupButton(type)).fire();
                return;
            }
        fail("Dialogo atteso non presente");
    }

    private void snapshot(String path) throws Exception {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        var image = stage.getScene().snapshot(null);
        var pixels = image.getPixelReader();
        var out =
                new java.awt.image.BufferedImage(
                        (int) image.getWidth(),
                        (int) image.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++)
            for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, pixels.getArgb(x, y));
        javax.imageio.ImageIO.write(out, "png", Path.of(path).toFile());
    }

    private <T> T fx(Callable<T> action) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(
                () -> {
                    try {
                        result.complete(action.call());
                    } catch (Throwable error) {
                        result.completeExceptionally(error);
                    }
                });
        return result.get(60, TimeUnit.SECONDS);
    }

    private void await(Callable<Boolean> condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < end) {
            if (fx(condition)) return;
            Thread.sleep(40);
        }
        fail("Timeout dell'interfaccia");
    }
}
