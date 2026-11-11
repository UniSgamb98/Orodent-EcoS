package com.orodent.ecos.costi;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.csv.CsvImportResult;
import com.orodent.ecos.costi.orders.*;
import com.orodent.ecos.costi.orders.PowderOrders.*;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
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
    void fullHeightWorkspaceRetainsImportAndMappingsAcrossLayouts() throws Exception {
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
                                Powder white =
                                        new Powder(1, "ZST5Y-025OP", "S Bianca 5Y", "BIANCA");
                                Powder yellow =
                                        new Powder(2, "ZRZPEX4MYELLOW", "Gialla 4Y", "GIALLA");
                                Powder pink = new Powder(3, "ZRZPEX4MPINK", "Pink4M", "ROSA");
                                Powder gray = new Powder(4, "ZRZPEX4MGRAY", "Grigia 4Y", "GRIGIA");
                                var powders = List.of(white, yellow, pink, gray);
                                Row row = row("ZST5Y-025OP-EXT", 1, LocalDate.of(2026, 12, 10));
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
                                var stored =
                                        new ArrayList<Stored>(
                                                List.of(
                                                        overridden,
                                                        ninth,
                                                        new Stored(
                                                                4,
                                                                1,
                                                                4,
                                                                row("GRAY-CSV", 10, row.expected()),
                                                                null,
                                                                false),
                                                        new Stored(
                                                                2,
                                                                1,
                                                                2,
                                                                row(
                                                                        "YELLOW-CSV",
                                                                        2,
                                                                        row.expected()),
                                                                null,
                                                                false),
                                                        new Stored(
                                                                5,
                                                                1,
                                                                1,
                                                                row("WHITE-CSV", 3, null),
                                                                null,
                                                                false)));
                                for (int i = 6; i <= 60; i++)
                                    stored.add(
                                            new Stored(
                                                    i,
                                                    1,
                                                    (i % 4) + 1,
                                                    row(
                                                            powders.get(i % 4).code(),
                                                            i,
                                                            LocalDate.of(2026, 12, 24)),
                                                    null,
                                                    false));
                                view.catalogue(new OrderService.Catalogue(stored, powders));
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
                                        List.of(2L, 3L, 4L, 1L),
                                        view.table.getItems().stream()
                                                .limit(4)
                                                .map(Stored::id)
                                                .toList());
                                assertEquals(5L, view.table.getItems().getLast().id());
                                assertEquals(
                                        row.expected(),
                                        view.table.getColumns().getFirst().getCellData(overridden));
                                assertEquals(
                                        overridden.date(),
                                        view.table.getColumns().getLast().getCellData(overridden));
                                assertTrue(
                                        view.table.getHeight() > 600,
                                        "Orders must use the available window height");
                                view.table.getSelectionModel().select(ninth);

                                var imported = new ArrayList<CsvImportResult.ImportedRow<Row>>();
                                imported.add(new CsvImportResult.ImportedRow<>(2, row));
                                var aliases = new HashMap<Code, Long>();
                                for (int i = 2; i <= 24; i++) {
                                    Row value =
                                            row(
                                                    powders.get(i % 4).code(),
                                                    i,
                                                    LocalDate.of(2026, 12, 18));
                                    imported.add(new CsvImportResult.ImportedRow<>(i + 1, value));
                                    aliases.put(value.source(), powders.get(i % 4).id());
                                }
                                var preview =
                                        new Preview(
                                                new CsvImportResult<>(
                                                        24, imported, List.of(), List.of()),
                                                powders,
                                                aliases,
                                                Map.of(),
                                                List.of());
                                view.preview(preview);
                                layout(view);
                                assertTrue(view.apply.isDisabled());
                                assertTrue(
                                        view.previewPane
                                                        .localToScene(
                                                                view.previewPane.getBoundsInLocal())
                                                        .getMinX()
                                                > view.table
                                                        .localToScene(view.table.getBoundsInLocal())
                                                        .getMaxX());
                                assertTrue(view.previewPane.getHeight() > 650);
                                ComboBox<Powder> choice = choice(view);
                                assertTrue(choice.getWidth() <= 300);
                                var source = view.lookup("#orders-source-1");
                                double gap =
                                        choice.localToScene(choice.getBoundsInLocal()).getMinX()
                                                - source.localToScene(source.getBoundsInLocal())
                                                        .getMaxX();
                                assertTrue(
                                        gap < 65,
                                        "Code and target powder must stay close: gap=" + gap);
                                CheckBox unlinked = (CheckBox) view.lookup("#orders-only-unlinked");
                                unlinked.fire();
                                layout(view);
                                assertEquals(
                                        1,
                                        ((ListView<?>) view.lookup("#orders-mappings"))
                                                .getItems()
                                                .size());
                                choice(view).setValue(white);
                                assertEquals(
                                        0,
                                        ((ListView<?>) view.lookup("#orders-mappings"))
                                                .getItems()
                                                .size());
                                unlinked.fire();
                                layout(view);
                                assertFalse(view.apply.isDisabled());
                                assertEquals(1L, view.mappings().get(row.source()));
                                assertSame(ninth, view.table.getSelectionModel().getSelectedItem());
                                assertFalse(view.lookupAll(".powder-pink").isEmpty());
                                snapshot(stage, "target/orders-mappings-redesign.png");

                                TabPane previewTabs = (TabPane) view.lookup("#orders-preview-tabs");
                                previewTabs.getSelectionModel().select(0);
                                layout(view);
                                TableView<?> data =
                                        (TableView<?>) view.lookup("#orders-preview-table");
                                assertEquals(24, data.getItems().size());
                                assertTrue(data.getHeight() > 500);
                                assertTrue(
                                        view.apply
                                                        .localToScene(view.apply.getBoundsInLocal())
                                                        .getMaxY()
                                                < view.getHeight());
                                snapshot(stage, "target/orders-wide-ui.png");

                                stage.setWidth(1120);
                                view.resize(1120, 900);
                                layout(view);
                                TabPane compact = (TabPane) view.lookup("#orders-compact-tabs");
                                assertNotNull(compact);
                                assertEquals(1, compact.getSelectionModel().getSelectedIndex());
                                assertSame(preview, view.pending());
                                assertEquals(1L, view.mappings().get(row.source()));
                                assertTrue(data.getHeight() > 400);
                                assertTrue(
                                        view.apply
                                                        .localToScene(view.apply.getBoundsInLocal())
                                                        .getMaxY()
                                                < view.getHeight());
                                snapshot(stage, "target/orders-narrow-ui.png");
                                compact.getSelectionModel().select(0);
                                layout(view);
                                assertTrue(view.table.getHeight() > 400);
                                assertSame(ninth, view.table.getSelectionModel().getSelectedItem());
                                compact.getSelectionModel().select(1);
                                previewTabs.getSelectionModel().select(1);
                                layout(view);
                                assertEquals(white, choice(view).getValue());

                                stage.setWidth(1900);
                                view.resize(1900, 1000);
                                layout(view);
                                assertNull(view.lookup("#orders-compact-tabs"));
                                assertEquals(white, choice(view).getValue());
                                assertSame(preview, view.pending());
                                assertFalse(view.apply.isDisabled());
                                view.clearPreview();
                                layout(view);
                                assertNull(view.pending());
                                assertFalse(view.previewPane.isManaged());
                                assertTrue(view.table.getHeight() > 600);
                                assertSame(ninth, view.table.getSelectionModel().getSelectedItem());
                                var complete = new HashMap<>(aliases);
                                complete.put(row.source(), 1L);
                                view.preview(
                                        new Preview(
                                                preview.csv(),
                                                powders,
                                                complete,
                                                Map.of(),
                                                List.of()));
                                layout(view);
                                TabPane automatic = (TabPane) view.lookup("#orders-preview-tabs");
                                assertEquals(0, automatic.getSelectionModel().getSelectedIndex());
                                assertFalse(view.apply.isDisabled());
                                var invalidCsv =
                                        new CsvImportResult<Row>(
                                                25,
                                                imported,
                                                List.of(
                                                        new CsvImportResult.RowError(
                                                                26,
                                                                "UnitValue",
                                                                "abc",
                                                                "Prezzo non valido")),
                                                List.of());
                                view.preview(
                                        new Preview(
                                                invalidCsv,
                                                powders,
                                                complete,
                                                Map.of(),
                                                List.of()));
                                layout(view);
                                assertTrue(view.apply.isDisabled());
                                assertEquals(
                                        "Errori da correggere",
                                        automatic.getSelectionModel().getSelectedItem().getText());
                                assertTrue(
                                        ((TextArea)
                                                        automatic
                                                                .getSelectionModel()
                                                                .getSelectedItem()
                                                                .getContent())
                                                .getText()
                                                .contains("Riga 26"));
                                view.clearPreview();
                            } finally {
                                stage.close();
                            }
                            return null;
                        });
        Platform.runLater(task);
        task.get(60, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private ComboBox<Powder> choice(OrdersView view) {
        return (ComboBox<Powder>) view.lookup("#orders-mapping-1");
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
