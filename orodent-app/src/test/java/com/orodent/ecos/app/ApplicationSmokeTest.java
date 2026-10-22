package com.orodent.ecos.app;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Run with -Pui-smoke and an available graphical or virtual display. */
@Tag("javafx")
class ApplicationSmokeTest {
    @TempDir Path databaseHome;

    @Test
    void startsSharedDatabaseAndNavigatesFromHomeToTonAndBack() throws Exception {
        Map<String, String> properties = new HashMap<>();
        for (String name : new String[]{"ton.database.home", "ton.database.port", "ton.discovery.port"}) {
            properties.put(name, System.getProperty(name));
        }
        try (ServerSocket tcp = new ServerSocket(0); DatagramSocket udp = new DatagramSocket(0)) {
            System.setProperty("ton.database.port", Integer.toString(tcp.getLocalPort()));
            System.setProperty("ton.discovery.port", Integer.toString(udp.getLocalPort()));
        }
        System.setProperty("ton.database.home", databaseHome.toString());
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            Platform.startup(() -> {
                MainApp application = new MainApp();
                Stage stage = new Stage();
                Timeline polling = new Timeline();
                polling.getKeyFrames().add(new KeyFrame(Duration.millis(100), event -> {
                    if (!"Orodent-EcoS - Home".equals(stage.getTitle())) return;
                    polling.stop();
                    try {
                        assertTrue(stage.isShowing());
                        Label status = (Label) stage.getScene().lookup("#database-status");
                        assertNotNull(status);
                        assertTrue(status.getText().contains("HOST"));
                        ((Button) stage.getScene().lookup("#open-ton-button")).fire();
                        assertEquals("Orodent-EcoS - Ton", stage.getTitle());
                        assertNotNull(stage.getScene().lookup("#ton-view"));
                        ((Button) stage.getScene().lookup("#ton-home-button")).fire();
                        assertEquals("Orodent-EcoS - Home", stage.getTitle());
                        assertNotNull(stage.getScene().lookup("#database-status"));
                        application.stop();
                        stage.close();
                        result.complete(null);
                    } catch (Throwable failure) {
                        application.stop();
                        stage.close();
                        result.completeExceptionally(failure);
                    }
                }));
                polling.setCycleCount(200);
                polling.setOnFinished(event -> {
                    application.stop();
                    stage.close();
                    result.completeExceptionally(new AssertionError("Home non pronta entro 20 secondi"));
                });
                try {
                    application.start(stage);
                    polling.play();
                } catch (Throwable failure) {
                    application.stop();
                    stage.close();
                    result.completeExceptionally(failure);
                }
            });
            result.get(30, TimeUnit.SECONDS);
        } finally {
            Platform.exit();
            properties.forEach((name, value) -> {
                if (value == null) System.clearProperty(name); else System.setProperty(name, value);
            });
        }
    }
}
