package com.orodent.ecos.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class EcosLauncherTest {
    @TempDir Path temporary;
    private final Path javaHome = Path.of(System.getProperty("java.home"));

    @Test
    void packagedApplicationUsesWritableUserDataUnlessConfigured() {
        Path data = temporary.resolve("user data");
        assertEquals(data.resolve("data").toString(), EcosLauncher.databaseHome(null, null, true, data));
        assertEquals("custom", EcosLauncher.databaseHome("custom", "environment", true, data));
        assertNull(EcosLauncher.databaseHome(null, "environment", true, data));
        assertNull(EcosLauncher.databaseHome(null, null, false, data));
        assertEquals(data.resolve("data").toString(), EcosLauncher.databaseHome(" ", " ", true, data));
    }

    @Test
    void reportsAnIncompleteInstallation() {
        IOException missing = assertThrows(IOException.class, () -> EcosLauncher.command(temporary, javaHome));
        assertTrue(missing.getMessage().contains("app/lib"));
    }

    @Test
    void reportsAMissingApplication() throws Exception {
        Files.createDirectories(temporary.resolve("app/lib"));
        IOException missing = assertThrows(IOException.class, () -> EcosLauncher.command(temporary, javaHome));
        assertTrue(missing.getMessage().contains("orodent-app"));
    }

    @Test
    void reportsAMissingJavaRuntime() throws Exception {
        Path libraries = Files.createDirectories(temporary.resolve("app/lib"));
        Files.createFile(libraries.resolve("orodent-app-1.jar"));
        assertThrows(IOException.class, () -> EcosLauncher.command(temporary, temporary.resolve("missing-java")));
    }

    @Test
    void startsASeparateJvmWithSpacesAndLiteralArgumentsAndReturnsItsExitCode() throws Exception {
        Path installation = Files.createDirectories(temporary.resolve("Orodent EcoS"));
        Path libraries = Files.createDirectories(installation.resolve("app/lib"));
        Path sources = Files.createDirectories(temporary.resolve("sources"));
        Path classes = Files.createDirectories(temporary.resolve("classes"));
        Path descriptor = sources.resolve("module-info.java");
        Files.writeString(descriptor, "module com.orodent.ecos.app { }");
        Path main = sources.resolve("MainApp.java");
        Files.writeString(main, """
                package com.orodent.ecos.app;
                public final class MainApp {
                    public static void main(String[] args) throws Exception {
                        java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]),
                                args[1] + "|" + java.nio.file.Path.of("").toAbsolutePath());
                        System.exit(7);
                    }
                }
                """);
        int compiled = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", classes.toString(), descriptor.toString(), main.toString());
        assertEquals(0, compiled);
        try (var jar = new JarOutputStream(Files.newOutputStream(libraries.resolve("orodent-app-1.jar")));
             var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                jar.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, jar);
                jar.closeEntry();
            }
        }
        Path result = temporary.resolve("result with spaces.txt");
        String literal = "value with spaces; $(literal)";
        List<String> command = EcosLauncher.command(installation, javaHome, result.toString(), literal);
        assertEquals(literal, command.getLast());
        assertEquals(7, EcosLauncher.launch(installation, result.toString(), literal));
        assertEquals(literal + "|" + installation.toAbsolutePath(), Files.readString(result));
    }
}
