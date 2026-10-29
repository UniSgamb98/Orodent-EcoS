package com.orodent.ecos.launcher.update;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Cached Java installations are immutable. Extraction never modifies a running runtime. */
final class RuntimeStore {
    static final long MAX_ARCHIVE = 512L * 1024 * 1024;
    private final Path root;

    RuntimeStore(Path root) {
        this.root = root;
    }

    Path matchingBundled(RuntimeBundle spec, Path java) {
        if (java == null || java.getParent() == null || java.getParent().getParent() == null)
            return null;
        try {
            verify(java.getParent().getParent(), spec);
            return java;
        } catch (IOException mismatch) {
            return null;
        }
    }

    Path cached(RuntimeBundle spec) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
            return null;
        try (var candidates = Files.newDirectoryStream(root, "runtime-" + spec.id() + "-*")) {
            for (Path path : candidates) {
                if (!path.getFileName()
                        .toString()
                        .matches("runtime-" + spec.id() + "-[a-f0-9-]{36}")) continue;
                try {
                    verify(path, spec);
                    return path.resolve("bin/java.exe");
                } catch (IOException corrupt) {
                    /* Preserve the directory; another process may still use it. */
                }
            }
        }
        return null;
    }

    Path prepare(RuntimeBundle spec, ReleaseVersion version, UpdateManager.Transfer transfer)
            throws Exception {
        Path present = cached(spec);
        if (present != null) return present;
        Files.createDirectories(root);
        if (Files.isSymbolicLink(root)) throw new IOException("Cartella runtime non valida");
        Path stage = Files.createTempDirectory(root, ".runtime-");
        try {
            Path archive = stage.resolve("runtime.zip");
            transfer.download(
                    UpdateManager.REPOSITORY
                            + "/releases/download/v"
                            + version
                            + "/Orodent-EcoS-runtime.zip",
                    archive,
                    MAX_ARCHIVE);
            if (!UpdateManager.sha256(archive).equals(spec.archiveHash()))
                throw new IOException("Checksum archivio Java non valido");
            Path image = Files.createDirectory(stage.resolve("image"));
            extract(archive, image, spec);
            verify(image, spec);
            // Windows does not use POSIX permission bits; this also supports Linux test fixtures.
            if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                    && !image.resolve("bin/java.exe").toFile().setExecutable(true, true))
                throw new IOException("Impossibile preparare l’eseguibile Java");
            Path target = root.resolve("runtime-" + spec.id() + "-" + UUID.randomUUID());
            Files.move(image, target, StandardCopyOption.ATOMIC_MOVE);
            return target.resolve("bin/java.exe");
        } finally {
            UpdateManager.deleteTree(stage);
        }
    }

    private static void extract(Path archive, Path image, RuntimeBundle spec) throws IOException {
        var seen = new HashSet<String>();
        long total = 0;
        try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String name = entry.getName();
                // Publisher writes file entries only, with paths already verified in the signed
                // inventory.
                if (entry.isDirectory() || !spec.files().containsKey(name) || !seen.add(name))
                    throw new IOException("File inatteso nel runtime: " + name);
                Path target = image.resolve(name);
                Files.createDirectories(target.getParent());
                try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    byte[] buffer = new byte[65536];
                    for (int n; (n = zip.read(buffer)) != -1; ) {
                        total += n;
                        if (total > MAX_ARCHIVE * 2)
                            throw new IOException("Runtime espanso troppo grande");
                        output.write(buffer, 0, n);
                    }
                }
            }
        }
        if (seen.size() != spec.files().size())
            throw new IOException("Runtime scaricato incompleto");
    }

    private static void verify(Path directory, RuntimeBundle spec) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory))
            throw new IOException("Cartella runtime non valida");
        long count = 0;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path))
                    throw new IOException("Collegamento non ammesso nel runtime");
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                String relative = directory.relativize(path).toString().replace('\\', '/');
                if (!spec.files().containsKey(relative)
                        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || !UpdateManager.sha256(path).equals(spec.files().get(relative)))
                    throw new IOException("File Java corrotto: " + relative);
                count++;
            }
        }
        if (count != spec.files().size()) throw new IOException("Runtime incompleto");
        Properties release = new Properties();
        try (var in = Files.newBufferedReader(directory.resolve("release"))) {
            release.load(in);
        }
        String version = release.getProperty("JAVA_VERSION", "").replace("\"", "");
        if (!version.matches(spec.feature() + "([.+-].*)?"))
            throw new IOException("Versione del runtime non corrispondente al catalogo");
    }
}
