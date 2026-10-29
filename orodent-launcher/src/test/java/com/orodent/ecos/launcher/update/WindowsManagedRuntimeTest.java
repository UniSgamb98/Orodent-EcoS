package com.orodent.ecos.launcher.update;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;

import javax.tools.ToolProvider;

/** Real Windows Java image: publishing -> verified extraction -> selected Java -> app process. */
@EnabledOnOs(OS.WINDOWS)
class WindowsManagedRuntimeTest {
    @TempDir Path temp;

    @Test
    void executesApplicationWithTheDownloadedJavaImage() throws Exception {
        Path runtime = temp.resolve("new-runtime");
        Path log = temp.resolve("jlink.log");
        try (var output = new PrintWriter(Files.newBufferedWriter(log))) {
            var jlink = java.util.spi.ToolProvider.findFirst("jlink").orElseThrow();
            assertEquals(
                    0,
                    jlink.run(
                            output,
                            output,
                            "--add-modules",
                            "java.base,java.logging",
                            "--output",
                            runtime.toString(),
                            "--strip-debug",
                            "--no-header-files",
                            "--no-man-pages"),
                    () -> read(log));
        }
        Path lib = Files.createDirectories(temp.resolve("app/lib"));
        try (var jar =
                new JarOutputStream(
                        Files.newOutputStream(lib.resolve("orodent-database-test.jar")))) {
            jar.putNextEntry(new JarEntry("database/version.properties"));
            jar.write("minimum=1\ncurrent=6\n".getBytes());
            jar.closeEntry();
        }
        Path source = Files.createDirectories(temp.resolve("source")),
                classes = Files.createDirectories(temp.resolve("classes"));
        Path info = source.resolve("module-info.java"), main = source.resolve("MainApp.java");
        Files.writeString(info, "module com.orodent.ecos.app { requires java.logging; }");
        Files.writeString(
                main,
                "package com.orodent.ecos.app; public class MainApp { public static void"
                    + " main(String[] args) { System.out.println(\"MANAGED_JAVA_OK\"); } }");
        assertEquals(
                0,
                ToolProvider.getSystemJavaCompiler()
                        .run(
                                null,
                                null,
                                null,
                                "-d",
                                classes.toString(),
                                info.toString(),
                                main.toString()));
        try (var jar =
                        new JarOutputStream(
                                Files.newOutputStream(lib.resolve("orodent-app-test.jar")));
                var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                jar.putNextEntry(
                        new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, jar);
                jar.closeEntry();
            }
        }
        Path publisher = Files.createDirectory(temp.resolve("publisher")),
                tool = Path.of("../scripts/ReleaseTool.java").toAbsolutePath().normalize(),
                published = publisher.resolve("published");
        run(
                new ProcessBuilder(java(), tool.toString(), "keygen").directory(publisher.toFile()),
                temp.resolve("keygen.log"));
        var signer =
                new ProcessBuilder(
                                java(),
                                tool.toString(),
                                "release",
                                "0.2.0",
                                lib.toString(),
                                published.toString(),
                                runtime.toString())
                        .directory(publisher.toFile());
        signer.environment()
                .put(
                        "UPDATE_SIGNING_PRIVATE_KEY",
                        Files.readString(
                                publisher.resolve(".release-keys/update-private-key.pem")));
        run(signer, temp.resolve("signer.log"));
        PublicKey key =
                UpdateCatalog.publicKey(
                        Files.readString(publisher.resolve("config/update-public-key.pem")));
        var manager =
                new UpdateManager(
                        temp.resolve("updates"),
                        key,
                        "windows-x64-java21-v2",
                        Path.of(java()),
                        (url, path, limit) -> {
                            String name =
                                    url.endsWith(".sig")
                                            ? "update.properties.sig"
                                            : url.endsWith("runtime.zip")
                                                    ? "Orodent-EcoS-runtime.zip"
                                                    : url.endsWith(".zip")
                                                            ? "Orodent-EcoS-components.zip"
                                                            : "update.properties";
                            assertTrue(Files.size(published.resolve(name)) <= limit);
                            Files.copy(published.resolve(name), path);
                        },
                        (libraries, selectedJava) ->
                                run(
                                        new ProcessBuilder(
                                                selectedJava.toString(),
                                                "--dry-run",
                                                "--module-path",
                                                libraries.toString(),
                                                "--module",
                                                "com.orodent.ecos.app/com.orodent.ecos.app.MainApp"),
                                        temp.resolve("validation.log")));
        Path installed = Files.createDirectories(temp.resolve("installed/lib"));
        var selected = manager.select(installed, ReleaseVersion.parse("0.1.0"));
        assertEquals(ReleaseVersion.parse("0.2.0"), selected.version(), manager.lastFailure());
        assertNotEquals(Path.of(java()), selected.javaExecutable());
        run(
                new ProcessBuilder(
                        selected.javaExecutable().toString(),
                        "--module-path",
                        selected.libraries().toString(),
                        "--module",
                        "com.orodent.ecos.app/com.orodent.ecos.app.MainApp"),
                temp.resolve("application.log"));
        assertTrue(read(temp.resolve("application.log")).contains("MANAGED_JAVA_OK"));
    }

    private String java() {
        return Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
    }

    private static void run(ProcessBuilder builder, Path log) throws Exception {
        Process process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), () -> read(log));
            assertEquals(0, process.exitValue(), () -> read(log));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static String read(Path log) {
        try {
            return Files.readString(log);
        } catch (IOException failure) {
            return failure.toString();
        }
    }
}
