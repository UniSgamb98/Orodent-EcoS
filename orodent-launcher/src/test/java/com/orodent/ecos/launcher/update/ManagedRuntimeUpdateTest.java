package com.orodent.ecos.launcher.update;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;

class ManagedRuntimeUpdateTest {
    @TempDir Path temporary;
    private final KeyPair keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    private final Path bundledJava = Path.of("bundled-java/bin/java.exe").toAbsolutePath();
    private static final String RUNTIME = "windows-x64-java21-v2";
    private static final ReleaseVersion BASE = ReleaseVersion.parse("0.1.0");
    private final AtomicInteger runtimeDownloads = new AtomicInteger(),
            executedChecks = new AtomicInteger();
    private final List<Path> checkedJava = new ArrayList<>();

    ManagedRuntimeUpdateTest() throws Exception {}

    private record Release(byte[] catalog, byte[] signature, byte[] components, byte[] runtime) {}

    private Map<String, byte[]> image(String tag) {
        return new TreeMap<>(
                Map.of(
                        "release",
                        ("JAVA_VERSION=\"21.0.12\"\n").getBytes(StandardCharsets.UTF_8),
                        "bin/java.exe",
                        ("java " + tag).getBytes(),
                        "bin/server/jvm.dll",
                        ("jvm " + tag).getBytes(),
                        "lib/modules",
                        ("modules " + tag).getBytes()));
    }

    private static String sha(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private static byte[] zip(Map<String, byte[]> files) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            for (var f : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(f.getKey()));
                zip.write(f.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private Release release(String version, Map<String, byte[]> image) throws Exception {
        byte[] jar = ("app " + version).getBytes();
        String name = "orodent-app-" + version + ".jar";
        byte[] components = zip(Map.of("lib/" + name, jar)), runtime = zip(image);
        var files = new TreeMap<String, String>();
        for (var f : image.entrySet()) files.put(f.getKey(), sha(f.getValue()));
        var p = new Properties();
        p.setProperty("protocol", "2");
        p.setProperty("database.schema", "1");
        p.setProperty("version", version);
        p.setProperty("runtime", "managed");
        p.setProperty("platform", "windows-x64");
        p.setProperty("runtime.java.feature", "21");
        p.setProperty("runtime.id", RuntimeBundle.identity(files));
        p.setProperty("runtime.archive.sha256", sha(runtime));
        p.setProperty("runtime.files", String.join(",", files.keySet()));
        files.forEach((f, h) -> p.setProperty("runtime.sha256." + f, h));
        p.setProperty("files", name);
        p.setProperty("sha256." + name, sha(jar));
        p.setProperty("archive.sha256", sha(components));
        return signed(p, components, runtime);
    }

    private Release signed(Properties p, byte[] components, byte[] runtime) throws Exception {
        var out = new StringWriter();
        p.store(out, "test");
        byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate());
        signer.update(bytes);
        return new Release(bytes, Base64.getEncoder().encode(signer.sign()), components, runtime);
    }

    private Properties properties(Release r) throws Exception {
        var p = new Properties();
        p.load(new StringReader(new String(r.catalog(), StandardCharsets.UTF_8)));
        return p;
    }

    private UpdateManager manager(Release r) {
        return manager(r, bundledJava);
    }

    private UpdateManager manager(Release r, Path initialJava) {
        return new UpdateManager(
                temporary.resolve("updates"),
                keys.getPublic(),
                RUNTIME,
                initialJava,
                (url, path, limit) -> {
                    byte[] bytes;
                    if (url.endsWith("Orodent-EcoS-runtime.zip")) {
                        runtimeDownloads.incrementAndGet();
                        bytes = r.runtime();
                    } else
                        bytes =
                                url.endsWith(".sig")
                                        ? r.signature()
                                        : url.endsWith(".zip") ? r.components() : r.catalog();
                    assertTrue(bytes.length <= limit);
                    Files.write(path, bytes);
                },
                (lib, java) -> {
                    assertTrue(Files.exists(lib));
                    assertNotNull(java);
                    assertTrue(Files.isRegularFile(java));
                    checkedJava.add(java);
                    executedChecks.incrementAndGet();
                });
    }

    private UpdateManager offline() {
        return new UpdateManager(
                temporary.resolve("updates"),
                keys.getPublic(),
                RUNTIME,
                bundledJava,
                (u, p, l) -> {
                    throw new IOException("offline");
                },
                (lib, java) -> {
                    assertTrue(Files.isRegularFile(java));
                });
    }

    private Path bundled() throws Exception {
        return Files.createDirectories(temporary.resolve("installation/app/lib"));
    }

    @Test
    void selectsApplicationAndMatchingRuntimeAndReusesBothOffline() throws Exception {
        Path base = bundled();
        Path config = temporary.resolve("installation/config.properties");
        Files.writeString(config, "database.home=shared");
        var selection = manager(release("0.1.1", image("first"))).select(base, BASE);
        assertEquals(ReleaseVersion.parse("0.1.1"), selection.version());
        assertNotEquals(bundledJava, selection.javaExecutable());
        assertEquals("java first", Files.readString(selection.javaExecutable()));
        assertEquals(selection.javaExecutable(), checkedJava.getLast());
        assertEquals(selection, offline().select(base, BASE));
        assertEquals(1, runtimeDownloads.get());
        assertEquals("database.home=shared", Files.readString(config));
    }

    @Test
    void downloadsOnlyComponentsWhenRuntimeAlreadyCachedAndKeepsRunningOlderRuntime()
            throws Exception {
        Path base = bundled();
        var same = image("same");
        var first = manager(release("0.1.1", same)).select(base, BASE);
        var second = manager(release("0.1.2", same)).select(base, BASE);
        assertEquals(ReleaseVersion.parse("0.1.2"), second.version());
        assertEquals(first.javaExecutable(), second.javaExecutable());
        assertEquals(1, runtimeDownloads.get());
        var third = manager(release("0.1.3", image("new"))).select(base, BASE);
        assertNotEquals(second.javaExecutable(), third.javaExecutable());
        assertEquals("java same", Files.readString(second.javaExecutable()));
        assertTrue(Files.isDirectory(first.libraries()));
        assertEquals(2, runtimeDownloads.get());
    }

    @Test
    void corruptRuntimeNeverActivatesOrReplacesTheValidPair() throws Exception {
        Path base = bundled();
        var first = manager(release("0.1.1", image("good"))).select(base, BASE);
        var next = release("0.1.2", image("broken"));
        var updater =
                manager(
                        new Release(
                                next.catalog(),
                                next.signature(),
                                next.components(),
                                new byte[] {1}));
        assertEquals(first, updater.select(base, BASE));
        assertTrue(updater.lastFailure().contains("Checksum"));
        assertEquals(first, offline().select(base, BASE));
        assertEquals(
                2,
                executedChecks
                        .get()); // active pair revalidated once; the corrupt Java never executes
    }

    @Test
    void reusesIdenticalBundledRuntimeWithoutDownloadingJava() throws Exception {
        Path base = bundled();
        var files = image("bundled");
        Path root = Files.createDirectories(temporary.resolve("installation/runtime"));
        for (var f : files.entrySet()) {
            Path path = root.resolve(f.getKey());
            Files.createDirectories(path.getParent());
            Files.write(path, f.getValue());
        }
        Path java = root.resolve("bin/java.exe");
        var selected = manager(release("0.1.1", files), java).select(base, BASE);
        assertEquals(ReleaseVersion.parse("0.1.1"), selected.version());
        assertEquals(java, selected.javaExecutable());
        assertEquals(0, runtimeDownloads.get());
    }

    @Test
    void interruptedJavaDownloadKeepsPreviousPairAndCleansStaging() throws Exception {
        Path base = bundled();
        var first = manager(release("0.1.1", image("first"))).select(base, BASE);
        var next = release("0.1.2", image("second"));
        var updater =
                new UpdateManager(
                        temporary.resolve("updates"),
                        keys.getPublic(),
                        RUNTIME,
                        bundledJava,
                        (url, path, limit) -> {
                            if (url.endsWith("runtime.zip")) {
                                Files.write(path, new byte[] {1, 2});
                                throw new IOException("download interrupted");
                            }
                            Files.write(
                                    path,
                                    url.endsWith(".sig")
                                            ? next.signature()
                                            : url.endsWith(".zip")
                                                    ? next.components()
                                                    : next.catalog());
                        },
                        (lib, java) -> {});
        assertEquals(first, updater.select(base, BASE));
        assertEquals(first, offline().select(base, BASE));
        try (var paths = Files.walk(temporary.resolve("updates"))) {
            assertFalse(
                    paths.anyMatch(
                            p ->
                                    p.getFileName().toString().startsWith(".download-")
                                            || p.getFileName().toString().startsWith(".runtime-")));
        }
    }

    @Test
    void invalidSignatureIsRejectedBeforeRuntimeDownloadOrExecution() throws Exception {
        Path base = bundled();
        var release = release("0.1.1", image("good"));
        byte[] catalog = release.catalog().clone();
        catalog[0] ^= 1;
        assertEquals(
                base,
                manager(
                                new Release(
                                        catalog,
                                        release.signature(),
                                        release.components(),
                                        release.runtime()))
                        .select(base, BASE)
                        .libraries());
        assertEquals(0, runtimeDownloads.get());
        assertEquals(0, executedChecks.get());
        assertFalse(Files.exists(temporary.resolve("updates/active")));
    }

    @Test
    void signedTraversalCaseCollisionsAndWrongPlatformsAreRejected() throws Exception {
        Path base = bundled();
        var good = release("0.1.1", image("good"));
        for (String bad :
                List.of(
                        "../escape",
                        "bin/../escape",
                        "C:/escape",
                        "bin/CON.txt",
                        "bin/java.exe.",
                        "bin//escape")) {
            var p = properties(good);
            p.setProperty("runtime.files", p.getProperty("runtime.files") + "," + bad);
            p.setProperty("runtime.sha256." + bad, sha(new byte[] {1}));
            assertEquals(
                    base,
                    manager(signed(p, good.components(), good.runtime()))
                            .select(base, BASE)
                            .libraries());
        }
        var p = properties(good);
        p.setProperty("platform", "linux-x64");
        assertEquals(
                base,
                manager(signed(p, good.components(), good.runtime()))
                        .select(base, BASE)
                        .libraries());
        p = properties(good);
        p.setProperty("runtime.files", p.getProperty("runtime.files") + ",bin/JAVA.exe");
        p.setProperty("runtime.sha256.bin/JAVA.exe", sha(new byte[] {1}));
        assertEquals(
                base,
                manager(signed(p, good.components(), good.runtime()))
                        .select(base, BASE)
                        .libraries());
        assertEquals(0, runtimeDownloads.get());
    }

    @Test
    void runtimeInventoryAndFileVersionAreCheckedEvenWithValidArchiveHash() throws Exception {
        Path base = bundled();
        var good = release("0.1.1", image("good"));
        var changed = image("good");
        changed.put("bin/java.exe", "altered".getBytes());
        byte[] corrupt = zip(changed);
        var p = properties(good);
        p.setProperty("runtime.archive.sha256", sha(corrupt));
        assertEquals(
                base,
                manager(signed(p, good.components(), corrupt)).select(base, BASE).libraries());
        assertEquals(0, executedChecks.get());
        var wrong = image("wrong-version");
        wrong.put("release", "JAVA_VERSION=\"17.0.1\"\n".getBytes());
        assertEquals(base, manager(release("0.1.2", wrong)).select(base, BASE).libraries());
        assertEquals(0, executedChecks.get());
    }

    @Test
    void failedModuleValidationLeavesPreviousVersionActive() throws Exception {
        Path base = bundled();
        var first = manager(release("0.1.1", image("first"))).select(base, BASE);
        var release = release("0.1.2", image("next"));
        UpdateManager.Transfer transfer =
                (url, path, limit) ->
                        Files.write(
                                path,
                                url.endsWith("runtime.zip")
                                        ? release.runtime()
                                        : url.endsWith(".sig")
                                                ? release.signature()
                                                : url.endsWith(".zip")
                                                        ? release.components()
                                                        : release.catalog());
        var broken =
                new UpdateManager(
                        temporary.resolve("updates"),
                        keys.getPublic(),
                        RUNTIME,
                        bundledJava,
                        transfer,
                        (lib, java) -> {
                            if (!java.equals(first.javaExecutable()))
                                throw new IOException("incompatible module graph");
                        });
        assertEquals(first, broken.select(base, BASE));
        assertEquals(first, offline().select(base, BASE));
    }

    @Test
    void tamperedCachedRuntimeIsRejectedOfflineAndRepairedWithoutOverwritingIt() throws Exception {
        Path base = bundled();
        var release = release("0.1.1", image("first"));
        var first = manager(release).select(base, BASE);
        Files.writeString(first.javaExecutable(), "tampered");
        assertEquals(base, offline().select(base, BASE).libraries());
        var repaired = manager(release).select(base, BASE);
        assertEquals(first.version(), repaired.version());
        assertNotEquals(first.javaExecutable(), repaired.javaExecutable());
        assertEquals("tampered", Files.readString(first.javaExecutable()));
        assertEquals("java first", Files.readString(repaired.javaExecutable()));
    }

    @Test
    void incompleteZipOrUnexpectedRuntimeFilesNeverActivate() throws Exception {
        Path base = bundled();
        var good = release("0.1.1", image("good"));
        for (Map<String, byte[]> entries :
                List.of(
                        Map.of("bin/java.exe", new byte[] {1}),
                        Map.of("../escape", new byte[] {1}))) {
            byte[] archive = zip(entries);
            var p = properties(good);
            p.setProperty("runtime.archive.sha256", sha(archive));
            assertEquals(
                    base,
                    manager(signed(p, good.components(), archive)).select(base, BASE).libraries());
        }
        assertFalse(Files.exists(temporary.resolve("updates/active")));
        assertEquals(0, executedChecks.get());
    }
}
