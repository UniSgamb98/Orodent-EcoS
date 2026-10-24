package com.orodent.ecos.app;

import com.orodent.ecos.app.navigation.AppNavigator;
import com.orodent.ecos.features.home.view.HomeView;
import com.orodent.ecos.module.EcosModule;
import com.orodent.ecos.module.ModuleContext;
import com.orodent.ecos.module.ModuleSession;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public final class AppController implements AppNavigator {
    private static final double INITIAL_WIDTH = 1120;
    private static final double INITIAL_HEIGHT = 760;
    private final Stage stage;
    private final AppContainer app;
    private final String globalCss;
    private Scene scene;
    private Runnable activePageCleanup = () -> { };

    public AppController(Stage stage, AppContainer app, String globalCss) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.app = Objects.requireNonNull(app, "app");
        this.globalCss = Objects.requireNonNull(globalCss, "globalCss");
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
            scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
        scene.getStylesheets().setAll(globalCss);
    }

    private void runActivePageCleanup() {
        try {
            activePageCleanup.run();
        } catch (RuntimeException exception) {
            System.err.println("Errore durante la chiusura della pagina: " + exception.getMessage());
        }
    }

    public void shutdown() {
        runActivePageCleanup();
        activePageCleanup = () -> { };
        app.shutdown();
    }
}
