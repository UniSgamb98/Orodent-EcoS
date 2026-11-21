package com.orodent.ecos.ton.features.suppliers.view;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.*;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;
import com.orodent.ecos.ton.TonModule;
import com.orodent.ecos.ton.features.powders.model.PowderModel.*;
import com.orodent.ecos.ton.features.powders.service.PowderService;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

public final class SuppliersUiProbe {
    Stage stage;
    ModuleSession session;

    public static void main(String[] args) {
        try {
            new SuppliersUiProbe().run();
            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    void run() throws Exception {
        String url =
                "jdbc:derby:memory:supplier_ui_" + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        var workers = Executors.newFixedThreadPool(2);
        Platform.startup(() -> {});
        Platform.setImplicitExit(false);
        try {
            fx(
                    () -> {
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
                        stage.setScene(new Scene(session.root(), 1500, 950));
                        stage.getScene()
                                .getStylesheets()
                                .add(
                                        Path.of("../orodent-app/src/main/resources/css/global.css")
                                                .toUri()
                                                .toString());
                        stage.show();
                        assertNotNull(node("ton-suppliers-button"));
                        button("ton-suppliers-button").fire();
                        return null;
                    });
            await(() -> node("ton-suppliers-table") instanceof TableView<?> t && !t.isDisabled());
            fx(
                    () -> {
                        assertTrue(
                                node("ton-suppliers-list")
                                        .getStyleClass()
                                        .contains("ton-list-page"));
                        button("ton-suppliers-new").fire();
                        field("ton-suppliers-code").setText("TS");
                        field("ton-suppliers-name").setText("Tosoh");
                        button("ton-suppliers-save").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-suppliers-table") instanceof TableView<?> t
                                    && t.getItems().size() == 1
                                    && !t.isDisabled());
            var supplier = db.withConnection(c -> SupplierCatalogue.list(c, "", false).getFirst());
            fx(
                    () -> {
                        var t = (TableView<?>) node("ton-suppliers-table");
                        t.getSelectionModel().selectFirst();
                        t.getOnKeyPressed()
                                .handle(
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
            await(() -> node("ton-suppliers-editor").isVisible());
            fx(
                    () -> {
                        field("ton-suppliers-name").setText("Tosoh Japan");
                        button("ton-suppliers-save").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-suppliers-list").isVisible()
                                    && !node("ton-suppliers-table").isDisabled());
            fx(
                    () -> {
                        nav("technical");
                        button("ton-tech-powders-button").fire();
                        return null;
                    });
            await(() -> node("ton-powders-table") instanceof TableView<?> t && !t.isDisabled());
            fx(
                    () -> {
                        button("ton-powder-new").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-powder-supplier") instanceof ComboBox<?> cb
                                    && cb.getItems().stream()
                                            .anyMatch(o -> o instanceof SupplierCatalogue.Entry));
            fx(
                    () -> {
                        ComboBox<SupplierCatalogue.Entry> cb =
                                (ComboBox<SupplierCatalogue.Entry>) node("ton-powder-supplier");
                        assertFalse(cb.isEditable());
                        cb.setValue(
                                cb.getItems().stream()
                                        .filter(Objects::nonNull)
                                        .findFirst()
                                        .orElseThrow());
                        assertEquals("TS · Tosoh Japan", cb.getValue().toString());
                        field("ton-powder-code").setText("P1");
                        field("ton-powder-name").setText("Bianca");
                        ((ComboBox<PowderColor>) node("ton-powder-color"))
                                .setValue(PowderColor.BIANCA);
                        button("ton-powder-save").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-powder-state") instanceof Label l
                                    && !l.getText().contains("nuova bozza"));
            var powders = new PowderService(db, workers);
            var powder = powders.findAll("P1", false).join().rows().getFirst().powder();
            assertEquals(supplier.id(), powder.supplierId());
            assertEquals("Tosoh Japan", powder.supplier());
            db.withTransaction(
                    c -> {
                        var current = SupplierCatalogue.load(c, supplier.id());
                        return SupplierCatalogue.save(
                                c,
                                current,
                                new SupplierCatalogue.Draft(current.code(), current.name(), false));
                    });
            fx(
                    () -> {
                        button("ton-powder-reload").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-powder-supplier") instanceof ComboBox<?> cb
                                    && cb.getValue() instanceof SupplierCatalogue.Entry e
                                    && !e.active());
            fx(
                    () -> {
                        assertTrue(
                                ((ComboBox<?>) node("ton-powder-supplier"))
                                        .getValue()
                                        .toString()
                                        .contains("Archiviato"));
                        button("ton-powders-back").fire();
                        return null;
                    });
            await(() -> node("ton-powders-table") instanceof TableView<?> t && !t.isDisabled());
            fx(
                    () -> {
                        button("ton-powder-new").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-powder-code") instanceof TextField f
                                    && f.getText().isEmpty()
                                    && ((ComboBox<?>) node("ton-powder-supplier")).getItems().size()
                                            == 1);
            fx(
                    () -> {
                        assertTrue(
                                ((ComboBox<?>) node("ton-powder-supplier"))
                                        .getItems().stream()
                                                .noneMatch(
                                                        o -> o instanceof SupplierCatalogue.Entry));
                        return null;
                    });
        } finally {
            fx(
                    () -> {
                        if (session != null) session.close();
                        if (stage != null) stage.close();
                        return null;
                    });
            workers.shutdown();
            workers.awaitTermination(10, TimeUnit.SECONDS);
            Platform.exit();
        }
    }

    Node node(String id) {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        return stage.getScene().getRoot().lookup("#" + id);
    }

    Button button(String id) {
        return (Button) Objects.requireNonNull(node(id), id);
    }

    TextField field(String id) {
        return (TextField) Objects.requireNonNull(node(id), id);
    }

    void nav(String id) {
        if (node("ton-nav-" + id) == null) button("ton-menu-button").fire();
        button("ton-nav-" + id).fire();
    }

    <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<T>(work);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }

    void await(BooleanSupplier test) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (fx(test::getAsBoolean)) return;
            Thread.sleep(40);
        }
        throw new AssertionError("Supplier UI timed out");
    }
}
