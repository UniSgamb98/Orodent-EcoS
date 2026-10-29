package com.orodent.ecos.launcher.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.zip.ZipInputStream;

/** Signed, immutable version directories; neither configuration nor data is updated. */
public final class UpdateManager {
    public static final String REPOSITORY = "https://github.com/UniSgamb98/Orodent-EcoS";
    public static final long MAX_ARCHIVE = 256L * 1024 * 1024;
    private final Path state;
    private final PublicKey key;
    private final String runtime;
    private final Transfer transfer;
    private final RuntimeValidator validator;
    private final Path bundledJava;
    private final RuntimeStore runtimes;
    private String lastFailure;

    public String lastFailure() {
        return lastFailure;
    }

    @FunctionalInterface
    public interface Transfer {
        void download(String url, Path destination, long limit) throws Exception;
    }

    @FunctionalInterface
    public interface Validator {
        void validate(Path libraries) throws Exception;
    }

    @FunctionalInterface
    public interface RuntimeValidator {
        void validate(Path libraries, Path java) throws Exception;
    }

    public record Selection(Path libraries, ReleaseVersion version, Path javaExecutable) {
        public Selection(Path libraries, ReleaseVersion version) {
            this(libraries, version, null);
        }
    }

    public UpdateManager(
            Path state, PublicKey key, String runtime, Transfer transfer, Validator validator) {
        this(
                state,
                key,
                runtime,
                null,
                transfer,
                (libraries, java) -> validator.validate(libraries));
    }

    public UpdateManager(
            Path state,
            PublicKey key,
            String runtime,
            Path bundledJava,
            Transfer transfer,
            RuntimeValidator validator) {
        this.state = state;
        this.key = key;
        this.runtime = runtime;
        this.bundledJava = bundledJava;
        this.transfer = transfer;
        this.validator = validator;
        this.runtimes = new RuntimeStore(state.resolve("runtimes"));
    }

    public Selection select(Path bundledLibraries, ReleaseVersion bundledVersion) {
        Selection fallback = new Selection(bundledLibraries, bundledVersion, bundledJava);
        lastFailure = null;
        try {
            Files.createDirectories(state);
            try (var channel =
                            FileChannel.open(
                                    state.resolve("update.lock"),
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.WRITE);
                    var lock = channel.tryLock()) {
                if (lock == null) return active(fallback);
                fallback = active(fallback);
                Path staging = Files.createTempDirectory(state, ".download-");
                try {
                    transfer.download(
                            REPOSITORY + "/releases/latest/download/update.properties",
                            staging.resolve("update.properties"),
                            UpdateCatalog.MAX_CATALOG);
                    transfer.download(
                            REPOSITORY + "/releases/latest/download/update.properties.sig",
                            staging.resolve("update.properties.sig"),
                            256);
                    UpdateCatalog catalog = read(staging);
                    if (catalog.version().compareTo(fallback.version()) <= 0) return fallback;
                    transfer.download(
                            REPOSITORY
                                    + "/releases/download/v"
                                    + catalog.version()
                                    + "/Orodent-EcoS-components.zip",
                            staging.resolve("components.zip"),
                            MAX_ARCHIVE);
                    if (!sha256(staging.resolve("components.zip")).equals(catalog.archiveHash()))
                        throw new IOException("Checksum archivio non valido");
                    extract(staging.resolve("components.zip"), staging, catalog);
                    Files.delete(staging.resolve("components.zip"));
                    verifyFiles(staging, catalog);
                    Path selectedJava = runtimeFor(catalog, true);
                    validator.validate(staging.resolve("lib"), selectedJava);
                    Path version =
                            state.resolve(
                                    "version-"
                                            + catalog.version()
                                            + "-"
                                            + java.util.UUID.randomUUID());
                    Files.move(staging, version, StandardCopyOption.ATOMIC_MOVE);
                    Path marker = Files.createTempFile(state, ".active-", ".tmp");
                    try {
                        Files.writeString(marker, version.getFileName().toString());
                        Files.move(
                                marker,
                                state.resolve("active"),
                                StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                    } finally {
                        Files.deleteIfExists(marker);
                    }
                    return new Selection(version.resolve("lib"), catalog.version(), selectedJava);
                } finally {
                    deleteTree(staging);
                }
            }
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            lastFailure = failure.getMessage();
            System.err.println(
                    "Aggiornamento non applicato; uso la versione disponibile: " + lastFailure);
            return fallback;
        }
    }

    private Selection active(Selection bundled) {
        try {
            String name = Files.readString(state.resolve("active")).trim();
            if (!name.matches("version-[0-9]+\\.[0-9]+\\.[0-9]+-[a-f0-9-]{36}")) return bundled;
            Path directory = state.resolve(name);
            UpdateCatalog catalog = read(directory);
            verifyFiles(directory, catalog);
            if (catalog.version().compareTo(bundled.version()) > 0) {
                Path selectedJava = runtimeFor(catalog, false);
                if (catalog.runtimeBundle() != null && selectedJava == null) return bundled;
                validator.validate(directory.resolve("lib"), selectedJava);
                return new Selection(directory.resolve("lib"), catalog.version(), selectedJava);
            }
        } catch (Exception ignored) {
            /* Missing/corrupt cache must not replace the bundled installation. */
        }
        return bundled;
    }

    private Path runtimeFor(UpdateCatalog catalog, boolean download) throws Exception {
        RuntimeBundle spec = catalog.runtimeBundle();
        if (spec == null) return bundledJava;
        Path installed = runtimes.matchingBundled(spec, bundledJava);
        if (installed != null) return installed;
        return download
                ? runtimes.prepare(spec, catalog.version(), transfer)
                : runtimes.cached(spec);
    }

    private UpdateCatalog read(Path directory) throws Exception {
        if (Files.size(directory.resolve("update.properties")) > UpdateCatalog.MAX_CATALOG
                || Files.size(directory.resolve("update.properties.sig")) > 256)
            throw new IOException("Catalogo troppo grande");
        return UpdateCatalog.verify(
                Files.readAllBytes(directory.resolve("update.properties")),
                Files.readAllBytes(directory.resolve("update.properties.sig")),
                key,
                runtime);
    }

    private static void extract(Path archive, Path directory, UpdateCatalog catalog)
            throws IOException {
        Set<String> seen = new HashSet<>();
        long total = 0;
        Files.createDirectories(directory.resolve("lib"));
        try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.isDirectory() && entry.getName().equals("lib/")) continue;
                String name = entry.getName();
                if (!name.startsWith("lib/")
                        || !catalog.files().containsKey(name.substring(4))
                        || !seen.add(name))
                    throw new IOException("File inatteso nell'aggiornamento: " + name);
                try (var output =
                        Files.newOutputStream(
                                directory.resolve(name), StandardOpenOption.CREATE_NEW)) {
                    byte[] buffer = new byte[65536];
                    for (int count; (count = zip.read(buffer)) != -1; ) {
                        total += count;
                        if (total > MAX_ARCHIVE * 2)
                            throw new IOException("Archivio espanso troppo grande");
                        output.write(buffer, 0, count);
                    }
                }
            }
        }
        if (seen.size() != catalog.files().size())
            throw new IOException("Aggiornamento incompleto");
    }

    private static void verifyFiles(Path directory, UpdateCatalog catalog) throws IOException {
        try (var paths = Files.list(directory.resolve("lib"))) {
            if (paths.count() != catalog.files().size())
                throw new IOException("File aggiuntivi nel componente");
        }
        for (var file : catalog.files().entrySet()) {
            Path path = directory.resolve("lib").resolve(file.getKey());
            if (Files.isSymbolicLink(path) || !sha256(path).equals(file.getValue()))
                throw new IOException("Componente corrotto: " + file.getKey());
        }
    }

    public static String sha256(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            for (int count; (count = input.read(buffer)) != -1; ) digest.update(buffer, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                Files.delete(path);
        }
    }
}
