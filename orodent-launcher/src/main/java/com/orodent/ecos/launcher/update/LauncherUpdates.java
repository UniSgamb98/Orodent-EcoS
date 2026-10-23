package com.orodent.ecos.launcher.update;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public final class LauncherUpdates {
    private LauncherUpdates() { }
    public static ReleaseVersion bundledVersion() {
        return ReleaseVersion.parse(metadata().getProperty("version").replace("-SNAPSHOT", ""));
    }
    private static Properties metadata() {
        Properties properties = new Properties();
        try (InputStream input = LauncherUpdates.class.getResourceAsStream("/updates/launcher.properties")) {
            if (input == null) throw new IllegalStateException("Metadati launcher assenti");
            properties.load(input); return properties;
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    public static UpdateManager.Selection select(Path installation, Path config, Path userData, Path java) {
        var bundled = new UpdateManager.Selection(installation.resolve("app/lib"), bundledVersion());
        JDialog[] progress = new JDialog[1];
        try {
            Properties settings = new Properties();
            if (!Files.exists(config)) return bundled;
            try (var reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) { settings.load(reader); }
            if (!Boolean.parseBoolean(settings.getProperty("updates.enabled", "false"))) return bundled;
            // Only distributed launchers call this method; IntelliJ starts MainApp directly.
            try (InputStream input = LauncherUpdates.class.getResourceAsStream("/updates/update-public-key.pem")) {
                if (input == null) throw new IllegalStateException("Chiave pubblica aggiornamenti non configurata");
                var key = UpdateCatalog.publicKey(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                if (!GraphicsEnvironment.isHeadless()) SwingUtilities.invokeAndWait(() -> {
                    JDialog dialog = new JDialog(); progress[0] = dialog;
                    dialog.setTitle("Orodent-EcoS"); dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                    JPanel panel = new JPanel(); panel.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
                    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
                    panel.add(new JLabel("Controllo e preparazione aggiornamenti…"));
                    JProgressBar bar = new JProgressBar(); bar.setIndeterminate(true); panel.add(bar);
                    dialog.setContentPane(panel); dialog.pack(); dialog.setLocationRelativeTo(null); dialog.setVisible(true);
                });
                UpdateManager manager = new UpdateManager(userData.resolve("updates/windows-x64-schema2"), key,
                        metadata().getProperty("runtime"), new GithubTransfer(), libraries -> {
                    Path output = Files.createTempFile(userData, "runtime-check-", ".log");
                    try {
                        Process check = new ProcessBuilder(java.toString(), "--dry-run", "--module-path", libraries.toString(),
                                "--module", "com.orodent.ecos.app/com.orodent.ecos.app.MainApp")
                                .redirectErrorStream(true).redirectOutput(output.toFile()).start();
                        if (!check.waitFor(20, TimeUnit.SECONDS)) { check.destroyForcibly(); throw new java.io.IOException("Verifica runtime scaduta"); }
                        if (check.exitValue() != 0) throw new java.io.IOException("Release incompatibile con il runtime installato");
                    } finally { Files.deleteIfExists(output); }
                });
                var selected = manager.select(bundled.libraries(), bundled.version());
                if (manager.lastFailure() != null) log(userData, manager.lastFailure());
                return selected;
            }
        } catch (Exception failure) {
            log(userData, failure.getMessage()); return bundled;
        } finally {
            if (progress[0] != null) SwingUtilities.invokeLater(progress[0]::dispose);
        }
    }
    private static void log(Path userData, String message) {
        try {
            Path logs = Files.createDirectories(userData.resolve("logs"));
            Files.writeString(logs.resolve("updates.log"), java.time.Instant.now() + " " + message + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception failure) { System.err.println("Updater: " + message); }
    }
}
