package com.orodent.ecos.crm;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.crm.core.database.model.CallOutcome;
import com.orodent.ecos.crm.feature.clienti.nuovocliente.view.NuovoClienteView;
import com.orodent.ecos.crm.feature.clienti.schedacliente.view.SchedaClienteView;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.*;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Tag("javafx")
class CrmUiTest {
    Stage stage;
    ModuleSession session;
    final List<Throwable> errors = new CopyOnWriteArrayList<>();

    @Test
    void opensCreatesLeadRecordsCallAndKeepsOperatorAcrossReopening() throws Exception {
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        String url = "jdbc:derby:memory:crm_ui_" + UUID.randomUUID().toString().replace("-", "");
        AtomicInteger fxSql = new AtomicInteger();
        ConnectionProvider db =
                () -> {
                    if (Platform.isFxApplicationThread()) fxSql.incrementAndGet();
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        var workers = Executors.newFixedThreadPool(3);
        var homes = new AtomicInteger();
        var operator = new OperatorSession();
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException alreadyStarted) {
        }
        Platform.setImplicitExit(false);
        var context =
                new ModuleContext(
                        db,
                        workers,
                        new ClientiCsvImportService(db, workers),
                        new VenditeCsvImportService(db, workers),
                        homes::incrementAndGet,
                        operator);
        try {
            fx(
                    () -> {
                        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> errors.add(e));
                        session = new CrmModule().open(context);
                        stage = new Stage();
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
                            button("crm-select-operator") != null
                                    && enabledButton("crm-select-operator"));
            fx(
                    () -> {
                        button("crm-select-operator").fire();
                        return null;
                    });
            await(() -> button("crm-create-lead") != null && enabledButton("crm-create-lead"));
            fx(
                    () -> {
                        button("crm-create-lead").fire();
                        return null;
                    });
            await(
                    () ->
                            stage.getScene().lookup("#crm-new-lead") != null
                                    && enabledButton("crm-save-lead"));
            fx(
                    () -> {
                        var page = (NuovoClienteView) stage.getScene().lookup("#crm-new-lead");
                        page.getNameField().setText("Laboratorio Rossi");
                        page.getPhoneField().setText("123456789");
                        page.getEmailField().setText("info@example.test");
                        button("crm-save-lead").fire();
                        return null;
                    });
            await(
                    () ->
                            stage.getScene().lookup("#crm-profile") != null
                                    && enabledButton("crm-new-call"));
            fx(
                    () -> {
                        button("crm-new-call").fire();
                        var profile = (SchedaClienteView) stage.getScene().lookup("#crm-profile");
                        profile.getNoteTextArea()
                                .setText("Inviato listino, richiamare la prossima settimana");
                        profile.getNextCallDatePicker()
                                .setValue(java.time.LocalDate.now().plusDays(2));
                        profile.getCallOutcomeChoiceBox().setValue(CallOutcome.LISTINO);
                        Platform.runLater(() -> answer(ButtonType.CANCEL));
                        button("crm-home").fire();
                        assertEquals(0, homes.get());
                        button("crm-save-interaction").fire();
                        return null;
                    });
            await(() -> button("crm-new-call") != null && enabledButton("crm-new-call"));
            fx(
                    () -> {
                        snapshot("target/crm-profile.png");
                        button("crm-nav-calendar").fire();
                        return null;
                    });
            await(
                    () ->
                            stage.getScene().lookup("#crm-calendar") != null
                                    && !stage.getScene().lookup("#crm-calendar").isDisabled());
            fx(
                    () -> {
                        snapshot("target/crm-calendar.png");
                        button("crm-nav-settings").fire();
                        return null;
                    });
            await(
                    () ->
                            stage.getScene().lookup("#crm-settings") != null
                                    && !stage.getScene().lookup("#crm-settings").isDisabled());
            fx(
                    () -> {
                        snapshot("target/crm-settings.png");
                        button("crm-home").fire();
                        assertEquals(1, homes.get());
                        session.close();
                        session = new CrmModule().open(context);
                        stage.getScene().setRoot(session.root());
                        return null;
                    });
            await(() -> button("crm-create-lead") != null && enabledButton("crm-create-lead"));
            fx(
                    () -> {
                        assertNull(stage.getScene().lookup("#crm-login"));
                        snapshot("target/crm-list.png");
                        return null;
                    });
            assertEquals(0, fxSql.get(), "No JDBC should run on FX");
            assertTrue(errors.isEmpty(), errors::toString);
            db.withConnection(
                    c -> {
                        try (var q = c.createStatement();
                                var r = q.executeQuery("SELECT COUNT(*) FROM CRM.INTERAZIONI")) {
                            r.next();
                            assertEquals(1, r.getInt(1));
                        }
                        return null;
                    });
        } finally {
            fx(
                    () -> {
                        if (session != null) session.close();
                        for (Window w : List.copyOf(Window.getWindows())) w.hide();
                        return null;
                    });
            workers.shutdown();
            assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS));
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException e) {
                assertEquals("08006", e.getSQLState());
            }
        }
    }

    boolean enabledButton(String id) {
        Button button = button(id);
        return button != null && !button.isDisabled();
    }

    Button button(String id) {
        return (Button) stage.getScene().lookup("#" + id);
    }

    void answer(ButtonType type) {
        for (Window w : Window.getWindows())
            if (w != stage && w.isShowing() && w.getScene().getRoot() instanceof DialogPane pane) {
                ((Button) pane.lookupButton(type)).fire();
                return;
            }
        fail("Dialogo atteso non presente");
    }

    <T> T fx(Callable<T> task) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(
                () -> {
                    try {
                        result.complete(task.call());
                    } catch (Throwable e) {
                        result.completeExceptionally(e);
                    }
                });
        return result.get(60, TimeUnit.SECONDS);
    }

    void await(Callable<Boolean> check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
        while (System.nanoTime() < deadline) {
            if (fx(check)) return;
            Thread.sleep(50);
        }
        fail("Timeout CRM");
    }

    void snapshot(String path) throws Exception {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        var image = stage.getScene().snapshot(null);
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
