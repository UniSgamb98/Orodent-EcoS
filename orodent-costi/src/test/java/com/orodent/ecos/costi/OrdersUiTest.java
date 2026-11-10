package com.orodent.ecos.costi;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.csv.CsvImportResult;
import com.orodent.ecos.costi.orders.*;
import com.orodent.ecos.costi.orders.PowderOrders.*;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

@Tag("javafx")
class OrdersUiTest {
    @Test
    void previewRequiresMappingAndKeepsAllOrderValuesVisible() throws Exception {
        FxTestSupport.start();
        var task =
                new FutureTask<Void>(
                        () -> {
                            Stage stage = new Stage();
                            try {
                                OrdersView view = new OrdersView();
                                stage.setScene(new Scene(view, 1280, 850));
                                stage.show();
                                Row row =
                                        new Row(
                                                new Key("2600266", "26/00034", 1),
                                                LocalDate.of(2026, 7, 14),
                                                LocalDate.of(2026, 12, 10),
                                                "SUPPLIER",
                                                "Polvere",
                                                new BigDecimal("300"),
                                                BigDecimal.ZERO,
                                                new BigDecimal("300"),
                                                new BigDecimal("55"));
                                Powder powder = new Powder(1, "TON-CODE", "Bianca 5Y");
                                Preview preview =
                                        new Preview(
                                                new CsvImportResult<>(
                                                        1,
                                                        List.of(
                                                                new CsvImportResult.ImportedRow<>(
                                                                        2, row)),
                                                        List.of(),
                                                        List.of()),
                                                List.of(powder),
                                                Map.of(),
                                                Map.of(),
                                                List.of());
                                view.preview(preview);
                                view.applyCss();
                                view.layout();
                                assertTrue(view.apply.isDisabled());
                                @SuppressWarnings("unchecked")
                                ComboBox<Powder> mapping =
                                        (ComboBox<Powder>)
                                                view.previewPane
                                                        .lookupAll(".combo-box")
                                                        .iterator()
                                                        .next();
                                mapping.setValue(powder);
                                assertFalse(view.apply.isDisabled());
                                assertEquals(1L, view.mappings().get(row.source()));
                                view.catalogue(
                                        new OrderService.Catalogue(
                                                List.of(new Stored(1, 1, 1, row, null, false)),
                                                List.of(powder)));
                                assertEquals(1, view.table.getItems().size());
                                assertTrue(view.table.getColumns().size() >= 10);
                                view.clearPreview();
                                assertNull(view.pending());
                                assertFalse(view.previewPane.isManaged());
                            } finally {
                                stage.close();
                            }
                            return null;
                        });
        Platform.runLater(task);
        task.get(20, TimeUnit.SECONDS);
    }
}
