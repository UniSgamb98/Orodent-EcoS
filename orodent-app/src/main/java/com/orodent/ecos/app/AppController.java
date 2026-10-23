package com.orodent.ecos.app;

import com.orodent.ecos.app.navigation.AppNavigator;
import com.orodent.ecos.features.home.view.HomeView;
import com.orodent.ecos.ton.view.TonView;
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
        HomeView view = new HomeView(app.databaseMode());
        view.homeButton().setOnAction(event -> showHome());
        view.tonButton().setOnAction(event -> showTon());
        showView(view, () -> { });
        stage.setTitle("Orodent-EcoS - Home");
    }

    @Override
    public void showTon() {
        TonView view = new TonView();
        view.homeButton().setOnAction(event -> showHome());
        showView(view, () -> { });
        stage.setTitle("Orodent-EcoS - Ton");
    }

    private void showView(Parent root, Runnable cleanup, String... extraCss) {
        runActivePageCleanup();
        activePageCleanup = cleanup;
        if (scene == null) {
            scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
        scene.getStylesheets().setAll(globalCss);
        for (String css : extraCss) {
            scene.getStylesheets().add(Objects.requireNonNull(
                    getClass().getResource(css), "CSS non trovato: " + css).toExternalForm());
        }
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
