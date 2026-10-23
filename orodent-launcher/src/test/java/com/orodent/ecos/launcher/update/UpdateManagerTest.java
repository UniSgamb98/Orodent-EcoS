package com.orodent.ecos.launcher.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

class UpdateManagerTest {
    @TempDir Path temporary;
    private final KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    private static final String RUNTIME = "windows-x64-java21-v1";
    private static final ReleaseVersion BASE = ReleaseVersion.parse("0.1.0");

    UpdateManagerTest() throws Exception { }
    private record Release(byte[] catalog, byte[] signature, byte[] archive) { }

    private Release release(String version, String runtime, String entry) throws Exception {
        return release(version, runtime, entry, UpdateCatalog.DATA_COMPATIBILITY);
    }

    private Release release(String version, String runtime, String entry, String schema) throws Exception {
        return release(version, runtime, entry, schema, null, null);
    }

    private Release release(String version, String runtime, String entry, String contract,
                            String minimumMigration, String targetMigration) throws Exception {
        byte[] content = "component bytes".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(archive)) {
            zip.putNextEntry(new ZipEntry(entry)); zip.write(content); zip.closeEntry();
        }
        byte[] zip = archive.toByteArray();
        Properties properties = new Properties();
        properties.setProperty("protocol", "1"); properties.setProperty("database.schema", contract);
        if (minimumMigration != null) properties.setProperty("database.migration.minimum", minimumMigration);
        if (targetMigration != null) properties.setProperty("database.migration.target", targetMigration);
        properties.setProperty("runtime", runtime); properties.setProperty("version", version);
        properties.setProperty("files", "orodent-app-" + version + ".jar");
        properties.setProperty("sha256.orodent-app-" + version + ".jar", sha(content));
        properties.setProperty("archive.sha256", sha(zip));
        ByteArrayOutputStream catalog = new ByteArrayOutputStream();
        properties.store(catalog, "test");
        Signature signer = Signature.getInstance("Ed25519"); signer.initSign(keys.getPrivate()); signer.update(catalog.toByteArray());
        return new Release(catalog.toByteArray(), Base64.getEncoder().encode(signer.sign()), zip);
    }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private UpdateManager manager(Release release) {
        return new UpdateManager(temporary.resolve("updates"), keys.getPublic(), RUNTIME, (url, path, limit) -> {
            byte[] bytes = url.endsWith(".sig") ? release.signature() : url.endsWith(".zip") ? release.archive() : release.catalog();
            assertTrue(bytes.length <= limit); Files.write(path, bytes);
        }, libraries -> assertTrue(Files.exists(libraries)));
    }
    private Path bundled() throws Exception { return Files.createDirectories(temporary.resolve("installation/app/lib")); }
    private UpdateManager offline() {
        return new UpdateManager(temporary.resolve("updates"), keys.getPublic(), RUNTIME,
                (url, path, limit) -> { throw new java.io.IOException("offline"); }, libraries -> { });
    }

    @Test void installsSignedReleaseAndReusesItOfflineWithoutChangingConfiguration() throws Exception {
        Path base = bundled();
        Path config = temporary.resolve("installation/application.properties"); Files.writeString(config, "database.home=original");
        var selected = manager(release("0.1.1", RUNTIME, "lib/orodent-app-0.1.1.jar")).select(base, BASE);
        assertEquals(ReleaseVersion.parse("0.1.1"), selected.version());
        assertTrue(Files.exists(selected.libraries().resolve("orodent-app-0.1.1.jar")));
        assertEquals(selected, offline().select(base, BASE));
        assertEquals("database.home=original", Files.readString(config));
        var older = manager(release("0.1.0", RUNTIME, "lib/orodent-app-0.1.0.jar")).select(base, BASE);
        assertEquals(selected, older);
    }
    @Test void rejectsTamperedCatalog() throws Exception {
        Path base = bundled(); Release good = release("0.1.1", RUNTIME, "lib/orodent-app-0.1.1.jar");
        byte[] tampered = good.catalog().clone(); tampered[0] ^= 1;
        assertEquals(base, manager(new Release(tampered, good.signature(), good.archive())).select(base, BASE).libraries());
        assertFalse(Files.exists(temporary.resolve("updates/active")));
    }
    @Test void rejectsCorruptDownload() throws Exception {
        Path base = bundled(); Release good = release("0.1.1", RUNTIME, "lib/orodent-app-0.1.1.jar");
        assertEquals(base, manager(new Release(good.catalog(), good.signature(), new byte[]{1})).select(base, BASE).libraries());
        assertFalse(Files.exists(temporary.resolve("updates/active")));
    }
    @Test void rejectsArchivePathTraversalEvenWithValidSignature() throws Exception {
        Path base = bundled();
        assertEquals(base, manager(release("0.1.1", RUNTIME, "lib/../../outside.jar")).select(base, BASE).libraries());
        assertFalse(Files.exists(temporary.resolve("outside.jar")));
    }
    @Test void rejectsDifferentRuntimeAndFallsBackWhenOffline() throws Exception {
        Path base = bundled();
        assertEquals(base, manager(release("0.1.1", "other-runtime", "lib/orodent-app-0.1.1.jar")).select(base, BASE).libraries());
        assertEquals(base, offline().select(base, BASE).libraries());
    }
    @Test void rejectsDifferentCompatibilityContractsEvenWithAValidSignature() throws Exception {
        Path base = bundled();
        var compatible = manager(release("0.1.1", RUNTIME, "lib/orodent-app-0.1.1.jar")).select(base, BASE);
        for (String schema : List.of("2", "3")) {
            var updater = manager(release("0.1.2", RUNTIME, "lib/orodent-app-0.1.2.jar", schema));
            assertEquals(compatible, updater.select(base, BASE));
            assertTrue(updater.lastFailure().contains("contratto del launcher"));
        }
    }
    @Test void ignoresCachedComponentsFromAnIncompatibleContract() throws Exception {
        Path base = bundled();
        Release old = release("0.9.0", RUNTIME, "lib/orodent-app-0.9.0.jar", "2");
        Path state = Files.createDirectories(temporary.resolve("updates"));
        String directory = "version-0.9.0-" + UUID.randomUUID();
        Path cached = Files.createDirectories(state.resolve(directory));
        Files.write(cached.resolve("update.properties"), old.catalog());
        Files.write(cached.resolve("update.properties.sig"), old.signature());
        Files.writeString(state.resolve("active"), directory);
        assertEquals(base, offline().select(base, BASE).libraries());
    }
    @Test void acceptsInternalMigrationVersionsWithoutChangingTheLauncherContract() throws Exception {
        Path base = bundled();
        for (String target : List.of("2", "3")) {
            String version = "0.1." + target;
            var updater = manager(release(version, RUNTIME, "lib/orodent-app-" + version + ".jar", "1", "1", target));
            assertEquals(ReleaseVersion.parse(version), updater.select(base, BASE).version(), updater.lastFailure());
            assertNull(updater.lastFailure());
        }
    }
    @Test void rejectsInvalidMigrationRangesInSignedCatalogs() throws Exception {
        Path base = bundled();
        for (String[] range : new String[][]{{null, "2"}, {"1", null}, {"0", "2"}, {"3", "2"}, {"1", "invalid"}}) {
            var updater = manager(release("0.1.1", RUNTIME, "lib/orodent-app-0.1.1.jar", "1", range[0], range[1]));
            assertEquals(base, updater.select(base, BASE).libraries());
            assertTrue(updater.lastFailure().contains("Intervallo migrazioni"));
            assertFalse(Files.exists(temporary.resolve("updates/active")));
        }
    }
    @Test void detectsTamperingInCachedComponents() throws Exception {
        Path base = bundled();
        var selected = manager(release("0.1.1", RUNTIME, "lib/orodent-app-0.1.1.jar")).select(base, BASE);
        Files.writeString(selected.libraries().resolve("orodent-app-0.1.1.jar"), "modified");
        assertEquals(base, offline().select(base, BASE).libraries());
    }
    @Test void doesNotActivateReleaseWhenRuntimeCheckFails() throws Exception {
        Path base = bundled(); Release good = release("0.1.1", RUNTIME, "lib/orodent-app-0.1.1.jar");
        var broken = new UpdateManager(temporary.resolve("updates"), keys.getPublic(), RUNTIME,
                (url, path, limit) -> Files.write(path, url.endsWith(".sig") ? good.signature() : url.endsWith(".zip") ? good.archive() : good.catalog()),
                libraries -> { throw new java.io.IOException("invalid module graph"); });
        assertEquals(base, broken.select(base, BASE).libraries());
        assertFalse(Files.exists(temporary.resolve("updates/active")));
    }
    @Test void numericVersionsCompareCorrectlyAndRejectTagsAndSnapshots() {
        assertTrue(ReleaseVersion.parse("0.10.0").compareTo(ReleaseVersion.parse("0.9.9")) > 0);
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.parse("v0.1.0"));
        assertThrows(IllegalArgumentException.class, () -> ReleaseVersion.parse("0.1.0-SNAPSHOT"));
    }
}
