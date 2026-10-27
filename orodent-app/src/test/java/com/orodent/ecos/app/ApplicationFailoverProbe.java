package com.orodent.ecos.app;

import com.orodent.ecos.database.Database;
import com.orodent.ecos.database.DatabaseConfiguration;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Separate application process: its JavaFX toolkit and Derby engine have real lifecycles. */
public final class ApplicationFailoverProbe {
    public static void main(String[] args) throws Exception {
        Path home = Path.of(args[1]);
        int tcp = Integer.parseInt(args[2]);
        int udp = Integer.parseInt(args[3]);
        if (args[0].equals("host")) {
            Database database = new Database(new DatabaseConfiguration(home, tcp, udp, 800));
            try {
                database.start();
                System.out.println("READY HOST");
                try (var input = new BufferedReader(new InputStreamReader(System.in))) {
                    for (String command; (command = input.readLine()) != null;) {
                        if (command.equals("STOP")) break;
                        if (command.equals("STOP_NETWORK")) {
                            networkCommand(tcp, false);
                            System.out.println("NETWORK STOPPED");
                        } else if (command.equals("START_NETWORK")) {
                            networkCommand(tcp, true);
                            System.out.println("NETWORK STARTED");
                        }
                    }
                }
            } finally { database.stop(); }
            return;
        }
        System.setProperty("ton.database.home", home.toString());
        System.setProperty("ton.database.port", Integer.toString(tcp));
        System.setProperty("ton.discovery.port", Integer.toString(udp));
        System.setProperty("ton.discovery.timeout.ms", "800");
        MainApp app = new MainApp();
        CompletableFuture<Stage> started = new CompletableFuture<>();
        Platform.startup(() -> {
            Platform.setImplicitExit(false);
            Stage stage = new Stage();
            try { app.start(stage); started.complete(stage); }
            catch (Throwable failure) { started.completeExceptionally(failure); }
        });
        Stage stage = started.get(15, TimeUnit.SECONDS);
        try {
            awaitState(stage, "CLIENT", false);
            System.out.println("READY CLIENT");
            try (var input = new BufferedReader(new InputStreamReader(System.in))) {
                for (String command; (command = input.readLine()) != null;) {
                    if (command.equals("STOP")) break;
                    switch (command) {
                        case "WAIT_OFFLINE" -> { awaitState(stage, null, true); System.out.println("UI OFFLINE DISABLED"); }
                        case "WAIT_CLIENT" -> { awaitState(stage, "CLIENT", false); System.out.println("UI ONLINE CLIENT"); }
                        case "WAIT_HOST" -> { awaitState(stage, "HOST", false); System.out.println("UI ONLINE HOST"); }
                        default -> throw new IllegalArgumentException(command);
                    }
                }
            }
        } finally {
            fx(() -> { app.stop(); stage.close(); return null; });
            Platform.exit();
        }
    }

    private static void networkCommand(int port, boolean start) throws Exception {
        // Test fixture only: the application module deliberately has no direct Derby-server API dependency.
        Class<?> control = Class.forName("org.apache.derby.drda.NetworkServerControl");
        Object server = control.getConstructor(InetAddress.class, int.class)
                .newInstance(InetAddress.getByName(start ? "0.0.0.0" : "127.0.0.1"), port);
        if (start) control.getMethod("start", java.io.PrintWriter.class).invoke(server, new Object[]{null});
        else control.getMethod("shutdown").invoke(server);
    }

    private static void awaitState(Stage stage, String role, boolean offline) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25);
        while (System.nanoTime() < deadline) {
            if (fx(() -> {
                var scene = stage.getScene();
                Label banner = (Label) scene.lookup("#database-connection-banner");
                Label status = (Label) scene.lookup("#database-status");
                var catalog = scene.lookup("#module-catalog");
                if (banner == null || status == null || catalog == null) return false;
                if (offline) return banner.isVisible() && catalog.isDisabled() && !banner.getText().isBlank();
                return !banner.isVisible() && !catalog.isDisabled() && status.getText().contains(role);
            })) return;
            Thread.sleep(50);
        }
        throw new AssertionError("UI state timeout: " + role + " offline=" + offline);
    }

    private static <T> T fx(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try { result.complete(action.call()); }
            catch (Throwable failure) { result.completeExceptionally(failure); }
        });
        return result.get(10, TimeUnit.SECONDS);
    }
}
