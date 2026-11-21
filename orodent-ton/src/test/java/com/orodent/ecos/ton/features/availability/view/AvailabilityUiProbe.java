package com.orodent.ecos.ton.features.availability.view;

import static com.orodent.ecos.ton.features.availability.model.Availability.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.core.csv.*;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;
import com.orodent.ecos.ton.TonModule;
import com.orodent.ecos.ton.features.availability.service.AvailabilityService;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.Article;
import com.orodent.ecos.ton.features.catalog.service.CatalogService;

import javafx.application.Platform;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.*;

import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

public final class AvailabilityUiProbe {
    Stage stage;
    ModuleSession session;
    final List<Throwable> failures = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        try {
            new AvailabilityUiProbe().run();
            System.exit(0);
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    void run() throws Exception {
        String url =
                "jdbc:derby:memory:availability_ui_"
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
        var workers = Executors.newFixedThreadPool(3);
        var service = new AvailabilityService(db, workers);
        var catalog = new CatalogService(db, workers);
        Line thor =
                (Line)
                        catalog.create(new LineDraft("MST", "Thor", "", "", "", true))
                                .join()
                                .entry();
        Line eos =
                (Line) catalog.create(new LineDraft("MS", "Eos", "", "", "", true)).join().entry();
        Product a0 =
                (Product)
                        catalog.create(ProductDraft.configured(thor.id(), "A0", "", true))
                                .join()
                                .entry();
        Product a1 =
                (Product)
                        catalog.create(ProductDraft.configured(thor.id(), "A1", "", true))
                                .join()
                                .entry();
        Product e0 =
                (Product)
                        catalog.create(ProductDraft.configured(eos.id(), "A0", "", true))
                                .join()
                                .entry();
        StringBuilder csv = new StringBuilder("Codice;Quantità\n");
        long expectedTotal = 0;
        for (int i = 1; i <= 80; i++) {
            String code = "ZR98" + String.format("%02d", i) + "MSTA0";
            catalog.create(
                            new ArticleDraft(
                                    code,
                                    "Thor A0",
                                    a0.id(),
                                    new BigDecimal("98"),
                                    BigDecimal.valueOf(i),
                                    "",
                                    true))
                    .join();
            csv.append(code).append(';').append(i).append('\n');
            expectedTotal += i;
        }
        Article target =
                (Article)
                        catalog.create(
                                        new ArticleDraft(
                                                "ZR9512MSTA1",
                                                "Thor A1",
                                                a1.id(),
                                                new BigDecimal("95"),
                                                new BigDecimal("12"),
                                                "",
                                                true))
                                .join()
                                .entry();
        catalog.create(
                        new ArticleDraft(
                                "ZR9516MSTA1",
                                "Thor A1",
                                a1.id(),
                                new BigDecimal("95"),
                                new BigDecimal("16"),
                                "",
                                true))
                .join();
        catalog.create(
                        new ArticleDraft(
                                "ZR9812MSA0",
                                "Eos A0",
                                e0.id(),
                                new BigDecimal("98"),
                                new BigDecimal("12"),
                                "",
                                true))
                .join();
        csv.append("ZR9512MSTA1;0\nZR9516MSTA1;4\nZR9812MSA0;6\n");
        expectedTotal += 10;
        var parsed =
                new CsvReader()
                        .read(
                                new StringReader(csv.toString()),
                                CsvReadOptions.semicolonSeparated());
        var preview =
                service.preview(
                                Kind.DISPONIBILITA,
                                parsed,
                                "giacenze.csv",
                                LocalDate.now(),
                                "Codice",
                                "Quantità",
                                Numbers.ITALIAN,
                                false,
                                false)
                        .join();
        service.apply(preview).join();
        Platform.startup(() -> {});
        Platform.setImplicitExit(false);
        try {
            fx(
                    () -> {
                        Thread.currentThread()
                                .setUncaughtExceptionHandler(
                                        (t, e) -> {
                                            failures.add(e);
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
                        button("ton-availability-button").fire();
                        return null;
                    });
            await(() -> rows() == 83);
            long total = expectedTotal;
            fx(
                    () -> {
                        assertEquals(
                                "Disponibilità",
                                ((Label) node("ton-breadcrumb"))
                                        .getText()
                                        .substring(
                                                ((Label) node("ton-breadcrumb"))
                                                                .getText()
                                                                .lastIndexOf(" › ")
                                                        + 3));
                        assertEquals(4, table().getColumns().size());
                        assertTrue(
                                node("ton-availability-view").lookupAll(".text-field").isEmpty());
                        assertEquals(
                                Long.toString(total),
                                ((Label) node("ton-availability-stock-total")).getText());
                        assertEquals(
                                "—", ((Label) node("ton-availability-orders-total")).getText());
                        assertTrue(table().getHeight() > 650);
                        assertTrue(table().getWidth() > 1000);
                        screenshot("target/availability-desktop.png");
                        button("ton-availability-line-" + thor.id()).fire();
                        assertEquals(
                                a0.id(),
                                table().getSelectionModel()
                                        .getSelectedItem()
                                        .article()
                                        .productId());
                        assertEquals(83, rows());
                        var b = button("ton-availability-line-" + thor.id());
                        var p = b.localToScreen(b.getBoundsInLocal());
                        b.fireEvent(
                                new ContextMenuEvent(
                                        ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                                        3,
                                        3,
                                        p.getMinX() + 3,
                                        p.getMinY() + 3,
                                        false,
                                        new PickResult(b, 3, 3)));
                        assertTrue(b.getContextMenu().isShowing());
                        screenshot("target/availability-product-menu.png");
                        b.getContextMenu().getItems().stream()
                                .filter(
                                        i ->
                                                ("ton-availability-product-" + a1.id())
                                                        .equals(i.getId()))
                                .findFirst()
                                .orElseThrow()
                                .fire();
                        b.getContextMenu().hide();
                        assertEquals(
                                target.id(),
                                table().getSelectionModel().getSelectedItem().article().id());
                        assertEquals(83, rows());
                        return null;
                    });
            fx(
                    () -> {
                        layout();
                        var row =
                                table().lookupAll(".table-row-cell").stream()
                                        .filter(
                                                n ->
                                                        n instanceof TableRow<?> r
                                                                && r.getItem() instanceof Row x
                                                                && x.article().id() == target.id())
                                        .findFirst()
                                        .orElseThrow();
                        row.fireEvent(
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
                        return null;
                    });
            await(
                    () ->
                            node("ton-production-diameter") instanceof ComboBox<?> b
                                    && b.getValue() instanceof BigDecimal d
                                    && d.compareTo(new BigDecimal("95")) == 0);
            fx(
                    () -> {
                        assertTrue(
                                ((ToggleButton) node("ton-production-color-" + a1.id()))
                                        .isSelected());
                        assertEquals(
                                "← Torna alla disponibilità",
                                button("ton-production-back").getText());
                        button("ton-production-back").fire();
                        return null;
                    });
            await(() -> rows() == 83);
            fx(
                    () -> {
                        assertEquals(
                                target.id(),
                                table().getSelectionModel().getSelectedItem().article().id());
                        stage.setWidth(1180);
                        stage.setHeight(800);
                        return null;
                    });
            await(() -> stage.getScene().getWidth() < 1200);
            fx(
                    () -> {
                        assertTrue(table().getWidth() > 500);
                        screenshot("target/availability-compact.png");
                        return null;
                    });
            Path file = Path.of("target/availability-orders.csv");
            Files.writeString(file, "Codice;Quantità\nZR9512MSTA1;5\nZR9512MSTA1;2\n");
            var imported = new AtomicBoolean();
            fx(
                    () -> {
                        new AvailabilityImportDialog(
                                        stage,
                                        Kind.ORDINI,
                                        file,
                                        service,
                                        b -> {},
                                        () -> imported.set(true))
                                .show();
                        return null;
                    });
            await(
                    () ->
                            node("ton-availability-import-code") instanceof ComboBox<?> b
                                    && b.getValue() != null);
            fx(
                    () -> {
                        button("ton-availability-import-preview").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-availability-import-apply") instanceof Button b
                                    && !b.isDisabled());
            fx(
                    () -> {
                        button("ton-availability-import-apply").fire();
                        return null;
                    });
            await(imported::get);
            fx(
                    () -> {
                        button("ton-availability-refresh").fire();
                        return null;
                    });
            await(
                    () ->
                            node("ton-availability-orders-total") instanceof Label l
                                    && l.getText().equals("7"));
            fx(
                    () -> {
                        assertEquals(
                                Long.toString(total),
                                ((Label) node("ton-availability-stock-total")).getText());
                        assertEquals(
                                target.id(),
                                table().getSelectionModel().getSelectedItem().article().id());
                        assertTrue(failures.isEmpty(), failures.toString());
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

    @SuppressWarnings("unchecked")
    TableView<Row> table() {
        return (TableView<Row>) node("ton-availability-table");
    }

    int rows() {
        var t = table();
        return t == null ? -1 : t.getItems().size();
    }

    Node node(String id) {
        if (stage != null && stage.getScene() != null) {
            var n = stage.getScene().getRoot().lookup("#" + id);
            if (n != null) return n;
        }
        for (var w : Window.getWindows())
            if (w.isShowing() && w.getScene() != null) {
                var n = w.getScene().getRoot().lookup("#" + id);
                if (n != null) return n;
            }
        return null;
    }

    Button button(String id) {
        return (Button) Objects.requireNonNull(node(id), id);
    }

    void layout() {
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    <T> T fx(Callable<T> work) throws Exception {
        var t =
                new FutureTask<T>(
                        () -> {
                            if (stage != null) layout();
                            return work.call();
                        });
        Platform.runLater(t);
        return t.get(25, TimeUnit.SECONDS);
    }

    void await(BooleanSupplier test) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (System.nanoTime() < end) {
            if (fx(test::getAsBoolean)) return;
            Thread.sleep(40);
        }
        throw new AssertionError("Availability UI timed out: " + failures);
    }

    void screenshot(String name) throws Exception {
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
        javax.imageio.ImageIO.write(out, "png", Path.of(name).toFile());
    }
}
