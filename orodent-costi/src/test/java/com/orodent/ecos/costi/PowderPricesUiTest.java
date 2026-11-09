package com.orodent.ecos.costi;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.costi.model.PowderPrices.*;
import com.orodent.ecos.costi.service.PowderPriceService;
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
    void editsBothModesPreservesLotsAndProtectsUnsavedChanges() throws Exception {
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
                                "INSERT INTO TON.POLVERI(codice,nome,colore) VALUES"
                                        + " ('Z-PEX4M','Polvere bianca 4Y','BIANCA')");
                        q.executeUpdate(
                                "INSERT INTO TON.POLVERI(codice,nome,colore) VALUES"
                                        + " ('PINK4M','Polvere rosa','ROSA')");
                    }
                    return null;
                });
        var workers = Executors.newFixedThreadPool(2);
        var homes = new AtomicInteger();
        var ready = new CompletableFuture<Void>();
        Platform.startup(
                () -> {
                    Platform.setImplicitExit(false);
                    Thread.currentThread()
                            .setUncaughtExceptionHandler((t, e) -> asyncErrors.add(e));
                    ready.complete(null);
                });
        ready.get(10, TimeUnit.SECONDS);
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
                                    && !field("costi-simple-price").isDisabled()
                                    && field("costi-simple-price").isVisible());
            fx(
                    () -> {
                        field("costi-simple-price").setText("92,1250");
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
                        toggle("costi-mode-advanced").fire();
                        button("costi-lot-add").fire();
                        button("costi-lot-add").fire();
                        lot(0, "L-2026-041", "100", "80");
                        lot(1, "L-2026-041", "50", "100");
                        assertTrue(label("costi-total-quantity").contains("150"));
                        assertTrue(label("costi-average-price").contains("86,6667"));
                        toggle("costi-mode-simple").fire();
                        assertEquals("92,125", field("costi-simple-price").getText());
                        toggle("costi-mode-advanced").fire();
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
                        field("costi-lot-0-price").setText("80,12345");
                        button("costi-save").fire();
                        assertTrue(feedback().contains("decimali"));
                        button("costi-cancel").fire();
                        assertEquals("80", field("costi-lot-0-price").getText());
                        field("costi-lot-0-price").setText("110");
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
                        table().getSelectionModel().select(1);
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
            await(
                    () ->
                            !field("costi-simple-price").isDisabled()
                                    && !toggle("costi-mode-advanced").isSelected());
            fx(
                    () -> {
                        assertEquals("", field("costi-simple-price").getText());
                        table().getSelectionModel().selectFirst();
                        return null;
                    });
            await(
                    () ->
                            toggle("costi-mode-advanced").isSelected()
                                    && !field("costi-lot-0-price").isDisabled());
            fx(
                    () -> {
                        assertEquals("80", field("costi-lot-0-price").getText());
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
            Platform.exit();
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

    private TextField field(String id) {
        return (TextField) stage.getScene().lookup("#" + id);
    }

    private Button button(String id) {
        return (Button) stage.getScene().lookup("#" + id);
    }

    private ToggleButton toggle(String id) {
        return (ToggleButton) stage.getScene().lookup("#" + id);
    }

    private String label(String id) {
        return ((Label) stage.getScene().lookup("#" + id)).getText();
    }

    private String feedback() {
        return label("costi-feedback");
    }

    @SuppressWarnings("unchecked")
    private TableView<Row> table() {
        return (TableView<Row>) stage.getScene().lookup("#costi-powders-table");
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
        return result.get(15, TimeUnit.SECONDS);
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
