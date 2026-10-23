package com.orodent.ecos.launcher;

import javax.swing.JOptionPane;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Bootstrap independent of JavaFX and application classes. */
public final class EcosLauncher {
    private EcosLauncher() { }

    public static void main(String[] args) {
        int status;
        try {
            Path installation = Path.of(EcosLauncher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath().getParent();
            status = launch(installation, args);
            if (status != 0) reportError("Orodent-EcoS si è chiuso con codice " + status
                    + ". Consultare l'output dell'applicazione.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            status = 1;
        } catch (Exception failure) {
            reportError("Impossibile avviare Orodent-EcoS: " + failure.getMessage());
            status = 1;
        }
        System.exit(status);
    }

    static int launch(Path installation, String... arguments) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command(installation, Path.of(System.getProperty("java.home")), arguments))
                .directory(installation.toFile())
                .inheritIO()
                .start();
        try {
            return process.waitFor();
        } catch (InterruptedException interrupted) {
            process.destroy();
            throw interrupted;
        }
    }

    static List<String> command(Path installation, Path javaHome, String... arguments) throws IOException {
        Path libraries = installation.toAbsolutePath().resolve("app/lib");
        if (!Files.isDirectory(libraries)) {
            throw new IOException("Cartella app/lib non trovata. Estrarre l'intera distribuzione, non solo il launcher.");
        }
        try (var files = Files.list(libraries)) {
            if (files.noneMatch(file -> file.getFileName().toString().startsWith("orodent-app-")
                    && file.getFileName().toString().endsWith(".jar"))) {
                throw new IOException("Il componente orodent-app non è installato.");
            }
        }
        String executable = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? "java.exe" : "java";
        Path java = javaHome.resolve("bin").resolve(executable);
        if (!Files.isRegularFile(java)) throw new IOException("Runtime Java non trovato: " + java);
        List<String> command = new ArrayList<>(List.of(java.toString()));
        for (String property : List.of("ton.database.home", "ton.database.port",
                "ton.discovery.port", "ton.discovery.timeout.ms")) {
            String value = System.getProperty(property);
            if (value != null) command.add("-D" + property + "=" + value);
        }
        command.addAll(List.of("--module-path", libraries.toString(),
                "--module", "com.orodent.ecos.app/com.orodent.ecos.app.MainApp"));
        command.addAll(List.of(arguments));
        return List.copyOf(command);
    }

    private static void reportError(String message) {
        System.err.println(message);
        if (!GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(null, message, "Orodent-EcoS", JOptionPane.ERROR_MESSAGE);
        }
    }
}
