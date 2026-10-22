package com.orodent.ecos.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public final class MainApp extends Application {

    private AppContainer appContainer;
    private AppController appController;
    private StartupController startupController;

    @Override
    public void start(Stage stage) {
        appContainer = new AppContainer();
        StartupView startupView = new StartupView();
        Scene startupScene = new Scene(startupView, 960, 680);
        startupScene.getStylesheets().add(globalCss());

        startupController = new StartupController(
                startupView,
                appContainer,
                appContainer.backgroundExecutor(),
                () -> showApplication(stage),
                Platform::exit
        );

        stage.setMinWidth(860);
        stage.setMinHeight(620);
        stage.setTitle("Orodent-EcoS - Avvio");
        stage.setScene(startupScene);
        stage.show();
        startupController.initialize();
    }

    private void showApplication(Stage stage) {
        startupController.dispose();
        startupController = null;
        appController = new AppController(stage, appContainer, globalCss());
    }

    private String globalCss() {
        return Objects.requireNonNull(getClass().getResource("/css/global.css")).toExternalForm();
    }

    @Override
    public void stop() {
        if (startupController != null) {
            startupController.dispose();
        }
        if (appController != null) {
            appController.shutdown();
        } else if (appContainer != null) {
            appContainer.shutdown();
        }
    }
}
