package com.orodent.ecos.registro;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.audit.AuditContext;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.Stage;

import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@Tag("javafx")
class RegistroUiTest {
    Stage stage;
    ModuleSession session;

    <T> T fx(Callable<T> job) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(
                () -> {
                    try {
                        result.complete(job.call());
                    } catch (Throwable e) {
                        result.completeExceptionally(e);
                    }
                });
        return result.get(15, TimeUnit.SECONDS);
    }

    void await(Callable<Boolean> condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < end) {
            if (fx(condition)) return;
            Thread.sleep(60);
        }
        fail("UI non pronta");
    }

    @Test
    void opensFiltersShowsDeletedContentAndNavigatesWithoutSqlOnFx() throws Exception {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException started) {
        }
        Platform.setImplicitExit(false);
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        String url =
                "jdbc:derby:memory:register_ui_" + UUID.randomUUID().toString().replace("-", "");
        var fxSql = new AtomicInteger();
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
        String customer = UUID.randomUUID().toString(), note = UUID.randomUUID().toString();
        UUID actor = UUID.randomUUID();
        db.withTransaction(
                c -> {
                    AuditContext.begin(c, actor, "Mario Rossi", "CRM");
                    try (var q = c.createStatement()) {
                        q.executeUpdate(
                                "INSERT INTO CRM.CLIENTI(ID,RAGIONE_SOCIALE) VALUES ('"
                                        + customer
                                        + "','Laboratorio Rossi')");
                        q.executeUpdate(
                                "INSERT INTO CRM.NOTE_CLIENTE(ID,CLIENTE_ID,TESTO) VALUES ('"
                                        + note
                                        + "','"
                                        + customer
                                        + "','Nota importante da ricostruire')");
                    }
                    return null;
                });
        db.withTransaction(
                c -> {
                    AuditContext.begin(c, UUID.randomUUID(), "Lucia Bianchi", "CRM");
                    try (var q = c.createStatement()) {
                        q.executeUpdate("DELETE FROM CRM.NOTE_CLIENTE");
                    }
                    return null;
                });
        var workers = Executors.newFixedThreadPool(2);
        var navigation = new AtomicReference<String>();
        var context =
                new ModuleContext(
                        db,
                        workers,
                        new ClientiCsvImportService(db, workers),
                        new VenditeCsvImportService(db, workers),
                        () -> {},
                        new OperatorSession(),
                        (module, id) -> navigation.set(module + ":" + id));
        try {
            fx(
                    () -> {
                        session = new RegistroModule().open(context);
                        stage = new Stage();
                        stage.setScene(new Scene(session.root(), 1500, 900));
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
                            ((TableView<?>) stage.getScene().lookup("#registro-events"))
                                            .getItems()
                                            .size()
                                    == 2);
            fx(
                    () -> {
                        var view = (RegistroView) session.root();
                        assertEquals(java.time.LocalDate.now().minusDays(7), view.from.getValue());
                        assertEquals(java.time.LocalDate.now(), view.to.getValue());
                        assertEquals(
                                java.time.LocalDate.now()
                                        .format(
                                                java.time.format.DateTimeFormatter.ofPattern(
                                                        "dd/MM/uuuu")),
                                view.to.getEditor().getText());
                        view.kind.setValue("Eliminazione");
                        view.customer.setText("Rossi");
                        view.apply.fire();
                        return null;
                    });
            await(
                    () ->
                            ((TableView<?>) stage.getScene().lookup("#registro-events"))
                                            .getItems()
                                            .size()
                                    == 1);
            fx(
                    () -> {
                        var view = (RegistroView) session.root();
                        assertNotNull(view.from.getValue());
                        assertNotNull(view.to.getValue());
                        view.table.getSelectionModel().selectFirst();
                        return null;
                    });
            await(
                    () ->
                            stage
                                    .getScene()
                                    .getRoot()
                                    .lookupAll("#registro-details .text-area")
                                    .stream()
                                    .anyMatch(
                                            n ->
                                                    ((TextArea) n)
                                                            .getText()
                                                            .equals(
                                                                    "Nota importante da"
                                                                            + " ricostruire")));
            fx(
                    () -> {
                        var root = stage.getScene().getRoot();
                        root.applyCss();
                        root.layout();
                        assertTrue(
                                root.lookupAll("#registro-details .text-area").stream()
                                        .allMatch(n -> !((TextArea) n).isEditable()));
                        assertTrue(
                                ((Label) root.lookup("#registro-total-deleted"))
                                        .getText()
                                        .startsWith("1"));
                        var image = root.snapshot(null, null);
                        var out =
                                new java.awt.image.BufferedImage(
                                        (int) image.getWidth(),
                                        (int) image.getHeight(),
                                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for (int y = 0; y < out.getHeight(); y++)
                            for (int x = 0; x < out.getWidth(); x++)
                                out.setRGB(x, y, image.getPixelReader().getArgb(x, y));
                        javax.imageio.ImageIO.write(
                                out, "png", Path.of("target/registro-desktop.png").toFile());
                        var row =
                                (TableRow<?>)
                                        root.lookupAll(".table-row-cell").stream()
                                                .filter(
                                                        n ->
                                                                n instanceof TableRow<?> r
                                                                        && !r.isEmpty())
                                                .findFirst()
                                                .orElseThrow();
                        row.fireEvent(
                                new MouseEvent(
                                        MouseEvent.MOUSE_CLICKED,
                                        0,
                                        0,
                                        0,
                                        0,
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
                                        false,
                                        null));
                        return null;
                    });
            await(() -> ("crm:" + customer).equals(navigation.get()));
            fx(
                    () -> {
                        stage.setWidth(1040);
                        return null;
                    });
            await(() -> stage.getScene().getWidth() < 1100);
            assertEquals(0, fxSql.get());
        } finally {
            fx(
                    () -> {
                        if (session != null) session.close();
                        if (stage != null) stage.hide();
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
}
