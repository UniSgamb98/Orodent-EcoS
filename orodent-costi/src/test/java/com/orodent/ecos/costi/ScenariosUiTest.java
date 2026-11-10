package com.orodent.ecos.costi;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.costi.scenarios.*;
import com.orodent.ecos.costi.scenarios.Scenarios.*;
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

@Tag("javafx")
class ScenariosUiTest {
    Stage stage;
    ModuleSession session;
    final List<Throwable> errors = new CopyOnWriteArrayList<>();

    @Test
    void fourLayerRecipeModelPricesAndHistoryAreEditableWithoutChangingRealData() throws Exception {
        String old = System.getProperty("derby.stream.error.file");
        System.setProperty("derby.stream.error.file", "target/scenarios-ui.log");
        Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        String url =
                "jdbc:derby:memory:scenario_ui_" + UUID.randomUUID().toString().replace("-", "");
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
                "INSERT INTO"
                    + " COSTI.PRODOTTI_PARAMETRI(prodotto_id,numero_versione,trasformazione_eur)"
                    + " VALUES (1,1,12)",
                "UPDATE TON.MODELLO_STRATI SET percentuale_altezza=25 WHERE modello_id=1",
                "INSERT INTO TON.MODELLO_STRATI(modello_id,numero_strato,percentuale_altezza)"
                        + " VALUES (1,3,25),(1,4,25)",
                "INSERT INTO TON.COMPOSIZIONE_STRATI(composizione_id,numero_strato) VALUES"
                        + " (1,3),(1,4)",
                "INSERT INTO"
                    + " TON.COMPOSIZIONE_INGREDIENTI(composizione_id,numero_strato,polvere_revisione_id,percentuale_massa)"
                    + " VALUES (1,3,1,100),(1,4,1,100)");
        var workers = Executors.newFixedThreadPool(2);
        FxTestSupport.start();
        try {
            initial(
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
                        stage.setScene(new Scene(session.root(), 1700, 1040));
                        stage.getScene()
                                .getStylesheets()
                                .add(
                                        Path.of("../orodent-app/src/main/resources/css/global.css")
                                                .toUri()
                                                .toString());
                        stage.show();
                        button("costi-nav-scenarios").fire();
                        return null;
                    });
            await(() -> view() != null && !view().create.isDisabled());
            fx(
                    () -> {
                        Platform.runLater(
                                () -> {
                                    DialogPane pane = dialog();
                                    ((TextField)
                                                    ((javafx.scene.layout.VBox) pane.getContent())
                                                            .getChildren()
                                                            .get(1))
                                            .setText("Scenario UI");
                                    ((Button) pane.lookupButton(ButtonType.OK)).fire();
                                });
                        view().create.fire();
                        return null;
                    });
            await(
                    () ->
                            view().name.getText().equals("Scenario UI")
                                    && !view().name.isDisabled()
                                    && view().preview.selected() != null);
            fx(
                    () -> {
                        view().name.setText("X".repeat(161));
                        assertEquals("Scenario UI", view().name.getText());
                        return null;
                    });
            fx(
                    () -> {
                        view().steps.getSelectionModel().select(1);
                        edit("scenarios-price-table", 0, 2, "Variazione %");
                        edit("scenarios-price-table", 0, 3, "10");
                        assertTrue(view().save.isDisabled());
                        button("scenarios-apply-prices").fire();
                        return null;
                    });
            await(
                    () ->
                            !view().name.isDisabled()
                                    && view().preview
                                                    .selected()
                                                    .after()
                                                    .nominalMixPrice()
                                                    .compareTo(new java.math.BigDecimal("119"))
                                            == 0);
            fx(
                    () -> {
                        editorTabs().getSelectionModel().select(1);
                        TableView<?> ingredients = table("scenarios-recipe-ingredients");
                        assertEquals(2, ingredients.getItems().size());
                        edit("scenarios-recipe-ingredients", 0, 1, "70");
                        edit("scenarios-recipe-ingredients", 1, 1, "30");
                        button("scenarios-apply-recipe").fire();
                        return null;
                    });
            await(
                    () ->
                            !view().name.isDisabled()
                                    && view().preview
                                                    .selected()
                                                    .after()
                                                    .nominalMixPrice()
                                                    .compareTo(new java.math.BigDecimal("116.75"))
                                            == 0);
            fx(
                    () -> {
                        editorTabs().getSelectionModel().select(2);
                        field("scenarios-model-top").setText("0");
                        field("scenarios-model-bottom").setText("0");
                        button("scenarios-apply-model").fire();
                        return null;
                    });
            await(
                    () ->
                            !view().name.isDisabled()
                                    && view().preview
                                                    .selected()
                                                    .after()
                                                    .grams()
                                                    .compareTo(new java.math.BigDecimal("251.25"))
                                            == 0);
            fx(
                    () -> {
                        snapshot("target/scenario-model-ui.png");
                        editorTabs().getSelectionModel().select(3);
                        field("scenarios-transform-common").setText("22");
                        textButton("Applica trasformazione allo scenario").fire();
                        return null;
                    });
            await(
                    () ->
                            !view().name.isDisabled()
                                    && view().preview
                                                    .selected()
                                                    .after()
                                                    .transformation()
                                                    .compareTo(new java.math.BigDecimal("22"))
                                            == 0);
            fx(
                    () -> {
                        view().save.fire();
                        return null;
                    });
            await(
                    () ->
                            !view().name.isDisabled()
                                    && view().status.getText().contains("v1")
                                    && view().status.getText().contains("salvata"));
            fx(
                    () -> {
                        view().name.setText("Scenario UI seconda");
                        view().save.fire();
                        return null;
                    });
            await(
                    () ->
                            !view().name.isDisabled()
                                    && view().status.getText().contains("v2")
                                    && view().status.getText().contains("salvata"));
            fx(
                    () -> {
                        view().preview.compareLine.fire();
                        view().comparison.weight.setValue(Weight.SALES);
                        assertEquals(1, view().comparison.table.getItems().size());
                        assertTrue(
                                view().comparison
                                        .table
                                        .getItems()
                                        .getFirst()
                                        .coverage()
                                        .contains("1/2"));
                        view().preview.compareProduct.fire();
                        assertEquals(2, view().comparison.table.getItems().size());
                        snapshot("target/scenario-comparison-ui.png");
                        view().comparison.exportArticles.fire();
                        return null;
                    });
            await(() -> exportDialog() != null);
            fx(
                    () -> {
                        var pane = exportDialog();
                        assertEquals(
                                2,
                                ((TableView<?>) pane.lookup("#csv-export-preview"))
                                        .getItems()
                                        .size());
                        ((Button) pane.lookupButton(ButtonType.CLOSE)).fire();
                        return null;
                    });
            var stored = new ScenarioService(db, workers);
            var entries = stored.list(false).join();
            assertEquals(1, entries.size());
            var loaded = stored.load(entries.getFirst()).join();
            assertEquals(2, loaded.version());
            assertEquals(4, loaded.recipes().get(1L).layers().size());
            assertEquals(0, loaded.models().get(1L).top().signum());
            assertEquals(
                    0,
                    loaded.transforms()
                            .get(1L)
                            .commonCost()
                            .compareTo(new java.math.BigDecimal("22")));
            assertEquals(2, stored.history(loaded.id()).join().size());
            assertEquals(
                    0,
                    db.withConnection(
                                    c -> {
                                        var r =
                                                c.createStatement()
                                                        .executeQuery(
                                                                "SELECT sovramateriale_superiore_mm"
                                                                        + " FROM TON.MODELLI_DISCO"
                                                                        + " WHERE modello_id=1");
                                        r.next();
                                        return r.getBigDecimal(1);
                                    })
                            .compareTo(new java.math.BigDecimal("1")));
            assertTrue(errors.isEmpty(), errors.toString());
        } finally {
            fx(
                    () -> {
                        if (session != null) session.close();
                        if (stage != null) stage.close();
                        return null;
                    });
            workers.shutdown();
            assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS));
            try {
                DriverManager.getConnection(url + ";drop=true", "APP", "pw");
            } catch (SQLException ignored) {
            }
            if (old == null) System.clearProperty("derby.stream.error.file");
            else System.setProperty("derby.stream.error.file", old);
        }
    }

    ScenariosView view() {
        return stage == null ? null : (ScenariosView) stage.getScene().lookup("#scenarios-module");
    }

    Button button(String id) {
        return (Button) stage.getScene().lookup("#" + id);
    }

    TextField field(String id) {
        return (TextField) stage.getScene().lookup("#" + id);
    }

    TableView<?> table(String id) {
        return (TableView<?>) stage.getScene().lookup("#" + id);
    }

    TabPane editorTabs() {
        return (TabPane) stage.getScene().lookup("#scenarios-editor-tabs");
    }

    Button textButton(String text) {
        return stage.getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof Button b && b.getText().equals(text))
                .map(n -> (Button) n)
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void edit(String id, int row, int column, String value) {
        TableView table = table(id);
        TableColumn col = (TableColumn) table.getColumns().get(column);
        col.getOnEditCommit()
                .handle(
                        new TableColumn.CellEditEvent(
                                table,
                                new TablePosition(table, row, col),
                                TableColumn.editCommitEvent(),
                                value));
    }

    DialogPane dialog() {
        return Window.getWindows().stream()
                .filter(
                        w ->
                                w != stage
                                        && w.isShowing()
                                        && w.getScene() != null
                                        && w.getScene().getRoot() instanceof DialogPane)
                .map(w -> (DialogPane) w.getScene().getRoot())
                .findFirst()
                .orElseThrow();
    }

    DialogPane exportDialog() {
        return Window.getWindows().stream()
                .filter(
                        w ->
                                w.isShowing()
                                        && w.getScene() != null
                                        && w.getScene().getRoot() instanceof DialogPane
                                        && w.getScene().lookup("#csv-export-preview") != null)
                .map(w -> (DialogPane) w.getScene().getRoot())
                .findFirst()
                .orElse(null);
    }

    void snapshot(String path) {
        var image = stage.getScene().snapshot(null);
        var pixels = image.getPixelReader();
        var out =
                new java.awt.image.BufferedImage(
                        (int) image.getWidth(),
                        (int) image.getHeight(),
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++)
            for (int x = 0; x < out.getWidth(); x++) out.setRGB(x, y, pixels.getArgb(x, y));
        try {
            javax.imageio.ImageIO.write(out, "png", Path.of(path).toFile());
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    <T> T initial(Callable<T> action) throws Exception {
        return fx(action, 60);
    }

    <T> T fx(Callable<T> action) throws Exception {
        return fx(action, 20);
    }

    <T> T fx(Callable<T> action, int seconds) throws Exception {
        var result = new CompletableFuture<T>();
        Platform.runLater(
                () -> {
                    try {
                        result.complete(action.call());
                    } catch (Throwable e) {
                        result.completeExceptionally(e);
                    }
                });
        return result.get(seconds, TimeUnit.SECONDS);
    }

    void await(Callable<Boolean> condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < end) {
            if (fx(condition)) return;
            Thread.sleep(60);
        }
        fail(
                "Timeout UI: "
                        + fx(() -> view() == null ? "nessuna vista" : view().feedback.getText()));
    }
}
