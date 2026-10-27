package com.orodent.ecos.launcher.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real publisher, the unchanged installed updater, and the downloaded DB code. */
class LegacyLauncherCompatibilityTest {
    private static final String RUNTIME = "windows-x64-java21-v1";
    private static final String PACKAGE = "com.orodent.ecos.launcher.update.";
    @TempDir Path temporary;

    @Test
    void installedLauncherUpdatesComponentsAndTheHostMigratesItsExistingDatabase() throws Exception {
        Path publisher = Files.createDirectory(temporary.resolve("publisher"));
        Path tool = Path.of("../scripts/ReleaseTool.java").toAbsolutePath().normalize();
        run(publisher, temporary.resolve("keygen.log"), List.of(tool.toString(), "keygen"));
        Path libraries = Path.of("target/distribution/app/lib").toAbsolutePath();
        Path output = publisher.resolve("release");
        ProcessBuilder signer = process(publisher, temporary.resolve("signer.log"),
                List.of(tool.toString(), "release", "0.1.4", libraries.toString(), output.toString()));
        signer.environment().put("UPDATE_SIGNING_PRIVATE_KEY",
                Files.readString(publisher.resolve(".release-keys/update-private-key.pem")));
        finish(signer, temporary.resolve("signer.log"));

        Properties catalog = new Properties();
        try (var input = Files.newInputStream(output.resolve("update.properties"))) { catalog.load(input); }
        assertEquals("1", catalog.getProperty("database.schema"), "Installed launchers must keep accepting this contract");
        assertEquals("1", catalog.getProperty("database.migration.minimum"));
        assertEquals(Integer.toString(com.orodent.ecos.database.DatabaseSchema.CURRENT_VERSION), catalog.getProperty("database.migration.target"));
        PublicKey key = UpdateCatalog.publicKey(Files.readString(publisher.resolve("config/update-public-key.pem")));
        assertEquals(ReleaseVersion.parse("0.1.4"), UpdateCatalog.verify(
                Files.readAllBytes(output.resolve("update.properties")),
                Files.readAllBytes(output.resolve("update.properties.sig")), key, RUNTIME).version());

        Path probe = copyResource("/upgrade-probe/UpgradeDatabaseProbe.java", temporary.resolve("UpgradeDatabaseProbe.java"));
        Path data = temporary.resolve("existing-database");
        run(temporary, temporary.resolve("seed.log"),
                List.of("-cp", classPath(libraries), probe.toString(), "seed", data.toString()));
        Path installed = Files.createDirectories(temporary.resolve("installed/app/lib"));
        Files.writeString(temporary.resolve("installed/application.properties"), "database.home=unchanged");

        try (var legacy = compileInstalledUpdater()) {
            Path downloaded = selectWithInstalledUpdater(legacy, installed, key, (url, destination, limit) -> {
                String file = url.endsWith(".sig") ? "update.properties.sig" : url.endsWith(".zip")
                        ? "Orodent-EcoS-components.zip" : "update.properties";
                assertTrue(Files.size(output.resolve(file)) <= limit);
                Files.copy(output.resolve(file), destination);
            });
            assertNotEquals(installed, downloaded, "The old launcher must activate the new components");
            Path discoveryProbe = copyResource("/upgrade-probe/ModuleDiscoveryProbe.java",
                    temporary.resolve("ModuleDiscoveryProbe.java"));
            Path discoveryLog = temporary.resolve("module-discovery.log");
            run(temporary, discoveryLog, List.of("--module-path", downloaded.toString(),
                    "--add-modules", "com.orodent.ecos.app",
                    "--add-exports", "com.orodent.ecos.app/com.orodent.ecos.app.modules=ALL-UNNAMED",
                    discoveryProbe.toString()));
            assertTrue(Files.readString(discoveryLog).contains("DISCOVERED statistiche,ton"));
            try (var files = Files.list(downloaded)) {
                assertTrue(files.anyMatch(path -> path.getFileName().toString().startsWith("orodent-database-")));
            }
            Path reused = selectWithInstalledUpdater(legacy, installed, key,
                    (url, destination, limit) -> { throw new java.io.IOException("offline"); });
            assertEquals(downloaded, reused, "The installed launcher must reuse the migrated release offline");
            assertEquals("database.home=unchanged", Files.readString(temporary.resolve("installed/application.properties")));

            Path log = temporary.resolve("upgrade.log");
            run(temporary, log, List.of("-cp", classPath(downloaded), probe.toString(),
                    "upgrade", data.toString(), catalog.getProperty("database.migration.target")));
            assertTrue(Files.readString(log).contains("UPGRADED " + com.orodent.ecos.database.DatabaseSchema.CURRENT_VERSION
                    + " PRESERVED CUSTOMER AND SALE 42 NEXT 43"));
            try (var snapshots = Files.list(data.resolve("backups"))) {
                List<Path> copies = snapshots.toList();
                assertEquals(1, copies.size(), "The upgraded HOST must back up the existing database");
                assertTrue(Files.isRegularFile(copies.getFirst().resolve("backup.properties")));
                assertTrue(Files.isRegularFile(copies.getFirst().resolve("OrodentEcoS/service.properties")));
            }
        }
    }

    private URLClassLoader compileInstalledUpdater() throws Exception {
        Path sources = Files.createDirectory(temporary.resolve("legacy-sources"));
        Path classes = Files.createDirectory(temporary.resolve("legacy-classes"));
        List<String> arguments = new ArrayList<>(List.of("-source", "21", "-target", "21", "-d", classes.toString()));
        for (String name : List.of("UpdateCatalog", "UpdateManager", "ReleaseVersion")) {
            arguments.add(copyResource("/legacy-launcher-v1/" + name + ".java", sources.resolve(name + ".java")).toString());
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Compatibility tests require JDK 21");
        Path log = temporary.resolve("legacy-compiler.log");
        try (var output = Files.newOutputStream(log)) {
            assertEquals(0, compiler.run(null, output, output, arguments.toArray(String[]::new)), () -> read(log));
        }
        return new URLClassLoader(new URL[]{classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }

    private Path selectWithInstalledUpdater(ClassLoader loader, Path installed, PublicKey key,
                                            UpdateManager.Transfer transfer) throws Exception {
        Class<?> managerType = loader.loadClass(PACKAGE + "UpdateManager");
        Class<?> transferType = loader.loadClass(PACKAGE + "UpdateManager$Transfer");
        Class<?> validatorType = loader.loadClass(PACKAGE + "UpdateManager$Validator");
        Class<?> versionType = loader.loadClass(PACKAGE + "ReleaseVersion");
        Object transport = Proxy.newProxyInstance(loader, new Class<?>[]{transferType}, (proxy, method, arguments) -> {
            transfer.download((String) arguments[0], (Path) arguments[1], (Long) arguments[2]);
            return null;
        });
        Object validator = Proxy.newProxyInstance(loader, new Class<?>[]{validatorType}, (proxy, method, arguments) -> {
            run(temporary, temporary.resolve("runtime-check.log"), List.of("--dry-run", "--module-path",
                    arguments[0].toString(), "--module", "com.orodent.ecos.app/com.orodent.ecos.app.MainApp"));
            return null;
        });
        Object manager = managerType.getConstructor(Path.class, PublicKey.class, String.class, transferType, validatorType)
                .newInstance(temporary.resolve("installed-updates"), key, RUNTIME, transport, validator);
        Object base = versionType.getMethod("parse", String.class).invoke(null, "0.1.3");
        Object selected = managerType.getMethod("select", Path.class, versionType).invoke(manager, installed, base);
        assertEquals("0.1.4", selected.getClass().getMethod("version").invoke(selected).toString(),
                () -> "Installed launcher rejected the update: " + failure(managerType, manager));
        return (Path) selected.getClass().getMethod("libraries").invoke(selected);
    }

    private static String failure(Class<?> type, Object manager) {
        try { return String.valueOf(type.getMethod("lastFailure").invoke(manager)); }
        catch (ReflectiveOperationException failure) { return failure.toString(); }
    }

    private Path copyResource(String resource, Path destination) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            Files.copy(input, destination);
        }
        return destination;
    }

    private static String classPath(Path libraries) throws Exception {
        try (var files = Files.list(libraries)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted().map(Path::toString)
                    .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        }
    }

    private static ProcessBuilder process(Path directory, Path log, List<String> arguments) {
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java").toString();
        List<String> command = new ArrayList<>(List.of(executable));
        command.addAll(arguments);
        return new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
    }

    private static void run(Path directory, Path log, List<String> arguments) throws Exception {
        finish(process(directory, log, arguments), log);
    }

    private static void finish(ProcessBuilder builder, Path log) throws Exception {
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(60, TimeUnit.SECONDS), () -> "Process timed out: " + read(log));
            assertEquals(0, process.exitValue(), () -> read(log));
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static String read(Path path) {
        try { return Files.readString(path); }
        catch (Exception failure) { return failure.toString(); }
    }
}
