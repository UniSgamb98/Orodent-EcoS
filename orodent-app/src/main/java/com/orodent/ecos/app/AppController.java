package com.orodent.ecos.app;

import com.orodent.ecos.app.navigation.AppNavigator;
import com.orodent.ecos.database.Database;
import com.orodent.ecos.features.home.view.HomeView;
import com.orodent.ecos.module.EcosModule;
import com.orodent.ecos.module.ModuleContext;
import com.orodent.ecos.module.ModuleSession;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.Objects;

public final class AppController implements AppNavigator {
    private static final double INITIAL_WIDTH = 1120;
    private static final double INITIAL_HEIGHT = 760;
    private final Stage stage;
    private final AppContainer app;
    private final String globalCss;
    private Scene scene;
    private final BorderPane shell = new BorderPane();
    private final StackPane page = new StackPane();
    private final Label connectionBanner = new Label();
    private final Runnable unsubscribeStatus;
    private boolean closed;
    private Runnable activePageCleanup = () -> { };

    public AppController(Stage stage, AppContainer app, String globalCss) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.app = Objects.requireNonNull(app, "app");
        this.globalCss = Objects.requireNonNull(globalCss, "globalCss");
        connectionBanner.setId("database-connection-banner");
        connectionBanner.setWrapText(true);
        connectionBanner.setMaxWidth(Double.MAX_VALUE);
        connectionBanner.getStyleClass().add("database-connection-banner");
        shell.setTop(connectionBanner);
        shell.setCenter(page);
        unsubscribeStatus = app.observeDatabaseStatus(status -> Platform.runLater(() -> {
            if (!closed) updateDatabaseStatus(status);
        }));
        showHome();
    }

    @Override
    public void showHome() {
        HomeView view = new HomeView(app.databaseMode(), app.modules().descriptors(), this::showModule);
        view.homeButton().setOnAction(event -> showHome());
        showView(view, () -> { });
        stage.setTitle("Orodent-EcoS - Home");
    }

    @Override
    public void showModule(String moduleId) {
        EcosModule module = app.modules().require(moduleId);
        ModuleSession session = module.open(new ModuleContext(
                app.connectionProvider(), app.backgroundExecutor(),
                app.clientiCsvImportService(), app.venditeCsvImportService(), this::showHome));
        showView(session.root(), session::close);
        stage.setTitle("Orodent-EcoS - " + module.descriptor().name());
    }

    private void showView(Parent root, Runnable cleanup) {
        runActivePageCleanup();
        activePageCleanup = cleanup;
        if (scene == null) {
            scene = new Scene(shell, INITIAL_WIDTH, INITIAL_HEIGHT);
            stage.setScene(scene);
        }
        page.getChildren().setAll(root);
        scene.getStylesheets().setAll(globalCss);
        updateDatabaseStatus(app.databaseStatus());
    }

    private void updateDatabaseStatus(Database.Status status) {
        boolean ready = status.state() == Database.State.READY;
        connectionBanner.setText(status.message());
        connectionBanner.setVisible(!ready);
        connectionBanner.setManaged(!ready);
        page.setDisable(!ready);
        if (!page.getChildren().isEmpty() && page.getChildren().getFirst() instanceof HomeView home)
            home.showDatabaseStatus(status.message());
    }

    private void runActivePageCleanup() {
        try {
            activePageCleanup.run();
        } catch (RuntimeException exception) {
            System.err.println("Errore durante la chiusura della pagina: " + exception.getMessage());
        }
    }

    public void shutdown() {
        if (closed) return;
        closed = true;
        unsubscribeStatus.run();
        runActivePageCleanup();
        activePageCleanup = () -> { };
        app.shutdown();
    }
}
