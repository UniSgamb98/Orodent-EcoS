package com.orodent.ecos.costi;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.csv.CsvImportResult;
import com.orodent.ecos.costi.orders.*;
import com.orodent.ecos.costi.orders.PowderOrders.*;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

@Tag("javafx")
class OrdersUiTest {
    @Test
    void previewAlignsBesideCompactSortedOrdersAndKeepsMappingsWhenResized() throws Exception {
        FxTestSupport.start();
        var task =
                new FutureTask<Void>(
                        () -> {
                            Stage stage = new Stage();
                            try {
                                OrdersView view = new OrdersView();
                                stage.setScene(new Scene(view, 1900, 1000));
                                stage.getScene()
                                        .getStylesheets()
                                        .add(
                                                Path.of(
                                                                "../orodent-app/src/main/resources/css/global.css")
                                                        .toUri()
                                                        .toString());
                                stage.show();
                                Row row = row("SUPPLIER", 1, LocalDate.of(2026, 12, 10));
                                Powder white = new Powder(1, "ZST5Y-025OP", "Bianca 5Y", "BIANCA");
                                Powder yellow =
                                        new Powder(2, "ZRZPEX4MYELLOW", "Gialla 4Y", "GIALLA");
                                Powder pink = new Powder(3, "ZRZPEX4MPINK", "Pink4M", "ROSA");
                                Powder gray = new Powder(4, "ZRZPEX4MGRAY", "Grigia 4Y", "GRIGIA");
                                var powders = List.of(white, yellow, pink, gray);
                                var preview =
                                        new Preview(
                                                new CsvImportResult<>(
                                                        1,
                                                        List.of(
                                                                new CsvImportResult.ImportedRow<>(
                                                                        2, row)),
                                                        List.of(),
                                                        List.of()),
                                                powders,
                                                Map.of(),
                                                Map.of(),
                                                List.of());
                                Stored overridden =
                                        new Stored(1, 1, 1, row, LocalDate.of(2026, 12, 20), false);
                                Stored ninth =
                                        new Stored(
                                                3,
                                                1,
                                                3,
                                                row("PINK-CSV", 9, row.expected()),
                                                null,
                                                false);
                                Stored tenth =
                                        new Stored(
                                                4,
                                                1,
                                                4,
                                                row("GRAY-CSV", 10, row.expected()),
                                                null,
                                                false);
                                Stored second =
                                        new Stored(
                                                2,
                                                1,
                                                2,
                                                row("YELLOW-CSV", 2, row.expected()),
                                                null,
                                                false);
                                Stored noDate =
                                        new Stored(5, 1, 1, row("WHITE-CSV", 3, null), null, false);
                                view.catalogue(
                                        new OrderService.Catalogue(
                                                List.of(overridden, tenth, noDate, ninth, second),
                                                powders));
                                layout(view);
                                assertEquals(
                                        List.of(
                                                "Data",
                                                "Ordine",
                                                "Polvere",
                                                "Ordinati",
                                                "€/kg",
                                                "Data prevista"),
                                        view.table.getColumns().stream()
                                                .map(TableColumn::getText)
                                                .toList());
                                assertEquals(
                                        List.of(2L, 3L, 4L, 1L, 5L),
                                        view.table.getItems().stream().map(Stored::id).toList());
                                assertEquals(
                                        row.expected(),
                                        view.table.getColumns().getFirst().getCellData(overridden));
                                assertEquals(
                                        overridden.date(),
                                        view.table.getColumns().getLast().getCellData(overridden));
                                assertTrue(
                                        view.table.getWidth() <= 890,
                                        "No full-window table for six compact columns");
                                view.table.getSelectionModel().select(ninth);
                                view.preview(preview);
                                layout(view);
                                assertTrue(view.apply.isDisabled());
                                assertEquals(1, GridPane.getColumnIndex(view.previewPane));
                                assertTrue(
                                        view.previewPane.getBoundsInParent().getMinX()
                                                > view.table.getWidth());
                                @SuppressWarnings("unchecked")
                                ComboBox<Powder> mapping =
                                        (ComboBox<Powder>) view.lookup("#orders-mapping-1");
                                assertTrue(mapping.getWidth() <= 240);
                                mapping.setValue(white);
                                assertFalse(view.apply.isDisabled());
                                assertEquals(1L, view.mappings().get(row.source()));
                                assertSame(ninth, view.table.getSelectionModel().getSelectedItem());
                                assertTrue(view.lookupAll(".powder-pink").size() > 0);
                                assertTrue(view.lookupAll(".powder-yellow").size() > 0);
                                assertTrue(view.lookupAll(".powder-gray").size() > 0);
                                assertTrue(
                                        view.lookupAll(".table-cell").stream()
                                                .filter(
                                                        n ->
                                                                n instanceof TableCell<?, ?> c
                                                                        && !c.isEmpty()
                                                                        && c.getTableColumn()
                                                                                == view.table
                                                                                        .getColumns()
                                                                                        .get(3))
                                                .map(n -> (TableCell<?, ?>) n)
                                                .allMatch(
                                                        c ->
                                                                c.getAlignment()
                                                                        == javafx.geometry.Pos
                                                                                .CENTER_RIGHT));
                                snapshot(stage, "target/orders-wide-ui.png");

                                stage.setWidth(1280);
                                view.resize(1280, 900);
                                layout(view);
                                assertEquals(0, GridPane.getColumnIndex(view.previewPane));
                                assertEquals(1, GridPane.getRowIndex(view.previewPane));
                                assertSame(mapping, view.lookup("#orders-mapping-1"));
                                assertEquals(white, mapping.getValue());
                                assertEquals(1L, view.mappings().get(row.source()));
                                assertSame(ninth, view.table.getSelectionModel().getSelectedItem());
                                snapshot(stage, "target/orders-narrow-ui.png");
                                stage.setWidth(1900);
                                view.resize(1900, 1000);
                                layout(view);
                                assertEquals(1, GridPane.getColumnIndex(view.previewPane));
                                assertSame(mapping, view.lookup("#orders-mapping-1"));
                                assertSame(preview, view.pending());
                                assertFalse(view.apply.isDisabled());
                                view.clearPreview();
                                layout(view);
                                assertNull(view.pending());
                                assertFalse(view.previewPane.isManaged());
                                assertSame(ninth, view.table.getSelectionModel().getSelectedItem());
                            } finally {
                                stage.close();
                            }
                            return null;
                        });
        Platform.runLater(task);
        task.get(60, TimeUnit.SECONDS);
    }

    private Row row(String code, int position, LocalDate expected) {
        return new Row(
                new Key("2600266", "26/00034", position),
                LocalDate.of(2026, 7, 14),
                expected,
                code,
                "Polvere",
                new BigDecimal("300"),
                BigDecimal.ZERO,
                new BigDecimal("300"),
                new BigDecimal("55"));
    }

    private void layout(OrdersView view) {
        view.applyCss();
        view.layout();
        view.layout();
    }

    private void snapshot(Stage stage, String path) throws Exception {
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
}
