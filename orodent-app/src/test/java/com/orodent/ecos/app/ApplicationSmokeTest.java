package com.orodent.ecos.app;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseConfiguration;
import com.orodent.ecos.database.service.ClientiCsvImportService;
import com.orodent.ecos.database.service.ClientiImportReport;
import com.orodent.ecos.database.service.VenditeCsvImportService;
import com.orodent.ecos.database.service.VenditeImportReport;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("javafx")
class ApplicationSmokeTest {
    private static final String CUSTOMER_CODE = "20ZR1036";
    private static final String CUSTOMER_NAME = "Cliente prova integrazione";
    private static final String PRODUCT_CODE = "ZR9816MSA2";
    private static final String[] DATABASE_PROPERTIES = {
            "ton.database.home", "ton.database.port", "ton.discovery.port"
    };

    @TempDir
    Path databaseHome;

    @Test
    void importsSharedDataAndNavigatesBetweenEcosModules() throws Exception {
        Map<String, String> properties = new HashMap<>();
        for (String name : DATABASE_PROPERTIES) {
            properties.put(name, System.getProperty(name));
        }
        int databasePort;
        try (ServerSocket tcp = new ServerSocket(0); DatagramSocket udp = new DatagramSocket(0)) {
            databasePort = tcp.getLocalPort();
            System.setProperty("ton.database.port", Integer.toString(databasePort));
            System.setProperty("ton.discovery.port", Integer.toString(udp.getLocalPort()));
        }
        System.setProperty("ton.database.home", databaseHome.toString());

        CompletableFuture<ApplicationSession> started = new CompletableFuture<>();
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                ApplicationSession session = new ApplicationSession(new MainApp(), new Stage());
                try {
                    session.application().start(session.stage());
                    started.complete(session);
                } catch (Throwable failure) {
                    session.application().stop();
                    session.stage().close();
                    started.completeExceptionally(failure);
                }
            });
            ApplicationSession session = started.get(15, TimeUnit.SECONDS);
            Stage stage = session.stage();
            awaitFx(() -> {
                assertNoVisibleError(stage.getScene().getRoot());
                return "Orodent-EcoS - Home".equals(stage.getTitle());
            }, "La Home non è pronta dopo l'avvio del database");

            runFx(() -> {
                assertTrue(stage.isShowing());
                assertHome(stage);
                button(stage, "open-ton-button").fire();
                assertEquals("Orodent-EcoS - Ton", stage.getTitle());
                assertNotNull(stage.getScene().lookup("#ton-view"));
                button(stage, "ton-home-button").fire();
                assertHome(stage);
            });

            // Import the same CSV formats used by the application, away from the FX thread.
            importRepresentativeData(databasePort);
            runFx(() -> {
                button(stage, "open-statistiche-button").fire();
                assertNotNull(stage.getScene().lookup("#statistiche-module"));
            });
            awaitDashboard(stage);

            visitAnalysis(stage, "stats-customers", "stats-customers-view", CUSTOMER_NAME, 1);
            visitCustomerDetail(stage);
            visitAnalysis(stage, "stats-products", "stats-products-view", null, 1);
            visitAnalysis(stage, "stats-discounts", "stats-discounts-view", CUSTOMER_NAME, 1);
            visitAnalysis(stage, "stats-returns", "stats-returns-view", CUSTOMER_NAME, 1);
            visitAnalysis(stage, "stats-archive", "stats-archive-view", CUSTOMER_NAME, 3);
            visitImport(stage, "stats-import-sales", "stats-import-sales-view", "Importa dati");
            visitImport(stage, "stats-import-customers", "stats-import-customers-view", "Importa anagrafica");

            runFx(() -> button(stage, "stats-dashboard-button").fire());
            awaitDashboard(stage);
            runFx(() -> {
                button(stage, "ecosystem-home-button").fire();
                assertHome(stage);
                assertNotNull(stage.getScene().lookup("#open-statistiche-button"));
            });
        } finally {
            try {
                if (started.isDone() && !started.isCompletedExceptionally()) {
                    ApplicationSession session = started.getNow(null);
                    runFx(() -> {
                        session.application().stop();
                        session.stage().close();
                    });
                }
            } finally {
                Platform.exit();
                properties.forEach((name, value) -> {
                    if (value == null) System.clearProperty(name);
                    else System.setProperty(name, value);
                });
            }
        }
    }

    private void importRepresentativeData(int databasePort) throws Exception {
        ConnectionProvider connections = () -> {
            try {
                return DriverManager.getConnection(
                        "jdbc:derby://127.0.0.1:" + databasePort + "/" + DatabaseConfiguration.DATABASE_NAME,
                        DatabaseConfiguration.USER, DatabaseConfiguration.PASSWORD);
            } catch (SQLException failure) {
                throw new IllegalStateException("Impossibile collegarsi al database della prova", failure);
            }
        };
        Path customers = databaseHome.resolve("clienti.csv");
        Files.writeString(customers, """
                Codice;ISO_CODE;Categoria;PriceList;Agente;MA_CustSupp_CustSuppKind;Ragione sociale
                %s;IT;CF;2024;DIRETTO;Nazionale;%s
                """.formatted(CUSTOMER_CODE, CUSTOMER_NAME));
        ClientiImportReport customerReport = new ClientiCsvImportService(connections, Runnable::run)
                .importFile(customers).get(10, TimeUnit.SECONDS);
        assertTrue(customerReport.imported(), customerReport.toString());
        assertEquals(1, customerReport.insertedRows());

        int year = Year.now().getValue() - 1;
        Path sales = databaseHome.resolve("vendite.csv");
        Files.writeString(sales, """
                Data reg.;Nr. doc.;MA_InventoryEntries_StoragePhase1;MA_InventoryReasons_Reason;MA_InventoryEntries_CustSupp;Articolo;Descrizione;Unità misura;Quantità;Divisa;Qta UM base;Valore unitario;Sconto;Imp. sconto;Importo riga;Importo Riga in Divisa Base
                15/01/%1$d;000001;MAGMOD;VEND;%2$s;%3$s;ORODENT EOS 98X16 COLOR: A2;N.;1;EUR;1;100;0;0;100;100
                16/02/%1$d;000002;MAGMOD;VEND;%2$s;%3$s;ORODENT EOS 98X16 COLOR: A2;N.;2;EUR;2;100;10;20;180;180
                17/03/%1$d;000003;MAGMOD;RESO;%2$s;%3$s;ORODENT EOS 98X16 COLOR: A2;N.;-1;EUR;-1;100;0;0;-100;-100
                """.formatted(year, CUSTOMER_CODE, PRODUCT_CODE));
        VenditeImportReport salesReport = new VenditeCsvImportService(connections, Runnable::run)
                .importFile(sales).get(10, TimeUnit.SECONDS);
        assertTrue(salesReport.imported(), salesReport.toString());
        assertEquals(3, salesReport.insertedRows());
    }

    private static void awaitDashboard(Stage stage) throws Exception {
        awaitFx(() -> {
            Parent dashboard = view(stage, "statistiche-dashboard");
            if (!loaded(dashboard)) return false;
            String revenue = NumberFormat.getCurrencyInstance(Locale.ITALY).format(new BigDecimal("180"));
            return tableContains(dashboard, CUSTOMER_NAME)
                    && tableContains(dashboard, PRODUCT_CODE)
                    && dashboard.lookupAll(".dashboard-metric-value").stream()
                    .filter(Label.class::isInstance).map(Label.class::cast)
                    .anyMatch(label -> revenue.equals(label.getText()));
        }, "La dashboard non mostra i clienti, i prodotti e il fatturato netto importati");
    }

    private static void visitAnalysis(Stage stage, String menuId, String viewId,
                                      String expectedValue, int expectedRows) throws Exception {
        runFx(() -> menuItem(stage, menuId).fire());
        awaitFx(() -> {
            Parent page = view(stage, viewId);
            return loaded(page)
                    && tables(page).anyMatch(table -> table.getItems().size() == expectedRows)
                    && (expectedValue == null || tableContains(page, expectedValue));
        }, "La pagina " + viewId + " non mostra i dati importati");
    }

    private static void visitImport(Stage stage, String menuId, String viewId,
                                    String importButtonText) throws Exception {
        runFx(() -> {
            menuItem(stage, menuId).fire();
            Parent page = view(stage, viewId);
            assertTrue(loaded(page));
            List<Button> buttons = page.lookupAll(".button").stream()
                    .filter(Button.class::isInstance).map(Button.class::cast).toList();
            Button selectFile = buttons.stream().filter(button -> "Seleziona file CSV".equals(button.getText()))
                    .findFirst().orElseThrow();
            Button importFile = buttons.stream().filter(button -> importButtonText.equals(button.getText()))
                    .findFirst().orElseThrow();
            assertFalse(selectFile.isDisabled());
            assertTrue(importFile.isDisabled(), "L'importazione deve attendere la selezione di un CSV");
        });
    }

    private static void visitCustomerDetail(Stage stage) throws Exception {
        runFx(() -> {
            Parent page = view(stage, "stats-customers-view");
            TableView<?> customers = tables(page).filter(ApplicationSmokeTest::visible)
                    .filter(table -> table.getItems().stream().anyMatch(row -> row.toString().contains(CUSTOMER_CODE)))
                    .findFirst().orElseThrow(() -> new AssertionError("Elenco clienti non trovato"));
            customers.getSelectionModel().selectFirst();
        });
        awaitFx(() -> {
            Parent page = view(stage, "stats-customers-view");
            Node tabs = page.lookup(".customer-detail-tabs");
            if (!loaded(page) || tabs == null || !visible(tabs)) return false;
            Node node = page.lookup(".customer-trend-chart");
            return node instanceof XYChart<?, ?> chart && chart.getData().stream()
                    .filter(series -> "Ordini mensili".equals(series.getName()))
                    .flatMap(series -> series.getData().stream())
                    .anyMatch(point -> point.getYValue() instanceof Number value && value.doubleValue() > 0);
        }, "Il dettaglio cliente non mostra il trend degli ordini importati");

        selectCustomerTab(stage, "Storico annuale");
        awaitFx(() -> customerChartHasPoint(stage, ".customer-yearly-chart", "Fatturato registrato",
                Integer.toString(Year.now().getValue() - 1), new BigDecimal("280")),
                "Lo storico cliente non mostra il fatturato delle vendite importate");

        selectCustomerTab(stage, "Andamento mensile");
        awaitFx(() -> customerChartHasPoint(stage, ".customer-monthly-chart",
                Integer.toString(Year.now().getValue() - 1), "feb", new BigDecimal("180")),
                "Il grafico mensile cliente non mostra le vendite di febbraio");
    }

    private static void selectCustomerTab(Stage stage, String title) throws Exception {
        runFx(() -> {
            TabPane tabs = (TabPane) view(stage, "stats-customers-view").lookup(".customer-detail-tabs");
            assertNotNull(tabs);
            tabs.getSelectionModel().select(tabs.getTabs().stream()
                    .filter(tab -> title.equals(tab.getText())).findFirst()
                    .orElseThrow(() -> new AssertionError("Scheda cliente non trovata: " + title)));
        });
    }

    private static boolean customerChartHasPoint(Stage stage, String selector, String seriesName,
                                                 String category, BigDecimal expectedValue) {
        Parent page = view(stage, "stats-customers-view");
        Node node = page.lookup(selector);
        return loaded(page) && node instanceof XYChart<?, ?> chart && visible(chart)
                && chart.getData().stream().filter(series -> seriesName.equals(series.getName()))
                .flatMap(series -> series.getData().stream())
                .anyMatch(point -> category.equals(point.getXValue())
                        && point.getYValue() instanceof Number value
                        && expectedValue.compareTo(new BigDecimal(value.toString())) == 0);
    }

    private static void assertHome(Stage stage) {
        assertEquals("Orodent-EcoS - Home", stage.getTitle());
        Label status = (Label) stage.getScene().lookup("#database-status");
        assertNotNull(status);
        assertTrue(status.getText().contains("HOST"), status.getText());
    }

    private static Parent view(Stage stage, String id) {
        stage.getScene().getRoot().applyCss();
        Node node = stage.getScene().lookup("#" + id);
        assertNotNull(node, "Vista non trovata: " + id);
        return (Parent) node;
    }

    private static Button button(Stage stage, String id) {
        Button button = (Button) stage.getScene().lookup("#" + id);
        assertNotNull(button, "Pulsante non trovato: " + id);
        assertFalse(button.isDisabled(), "Pulsante disabilitato: " + id);
        return button;
    }

    private static MenuItem menuItem(Stage stage, String id) {
        return stage.getScene().getRoot().lookupAll(".menu-button").stream()
                .filter(MenuButton.class::isInstance).map(MenuButton.class::cast)
                .flatMap(menu -> menu.getItems().stream()).flatMap(ApplicationSmokeTest::menuItems)
                .filter(item -> id.equals(item.getId())).findFirst()
                .orElseThrow(() -> new AssertionError("Voce di menu non trovata: " + id));
    }

    private static Stream<MenuItem> menuItems(MenuItem item) {
        return item instanceof Menu menu
                ? Stream.concat(Stream.of(item), menu.getItems().stream().flatMap(ApplicationSmokeTest::menuItems))
                : Stream.of(item);
    }

    private static boolean loaded(Parent page) {
        assertNoVisibleError(page);
        return page.lookupAll(".progress-indicator").stream().noneMatch(ApplicationSmokeTest::visible);
    }

    private static void assertNoVisibleError(Parent page) {
        for (String selector : List.of(".error-label", ".result-error")) {
            assertTrue(page.lookupAll(selector).stream().noneMatch(ApplicationSmokeTest::visible),
                    () -> "Errore nell'interfaccia: " + visibleText(page));
        }
        // Dashboard and startup failures expose a retry action beside the original message.
        assertTrue(page.lookupAll(".button").stream().filter(Button.class::isInstance)
                        .map(Button.class::cast).noneMatch(button -> visible(button) && "Riprova".equals(button.getText())),
                () -> "Caricamento fallito: " + visibleText(page));
    }

    private static String visibleText(Parent page) {
        return page.lookupAll(".label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                .filter(ApplicationSmokeTest::visible).map(Label::getText)
                .filter(text -> text != null && !text.isBlank()).toList().toString();
    }

    private static boolean visible(Node node) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible()) return false;
        }
        return true;
    }

    private static Stream<TableView<?>> tables(Parent page) {
        return page.lookupAll(".table-view").stream().filter(TableView.class::isInstance)
                .map(node -> (TableView<?>) node);
    }

    private static boolean tableContains(Parent page, String value) {
        return tables(page).flatMap(table -> table.getItems().stream())
                .anyMatch(row -> row.toString().contains(value));
    }

    private static void awaitFx(BooleanSupplier ready, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (queryFx(ready::getAsBoolean)) return;
            Thread.sleep(100);
        }
        fail(message);
    }

    private static void runFx(Runnable action) throws Exception {
        queryFx(() -> {
            action.run();
            return null;
        });
    }

    private static <T> T queryFx(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    private record ApplicationSession(MainApp application, Stage stage) { }
}
