package com.orodent.ecos.costi;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.costi.consumption.*;
import com.orodent.ecos.costi.consumption.Consumption.*;
import com.orodent.ecos.database.DatabaseSchema;
import com.orodent.ecos.database.service.*;
import com.orodent.ecos.module.*;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.*;

import org.junit.jupiter.api.*;

import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

@Tag("javafx")
class ConsumptionUiTest {
    private Stage stage;
    private ModuleSession session;
    private final List<Throwable> errors = new CopyOnWriteArrayList<>();

    @Test
    void individualPowdersColourTogglesExactDayTooltipAndResidualSimulation() throws Exception {
        String old = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/consumption-ui.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        String url =
                "jdbc:derby:memory:consumption_ui_" + UUID.randomUUID().toString().replace("-", "");
        ConnectionProvider db =
                () -> {
                    try {
                        return DriverManager.getConnection(url + ";create=true", "APP", "pw");
                    } catch (SQLException e) {
                        throw new IllegalStateException(e);
                    }
                };
        new DatabaseSchema(db).initialize();
        ProductCostFixture.seed(db);
        ConsumptionTest.seed(db);
        ProductCostFixture.execute(
                db,
                "INSERT INTO TON.POLVERI(codice,nome,colore) VALUES ('WHITE2','Seconda"
                        + " bianca','BIANCA')");
        ProductCostFixture.execute(
                db,
                "INSERT INTO TON.POLVERI_REVISIONI(polvere_id,numero_versione,stato,approvato_il)"
                    + " VALUES (3,1,'APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.COMPOSIZIONI(prodotto_id,numero_versione,stato,approvato_il)"
                    + " VALUES (1,2,'APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO TON.COMPOSIZIONE_STRATI(composizione_id,numero_strato) VALUES"
                    + " (2,1),(2,2)",
                "INSERT INTO"
                    + " TON.COMPOSIZIONE_INGREDIENTI(composizione_id,numero_strato,polvere_revisione_id,percentuale_massa)"
                    + " VALUES (2,1,4,100),(2,2,4,100)",
                "INSERT INTO"
                    + " TON.CONFIGURAZIONI_ARTICOLO(articolo_id,prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo,stato,approvato_il)"
                    + " VALUES (2,1,1,2,1,'TON-ORIGINALE','APPROVATA',CURRENT_TIMESTAMP)",
                "INSERT INTO"
                    + " TON.PRODUZIONE_RIGHE(produzione_id,numero_riga,configurazione_id,quantita_prodotta)"
                    + " VALUES (1,2,3,1)");
        var workers = Executors.newFixedThreadPool(2);
        FxTestSupport.start();
        try {
            fx(
                    () -> {
                        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> errors.add(e));
                        session =
                                new CostiModule()
                                        .open(
                                                new ModuleContext(
                                                        db,
                                                        workers,
                                                        new ClientiCsvImportService(db, workers),
                                                        new VenditeCsvImportService(db, workers),
                                                        () -> {}));
                        stage = new Stage();
                        stage.setScene(new Scene(session.root(), 1500, 1000));
                        stage.getScene()
                                .getStylesheets()
                                .add(
                                        Path.of("../orodent-app/src/main/resources/css/global.css")
                                                .toUri()
                                                .toString());
                        stage.show();
                        button("costi-nav-consumption").fire();
                        return null;
                    });
            await(
                    () ->
                            view() != null
                                    && !view().refresh.isDisabled()
                                    && view().powders.size() == 3);
            fx(
                    () -> {
                        view().from.setValue(LocalDate.of(2026, 11, 1));
                        view().through.setValue(LocalDate.of(2026, 11, 8));
                        assertTrue(view().export.isDisabled());
                        view().refresh.fire();
                        return null;
                    });
            await(() -> !view().refresh.isDisabled() && view().chart.visibleSeriesCount() == 3);
            fx(
                    () -> {
                        view().balances.getSelectionModel().selectFirst();
                        Platform.runLater(
                                () -> {
                                    for (Window w : Window.getWindows())
                                        if (w != stage
                                                && w.isShowing()
                                                && w.getScene() != null
                                                && w.getScene().getRoot()
                                                        instanceof DialogPane pane) {
                                            ((ComboBox<Unit>) pane.lookup(".combo-box"))
                                                    .setValue(Unit.GIORNI);
                                            ((TextField) pane.lookup(".text-field")).setText("15");
                                            ((Button) pane.lookupButton(ButtonType.OK)).fire();
                                            return;
                                        }
                                });
                        view().minimum.fire();
                        return null;
                    });
            await(
                    () ->
                            view().balances.getItems().stream()
                                    .anyMatch(
                                            b ->
                                                    b.powder().settings() != null
                                                            && b.powder().settings().unit()
                                                                    == Unit.GIORNI));

            fx(
                    () -> {
                        assertTrue(
                                view().chart.hoverText(LocalDate.of(2026, 11, 9)).contains("100"));
                        assertTrue(
                                view().chart
                                        .hoverText(LocalDate.of(2026, 11, 9))
                                        .contains("WHITE2"));
                        view().colors.get("BIANCA").fire();
                        assertEquals(1, view().chart.visibleSeriesCount());
                        assertFalse(
                                view().chart
                                        .hoverText(LocalDate.of(2026, 11, 9))
                                        .contains("WHITE2"));
                        view().colors.get("BIANCA").fire();
                        assertEquals(3, view().chart.visibleSeriesCount());
                        view().powders.get(3L).fire();
                        assertEquals(2, view().chart.visibleSeriesCount());
                        view().basis.setValue(Basis.CONFRONTO);
                        assertEquals(4, view().chart.visibleSeriesCount());
                        view().metric.setValue(Metric.DISPONIBILITA);
                        String text = view().chart.hoverText(LocalDate.of(2026, 11, 10));
                        assertTrue(text.contains("2026-11-10"));
                        assertTrue(text.contains("stimata"));
                        view().basis.setValue(Basis.PRODUZIONI);
                        view().tabs.getSelectionModel().select(1);
                        view().add.fire();
                        HBox row = (HBox) view().planRows.getChildren().getFirst();
                        ((TextField) row.getChildren().get(1)).setText("20");
                        view().calculate.fire();
                        return null;
                    });
            await(() -> view().planned.getItems().size() == 3 && !view().export.isDisabled());
            fx(
                    () -> {
                        assertEquals(3, view().contributions.getItems().size());
                        Balance untouched =
                                view().planned.getItems().stream()
                                        .filter(b -> b.powder().id() == 3)
                                        .findFirst()
                                        .orElseThrow();
                        assertEquals(
                                0,
                                untouched.required().signum(),
                                "Usual mix powders outside the order must participate in autonomy");
                        assertNull(untouched.current());
                        Balance white =
                                view().planned.getItems().stream()
                                        .filter(b -> b.powder().id() == 1)
                                        .findFirst()
                                        .orElseThrow();
                        assertEquals(0, ConsumptionTest.n("94.2715").compareTo(white.residual()));
                        assertTrue(white.afterDays().compareTo(white.beforeDays()) < 0);
                        view().export.fire();
                        return null;
                    });
            await(() -> exportPane() != null);
            fx(
                    () -> {
                        TableView<?> table =
                                (TableView<?>) exportPane().lookup("#csv-export-preview");
                        assertEquals(3, table.getItems().size());
                        ((Button) exportPane().lookupButton(ButtonType.CLOSE)).fire();
                        HBox row = (HBox) view().planRows.getChildren().getFirst();
                        ((TextField) row.getChildren().get(1)).setText("30");
                        assertTrue(view().export.isDisabled());
                        assertTrue(view().planned.getItems().isEmpty());
                        Platform.runLater(() -> answer(ButtonType.CANCEL));
                        button("costi-nav-products").fire();
                        assertNotNull(view());
                        Platform.runLater(() -> answer(ButtonType.OK));
                        button("costi-nav-products").fire();
                        return null;
                    });
            await(() -> stage.getScene().lookup("#costi-products-module") != null);
            assertTrue(errors.isEmpty(), errors::toString);
            fx(
                    () -> {
                        session.close();
                        session = null;
                        return null;
                    });
            assertEquals(42, workers.submit(() -> 42).get(5, TimeUnit.SECONDS));
        } finally {
            fx(
                    () -> {
                        if (session != null) session.close();
                        for (Window w : List.copyOf(Window.getWindows())) w.hide();
                        return null;
                    });
            workers.shutdown();
            assertTrue(workers.awaitTermination(15, TimeUnit.SECONDS));
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException e) {
                assertEquals("08006", e.getSQLState());
            }
            if (old == null) System.clearProperty("derby.stream.error.file");
            else System.setProperty("derby.stream.error.file", old);
        }
    }

    private ConsumptionView view() {
        return (ConsumptionView) stage.getScene().lookup("#consumption-module");
    }

    private Button button(String id) {
        return (Button) stage.getScene().lookup("#" + id);
    }

    private DialogPane exportPane() {
        for (Window w : Window.getWindows())
            if (w != stage
                    && w.isShowing()
                    && w.getScene() != null
                    && w.getScene().getRoot() instanceof DialogPane p
                    && p.lookup("#csv-export-preview") != null) return p;
        return null;
    }

    private void answer(ButtonType answer) {
        for (Window w : Window.getWindows())
            if (w != stage
                    && w.isShowing()
                    && w.getScene() != null
                    && w.getScene().getRoot() instanceof DialogPane p) {
                ((Button) p.lookupButton(answer)).fire();
                return;
            }
        fail("Conferma assente");
    }

    private <T> T fx(Callable<T> work) throws Exception {
        var future = new CompletableFuture<T>();
        Platform.runLater(
                () -> {
                    try {
                        future.complete(work.call());
                    } catch (Throwable e) {
                        future.completeExceptionally(e);
                    }
                });
        return future.get(15, TimeUnit.SECONDS);
    }

    private void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (System.nanoTime() < deadline) {
            if (fx(condition)) return;
            Thread.sleep(40);
        }
        fail(
                fx(
                        () ->
                                "Timeout consumi e fabbisogni: "
                                        + errors
                                        + " · "
                                        + (view() == null
                                                ? "view assente"
                                                : ((Label) view().lookup("#consumption-feedback"))
                                                        .getText())));
    }
}
