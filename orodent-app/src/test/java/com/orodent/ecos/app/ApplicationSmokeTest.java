package com.orodent.ecos.app;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.DatabaseConfiguration;
import com.orodent.ecos.database.DatabaseMode;
import com.orodent.ecos.features.home.view.HomeView;
import com.orodent.ecos.module.ModuleDescriptor;
import com.orodent.ecos.database.service.ClientiCsvImportService;
import com.orodent.ecos.database.service.ClientiImportReport;
import com.orodent.ecos.database.service.VenditeCsvImportService;
import com.orodent.ecos.database.service.VenditeImportReport;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
                assertAdditionalModuleAppearsAutomatically();
                button(stage, "open-ton-button").fire();
                assertEquals("Orodent-EcoS - Ton", stage.getTitle());
                assertNotNull(stage.getScene().lookup("#ton-view"));
                button(stage, "ton-home-button").fire();
                assertHome(stage);
            });

            navigatesTonAreasAndProtectsAnUnsavedProduct(stage);
            managesPowderOxidesAndImmutableTechnicalVersions(stage);
            managesTonCataloguesAndVersionedSpecifications(stage, databasePort);

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
            verifiesProductSalesPace(stage);
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
                // A new visit must get a fresh module session after the old one's cleanup.
                button(stage, "open-statistiche-button").fire();
            });
            awaitDashboard(stage);
            runFx(() -> button(stage, "ecosystem-home-button").fire());
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

    private void navigatesTonAreasAndProtectsAnUnsavedProduct(Stage stage) throws Exception {
        runFx(() -> {
            button(stage, "open-ton-button").fire();
            view(stage, "ton-overview-page");
            for (String area : List.of("production", "firing", "technical", "traceability", "documents")) {
                button(stage, "ton-nav-" + area).fire();
                Node page = view(stage, "ton-" + area + "-page");
                assertTrue(buttonUnchecked(stage, "ton-nav-" + area).getPseudoClassStates().contains(
                        javafx.css.PseudoClass.getPseudoClass("selected")), "Il menu deve evidenziare la pagina reale");
                assertTrue(page.lookupAll(".label").stream().filter(Label.class::isInstance)
                        .map(Label.class::cast).anyMatch(label -> "In preparazione".equals(label.getText())));
            }
            button(stage, "ton-nav-technical").fire();
            button(stage, "ton-tech-products-button").fire();
        });
        awaitFx(() -> stage.getScene().lookup("#ton-products-table") instanceof TableView<?> table && !table.isDisabled(),
                "Il catalogo deve essere raggiungibile da Dati tecnici");
        runFx(() -> {
            assertEquals("TON › Dati tecnici › Prodotti tecnici", ((Label) stage.getScene().lookup("#ton-breadcrumb")).getText());
            button(stage,"ton-product-new").fire();
            text(stage,"ton-product-code").setText("NON SALVATO");
            Platform.runLater(() -> respondToProductDiscard(javafx.scene.control.ButtonType.NO));
            button(stage, "ton-nav-documents").fire();
            assertNotNull(stage.getScene().lookup("#ton-products-view"));
            assertEquals("NON SALVATO", ((javafx.scene.control.TextField) stage.getScene().lookup("#ton-product-code")).getText());
            assertTrue(buttonUnchecked(stage, "ton-nav-technical").getPseudoClassStates().contains(
                    javafx.css.PseudoClass.getPseudoClass("selected")));
            Platform.runLater(() -> respondToProductDiscard(javafx.scene.control.ButtonType.YES));
            button(stage, "ton-nav-documents").fire();
            view(stage, "ton-documents-page");
            assertTrue(stage.getScene().lookup("#ton-products-view") == null);
            stage.setWidth(860);
        });
        awaitFx(() -> stage.getScene().lookup("#ton-sidebar") == null,
                "Il menu deve lasciare spazio al contenuto nelle finestre strette");
        runFx(() -> {
            button(stage, "ton-menu-button").fire();
            assertNotNull(stage.getScene().lookup("#ton-sidebar"));
            button(stage, "ton-nav-firing").fire();
            view(stage, "ton-firing-page");
            assertTrue(stage.getScene().lookup("#ton-sidebar") == null);
            assertNotNull(stage.getScene().lookup("#ton-home-button"));
            stage.setWidth(1120);
        });
        awaitFx(() -> stage.getScene().lookup("#ton-sidebar") != null, "Il menu deve tornare visibile allargando la finestra");
        runFx(() -> {
            button(stage, "ton-overview-button").fire();
            view(stage, "ton-overview-page");
            button(stage, "ton-home-button").fire();
            assertHome(stage);
        });
    }

    private static void respondToProductDiscard(javafx.scene.control.ButtonType answer) {
        javafx.scene.control.DialogPane dialog = javafx.stage.Window.getWindows().stream()
                .filter(javafx.stage.Window::isShowing).filter(window -> window.getScene() != null)
                .map(window -> window.getScene().getRoot().lookup(".dialog-pane"))
                .filter(javafx.scene.control.DialogPane.class::isInstance)
                .map(javafx.scene.control.DialogPane.class::cast).findFirst().orElseThrow();
        ((Button) dialog.lookupButton(answer)).fire();
    }

    private void managesPowderOxidesAndImmutableTechnicalVersions(Stage stage) throws Exception {
        runFx(() -> {
            button(stage, "open-ton-button").fire();
            button(stage, "ton-nav-technical").fire();
            button(stage, "ton-tech-powders-button").fire();
        });
        awaitFx(() -> stage.getScene().lookup("#ton-powders-table") instanceof TableView<?> table && !table.isDisabled(),
                "Il catalogo Polveri deve caricarsi da Dati tecnici");
        runFx(() -> {
            button(stage,"ton-powder-new").fire();
            text(stage,"ton-powder-code").setText(" p001 ");
            text(stage,"ton-powder-name").setText("Polvere prova 6Y");
            assertNull(stage.getScene().lookup("#ton-powder-order"));
            button(stage,"ton-powder-save").fire();
            assertTrue(powderFeedback(stage,"Seleziona il colore"));
            ((javafx.scene.control.ComboBox<?>)stage.getScene().lookup("#ton-powder-color")).getSelectionModel().select(1);
            powderTab(stage,"Dati tecnici");
            ((javafx.scene.control.ComboBox<?>)stage.getScene().lookup("#ton-powder-class")).getEditor().setText("6Y");
            text(stage,"ton-powder-strength").setText("1200");
            powderTab(stage,"Ossidi");
            button(stage,"ton-oxide-add").fire();
        });
        awaitFx(() -> stage.getScene().lookup("#ton-oxide-code-0") instanceof javafx.scene.control.TextField,
                "Gli ossidi devono essere modificabili direttamente nella tabella");
        runFx(() -> {
            text(stage,"ton-oxide-code-0").setText("Y2O3");
            text(stage,"ton-oxide-percent-0").setText("5,150");
            var clipboard=new javafx.scene.input.ClipboardContent(); clipboard.putString("Ossido\twt%\nZrO2\t94,850");
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(clipboard);
            Platform.runLater(() -> {
                var dialog=activeDialog();
                var answer=dialog.getButtonTypes().stream().filter(type -> type.getButtonData()==javafx.scene.control.ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
                ((Button)dialog.lookupButton(answer)).fire();
            });
            button(stage,"ton-oxide-paste").fire();
            assertTrue(((Label)stage.getScene().lookup("#ton-oxides-total")).getText().contains("100,000"));
            // Saving immediately after typing must include the last cell, without pressing Enter.
            button(stage,"ton-powder-save").fire();
            assertTrue(buttonUnchecked(stage,"ton-home-button").isDisabled());
        });
        awaitFx(() -> powderFeedback(stage,"Bozza salvata"), "La polvere e gli ossidi non sono stati salvati insieme");
        runFx(() -> {
            assertEquals("P001",text(stage,"ton-powder-code").getText());
            assertEquals("5,150",oxidePercent(stage,"Y2O3").getText());
            Platform.runLater(() -> respondToProductDiscard(javafx.scene.control.ButtonType.YES));
            button(stage,"ton-powder-approve").fire();
        });
        awaitFx(() -> powderFeedback(stage,"Scheda approvata"),"L'approvazione della scheda deve completarsi");
        runFx(() -> {
            assertTrue(oxidePercent(stage,"Y2O3").isDisabled(),"La versione approvata deve essere in sola lettura");
            assertEquals("6Y",((javafx.scene.control.ComboBox<?>)stage.getScene().lookup("#ton-powder-class")).getEditor().getText());
            button(stage,"ton-powder-new-version").fire();
        });
        awaitFx(() -> powderFeedback(stage,"Nuova bozza creata"),"La nuova versione deve copiare quella approvata");
        runFx(() -> {
            assertEquals("5,150",oxidePercent(stage,"Y2O3").getText());
            assertFalse(oxidePercent(stage,"Y2O3").isDisabled());
            oxidePercent(stage,"Y2O3").setText("9,350");oxidePercent(stage,"ZRO2").setText("90,650");
            button(stage,"ton-powder-save").fire();
        });
        awaitFx(() -> powderFeedback(stage,"Bozza salvata"),"La seconda versione deve essere salvata come bozza");
        runFx(() -> {
            var versions=(javafx.scene.control.ComboBox<?>)stage.getScene().lookup("#ton-powder-version");
            assertEquals(2,versions.getItems().size());versions.getSelectionModel().select(1);
            stage.getScene().getRoot().applyCss();
            assertEquals("5,150",oxidePercent(stage,"Y2O3").getText(),"La versione approvata non deve cambiare");
            assertTrue(oxidePercent(stage,"Y2O3").isDisabled());
            versions.getSelectionModel().select(0);stage.getScene().getRoot().applyCss();
            assertEquals("9,350",oxidePercent(stage,"Y2O3").getText());
            button(stage,"ton-powders-back").fire();
        });
        awaitFx(() -> stage.getScene().lookup("#ton-powders-table") instanceof TableView<?> table && !table.isDisabled() && table.getItems().size()==1,
                "L'elenco deve conservare la scheda approvata e la nuova bozza");
        runFx(() -> {
            TableView<?> table=(TableView<?>)stage.getScene().lookup("#ton-powders-table");
            assertEquals("Gialla",table.getColumns().get(2).getCellData(0));
            assertTrue(table.getColumns().get(4).getCellData(0).toString().contains("v1 approvata"));
            assertTrue(table.getColumns().get(4).getCellData(0).toString().contains("bozza v2"));
            button(stage,"ton-home-button").fire();assertHome(stage);
            button(stage,"open-ton-button").fire();button(stage,"ton-powders-button").fire();
        });
        awaitFx(() -> stage.getScene().lookup("#ton-powders-table") instanceof TableView<?> table && !table.isDisabled() && table.getItems().size()==1,
                "Le polveri devono restare disponibili dopo la riapertura del modulo");
        runFx(() -> {
            TableView<?> table=(TableView<?>)stage.getScene().lookup("#ton-powders-table");table.getSelectionModel().select(0);
            button(stage,"ton-powder-open").fire();
            button(stage,"ton-home-button").fire();assertHome(stage);
        });
    }

    private static boolean powderFeedback(Stage stage,String expected) {
        Node feedback=stage.getScene().lookup("#ton-powder-feedback");
        return feedback instanceof Label label && label.getText().contains(expected);
    }
    private static javafx.scene.control.TextField text(Stage stage,String id) {
        stage.getScene().getRoot().applyCss();
        var field=(javafx.scene.control.TextField)stage.getScene().lookup("#"+id);
        assertNotNull(field,id);return field;
    }
    private static void powderTab(Stage stage,String title) {
        stage.getScene().getRoot().applyCss();
        TabPane tabs=(TabPane)stage.getScene().lookup("#ton-powder-tabs");
        tabs.getSelectionModel().select(tabs.getTabs().stream().filter(tab -> tab.getText().equals(title)).findFirst().orElseThrow());
        stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();
    }
    private static javafx.scene.control.TextField oxidePercent(Stage stage,String code) {
        stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();
        TableView<?> table=(TableView<?>)stage.getScene().lookup("#ton-oxides-table");
        for(int index=0;index<table.getItems().size();index++) {
            Node field=stage.getScene().lookup("#ton-oxide-code-"+index);
            if(field instanceof javafx.scene.control.TextField input && java.text.Normalizer.normalize(input.getText(),java.text.Normalizer.Form.NFKC).toUpperCase(Locale.ROOT).equals(code))
                return text(stage,"ton-oxide-percent-"+index);
        }
        throw new AssertionError("Ossido non trovato: "+code);
    }
    private static javafx.scene.control.DialogPane activeDialog() {
        return javafx.stage.Window.getWindows().stream().filter(javafx.stage.Window::isShowing).filter(window -> window.getScene()!=null)
                .map(window -> window.getScene().getRoot().lookup(".dialog-pane")).filter(javafx.scene.control.DialogPane.class::isInstance)
                .map(javafx.scene.control.DialogPane.class::cast).findFirst().orElseThrow();
    }

    private void managesTonCataloguesAndVersionedSpecifications(Stage stage, int databasePort)
            throws Exception {
        runFx(
                () -> {
                    button(stage, "open-ton-button").fire();
                    button(stage, "ton-lines-button").fire();
                });
        awaitFx(() -> catalogLoaded(stage, "lines"), "Il catalogo linee deve caricarsi");
        runFx(
                () -> {
                    button(stage, "ton-line-new").fire();
                    text(stage, "ton-line-code").setText(" zr-ms ");
                    text(stage, "ton-line-name").setText("Linea MS");
                    text(stage, "ton-line-technology").setText("Multistrato");
                    ((javafx.scene.control.ComboBox<?>)
                                    stage.getScene().lookup("#ton-line-material"))
                            .getEditor()
                            .setText("ZR");
                    button(stage, "ton-line-save").fire();
                    assertTrue(buttonUnchecked(stage, "ton-home-button").isDisabled());
                    assertTrue(buttonUnchecked(stage, "ton-nav-documents").isDisabled());
                });
        awaitFx(
                () -> catalogFeedback(stage, "line", "Anagrafica creata"),
                "La linea deve essere salvata");
        runFx(
                () -> {
                    assertEquals("ZR-MS", text(stage, "ton-line-code").getText());
                    text(stage, "ton-line-name").setText("Linea MS corretta");
                    button(stage, "ton-line-save").fire();
                });
        awaitFx(
                () -> catalogFeedback(stage, "line", "Revisione salvata"),
                "La correzione deve creare una revisione");
        runFx(() -> catalogTab(stage, "line", "Storico"));
        awaitFx(
                () ->
                        stage.getScene().lookup("#ton-line-history-table") instanceof TableView<?> t
                                && t.getItems().size() == 2,
                "Lo storico deve conservare entrambe le revisioni della linea");
        runFx(() -> catalogTab(stage, "line", "Prodotti tecnici"));
        awaitFx(
                () ->
                        stage.getScene().lookup("#ton-line-children-table")
                                        instanceof TableView<?> t
                                && !t.isDisabled(),
                "I prodotti collegati devono caricarsi");
        runFx(() -> button(stage, "ton-line-children-new").fire());
        awaitFx(
                () ->
                        stage.getScene().lookup("#ton-product-parent")
                                        instanceof javafx.scene.control.ComboBox<?> box
                                && box.getValue() != null
                                && box.getValue().toString().startsWith("ZR-MS"),
                "La nuova scheda deve ereditare la linea");
        runFx(
                () -> {
                    text(stage, "ton-product-code").setText(" zr-ms-a2 ");
                    text(stage, "ton-product-description").setText("Prodotto MS A2");
                    ((javafx.scene.control.ComboBox<?>)
                                    stage.getScene().lookup("#ton-product-color"))
                            .getEditor()
                            .setText("a2");
                    button(stage, "ton-product-save").fire();
                });
        awaitFx(
                () -> catalogFeedback(stage, "product", "Anagrafica creata"),
                "Il prodotto deve essere salvato con linea e colore");
        runFx(() -> catalogTab(stage, "product", "Articoli"));
        awaitFx(
                () ->
                        stage.getScene().lookup("#ton-product-children-table")
                                        instanceof TableView<?> t
                                && !t.isDisabled(),
                "I formati del prodotto devono caricarsi");
        runFx(() -> button(stage, "ton-product-children-new").fire());
        awaitFx(
                () ->
                        stage.getScene().lookup("#ton-article-parent")
                                        instanceof javafx.scene.control.ComboBox<?> box
                                && box.getValue() != null
                                && box.getValue().toString().startsWith("ZR-MS-A2"),
                "Il nuovo articolo deve ereditare il prodotto");
        runFx(
                () -> {
                    assertTrue(text(stage, "ton-article-inherited").getText().contains("ZR-MS"));
                    assertTrue(text(stage, "ton-article-inherited").getText().contains("A2"));
                    text(stage, "ton-article-code").setText(PRODUCT_CODE);
                    text(stage, "ton-article-description").setText("Disco MS A2");
                    text(stage, "ton-article-diameter").setText("98");
                    text(stage, "ton-article-height").setText("16");
                    button(stage, "ton-article-save").fire();
                });
        awaitFx(
                () -> catalogFeedback(stage, "article", "Anagrafica creata"),
                "L'articolo deve essere salvabile senza configurazione");
        runFx(() -> catalogTab(stage, "article", "Specifiche di produzione"));
        awaitFx(
                () ->
                        stage.getScene().lookup("#ton-article-spec-save") instanceof Button b
                                && !b.isDisabled(),
                "La sezione specifiche deve caricarsi anche senza composizioni e modelli");
        runFx(
                () -> {
                    button(stage, "ton-article-spec-save").fire();
                    assertTrue(catalogFeedback(stage, "article", "Seleziona composizione"));
                });
        seedTonTechnicalSpecifications(databasePort);
        runFx(() -> button(stage, "ton-article-spec-refresh").fire());
        awaitFx(
                () ->
                        stage.getScene().lookup("#ton-article-spec-composition")
                                        instanceof javafx.scene.control.ComboBox<?> c
                                && !c.getItems().isEmpty()
                                && !c.isDisabled(),
                "Le composizioni approvate devono essere selezionabili");
        runFx(
                () -> {
                    ((javafx.scene.control.ComboBox<?>)
                                    stage.getScene().lookup("#ton-article-spec-composition"))
                            .getSelectionModel()
                            .selectFirst();
                    ((javafx.scene.control.ComboBox<?>)
                                    stage.getScene().lookup("#ton-article-spec-model"))
                            .getSelectionModel()
                            .selectFirst();
                    text(stage, "ton-article-spec-calculation").setText("dosi-v1");
                    button(stage, "ton-article-spec-save").fire();
                });
        awaitFx(
                () -> catalogFeedback(stage, "article", "Bozza salvata"),
                "La specifica deve essere salvata in bozza");
        awaitFx(
                () -> !buttonUnchecked(stage, "ton-article-spec-approve").isDisabled(),
                "La bozza deve ricaricarsi");
        runFx(
                () -> {
                    Platform.runLater(
                            () -> respondToProductDiscard(javafx.scene.control.ButtonType.YES));
                    button(stage, "ton-article-spec-approve").fire();
                });
        awaitFx(
                () -> catalogFeedback(stage, "article", "Versione approvata"),
                "La specifica deve essere approvata");
        awaitFx(
                () ->
                        stage.getScene().lookup("#ton-article-spec-calculation")
                                        instanceof javafx.scene.control.TextField f
                                && f.isDisabled(),
                "I dati approvati devono essere in sola lettura");
        runFx(() -> button(stage, "ton-article-spec-copy").fire());
        awaitFx(
                () -> catalogFeedback(stage, "article", "Nuova bozza"),
                "La versione approvata deve essere copiabile");
        awaitFx(
                () -> !text(stage, "ton-article-spec-calculation").isDisabled(),
                "La nuova bozza deve essere modificabile");
        runFx(
                () -> {
                    text(stage, "ton-article-spec-calculation").setText("dosi-v2");
                    button(stage, "ton-article-spec-save").fire();
                });
        awaitFx(
                () -> catalogFeedback(stage, "article", "Bozza salvata"),
                "La nuova versione deve essere salvata");
        awaitFx(
                () ->
                        stage.getScene().lookup("#ton-article-spec-version")
                                        instanceof javafx.scene.control.ComboBox<?> v
                                && v.getItems().size() == 2,
                "Entrambe le versioni devono restare disponibili");
        runFx(
                () -> {
                    ((javafx.scene.control.ComboBox<?>)
                                    stage.getScene().lookup("#ton-article-spec-version"))
                            .getSelectionModel()
                            .select(1);
                    assertEquals("dosi-v1", text(stage, "ton-article-spec-calculation").getText());
                    assertTrue(text(stage, "ton-article-spec-calculation").isDisabled());
                    catalogTab(stage, "article", "Anagrafica");
                    Platform.runLater(
                            () -> respondToProductDiscard(javafx.scene.control.ButtonType.YES));
                    button(stage, "ton-article-archive").fire();
                });
        awaitFx(
                () -> catalogFeedback(stage, "article", "Stato aggiornato"),
                "L'articolo deve essere archiviato conservando le versioni");
        runFx(
                () -> {
                    button(stage, "ton-home-button").fire();
                    assertHome(stage);
                    button(stage, "open-ton-button").fire();
                    button(stage, "ton-articles-button").fire();
                });
        awaitFx(() -> catalogLoaded(stage, "articles"), "Il catalogo articoli deve riaprirsi");
        runFx(
                () ->
                        ((javafx.scene.control.CheckBox)
                                        stage.getScene().lookup("#ton-articles-include-inactive"))
                                .fire());
        awaitFx(
                () ->
                        stage.getScene().lookup("#ton-articles-table") instanceof TableView<?> t
                                && !t.isDisabled()
                                && t.getItems().size() == 1,
                "L'articolo archiviato deve essere conservato");
        runFx(
                () -> {
                    TableView<?> table =
                            (TableView<?>) stage.getScene().lookup("#ton-articles-table");
                    assertEquals(PRODUCT_CODE, table.getColumns().get(0).getCellData(0));
                    table.getSelectionModel().selectFirst();
                    button(stage, "ton-article-edit").fire();
                    button(stage, "ton-home-button").fire();
                    assertHome(stage);
                });
    }

    private void seedTonTechnicalSpecifications(int port) throws Exception {
        try (var c =
                DriverManager.getConnection(
                        "jdbc:derby://127.0.0.1:"
                                + port
                                + "/"
                                + DatabaseConfiguration.DATABASE_NAME,
                        DatabaseConfiguration.USER,
                        DatabaseConfiguration.PASSWORD)) {
            c.setAutoCommit(false);
            try (var q = c.createStatement()) {
                long product;
                try (var r =
                        q.executeQuery(
                                "SELECT prodotto_id FROM TON.PRODOTTI WHERE codice='ZR-MS-A2'")) {
                    assertTrue(r.next());
                    product = r.getLong(1);
                }
                long powder;
                try (var r =
                        q.executeQuery(
                                "SELECT r.polvere_revisione_id FROM TON.POLVERI_REVISIONI r JOIN"
                                    + " TON.POLVERI p ON p.polvere_id=r.polvere_id WHERE"
                                    + " p.codice='P001' AND r.stato='APPROVATA'")) {
                    assertTrue(r.next());
                    powder = r.getLong(1);
                }
                q.executeUpdate(
                        "INSERT INTO"
                            + " TON.COMPOSIZIONI(prodotto_id,numero_versione,stato,approvato_il)"
                            + " VALUES ("
                                + product
                                + ",1,'APPROVATA',CURRENT_TIMESTAMP)");
                long composition;
                try (var r =
                        q.executeQuery(
                                "SELECT composizione_id FROM TON.COMPOSIZIONI WHERE prodotto_id="
                                        + product)) {
                    assertTrue(r.next());
                    composition = r.getLong(1);
                }
                q.executeUpdate(
                        "INSERT INTO TON.COMPOSIZIONE_STRATI VALUES ("
                                + composition
                                + ",1,'Strato unico')");
                q.executeUpdate(
                        "INSERT INTO TON.COMPOSIZIONE_INGREDIENTI VALUES ("
                                + composition
                                + ",1,"
                                + powder
                                + ",100)");
                q.executeUpdate(
                        "INSERT INTO"
                            + " TON.MODELLI_DISCO(codice,numero_versione,diametro_pressatura_mm,sovramateriale_superiore_mm,sovramateriale_inferiore_mm,pressione_kg_cm2,grammi_per_mm,stato,approvato_il)"
                            + " VALUES"
                            + " ('SMOKE-98',1,98,1,1,1000,20,'APPROVATA',CURRENT_TIMESTAMP)");
                long model;
                try (var r =
                        q.executeQuery(
                                "SELECT modello_id FROM TON.MODELLI_DISCO WHERE"
                                    + " codice='SMOKE-98'")) {
                    assertTrue(r.next());
                    model = r.getLong(1);
                }
                q.executeUpdate("INSERT INTO TON.MODELLO_STRATI VALUES (" + model + ",1,100)");
            }
            c.commit();
        }
    }

    private static boolean catalogLoaded(Stage stage, String plural) {
        return stage.getScene().lookup("#ton-" + plural + "-table") instanceof TableView<?> t
                && !t.isDisabled();
    }

    private static boolean catalogFeedback(Stage stage, String type, String message) {
        Node n = stage.getScene().lookup("#ton-" + type + "-feedback");
        return n instanceof Label l && l.getText().contains(message);
    }

    private static void catalogTab(Stage stage, String type, String title) {
        stage.getScene().getRoot().applyCss();
        TabPane tabs = (TabPane) stage.getScene().lookup("#ton-" + type + "-tabs");
        tabs.getSelectionModel()
                .select(
                        tabs.getTabs().stream()
                                .filter(t -> t.getText().equals(title))
                                .findFirst()
                                .orElseThrow());
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
    }

    private static Button buttonUnchecked(Stage stage, String id) {
        return (Button) stage.getScene().lookup("#" + id);
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

    private static void verifiesProductSalesPace(Stage stage) throws Exception {
        awaitFx(() -> stage.getScene().lookup("#stats-product-sales-pace-toggle") instanceof javafx.scene.control.TitledPane pane
                && pane.getText().startsWith("Ritmo di vendita ·"), "Il dettaglio prodotti deve mostrare il ritmo settimanale");
        runFx(() -> ((javafx.scene.control.TitledPane) stage.getScene().lookup("#stats-product-sales-pace-toggle")).setExpanded(true));
        awaitFx(() -> {
            Node value = stage.getScene().lookup("#stats-product-median-document");
            return value instanceof Label label && "1,5".equals(label.getText());
        }, "L'acquisto tipico deve usare i due documenti positivi da 1 e 2 dischi, escludendo il reso");
        runFx(() -> {
            assertEquals("1,5", ((Label) stage.getScene().lookup("#stats-product-mean-document")).getText());
            String scope = ((Label) stage.getScene().lookup("#stats-product-sales-pace-scope")).getText();
            assertTrue(scope.contains("3 dischi · 2 documenti"), scope);
            assertTrue(scope.contains("Campione ridotto"), scope);
            ((javafx.scene.control.TitledPane) stage.getScene().lookup("#stats-product-sales-pace-toggle")).setExpanded(false);
            TabPane tabs = stage.getScene().getRoot().lookupAll(".tab-pane").stream()
                    .filter(TabPane.class::isInstance).map(TabPane.class::cast)
                    .filter(pane -> pane.getTabs().stream().anyMatch(tab -> "Varianti".equals(tab.getText())))
                    .findFirst().orElseThrow();
            tabs.getSelectionModel().select(tabs.getTabs().stream().filter(tab -> "Varianti".equals(tab.getText())).findFirst().orElseThrow());
        });
        awaitFx(() -> stage.getScene().lookup("#stats-product-variants-table") instanceof TableView<?> table
                && table.getItems().size() == 1, "Le varianti devono mostrare il ritmo di vendita");
        runFx(() -> {
            TableView<?> table = (TableView<?>) stage.getScene().lookup("#stats-product-variants-table");
            assertEquals(7, table.getColumns().size());
            assertEquals("Dischi/sett.", table.getColumns().get(1).getText());
            assertTrue(((BigDecimal) table.getColumns().get(1).getCellData(0)).signum() > 0);
            assertEquals(0, new BigDecimal("1.5").compareTo((BigDecimal) table.getColumns().get(2).getCellData(0)));
            assertEquals(0, new BigDecimal("1.5").compareTo((BigDecimal) table.getColumns().get(3).getCellData(0)));
            assertEquals(0, new BigDecimal("2").compareTo((BigDecimal) table.getColumns().get(4).getCellData(0)));
            javafx.scene.control.ComboBox<?> dimension = (javafx.scene.control.ComboBox<?>) stage.getScene().lookup("#stats-product-variant-dimension");
            for (int index = 0; index < dimension.getItems().size(); index++) {
                if ("Altezza".equals(dimension.getItems().get(index).toString())) {
                    dimension.getSelectionModel().select(index);
                    break;
                }
            }
            assertEquals("16", table.getColumns().getFirst().getCellData(0));
            assertEquals(0, new BigDecimal("1.5").compareTo((BigDecimal) table.getColumns().get(2).getCellData(0)));
        });
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
        stage.getScene().getRoot().applyCss();
        assertEquals("Orodent-EcoS - Home", stage.getTitle());
        assertNotNull(stage.getScene().lookup("#module-catalog"));
        assertNotNull(stage.getScene().lookup("#module-ton"));
        assertNotNull(stage.getScene().lookup("#module-statistiche"));
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
        stage.getScene().getRoot().applyCss();
        Button button = (Button) stage.getScene().lookup("#" + id);
        assertNotNull(button, "Pulsante non trovato: " + id);
        assertFalse(button.isDisabled(), "Pulsante disabilitato: " + id);
        return button;
    }

    private static void assertAdditionalModuleAppearsAutomatically() {
        AtomicReference<String> selected = new AtomicReference<>();
        HomeView home = new HomeView(DatabaseMode.HOST, List.of(new ModuleDescriptor(
                "ufficio", "Ufficio", "Un nuovo strumento", "M0 0h24v24H0z")), selected::set);
        new Scene(home, 860, 620);
        home.applyCss();
        assertNotNull(home.lookup("#module-ufficio"));
        Button open = (Button) home.lookup("#open-ufficio-button");
        assertNotNull(open);
        assertEquals("Apri Ufficio", open.getText());
        open.fire();
        assertEquals("ufficio", selected.get());
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
