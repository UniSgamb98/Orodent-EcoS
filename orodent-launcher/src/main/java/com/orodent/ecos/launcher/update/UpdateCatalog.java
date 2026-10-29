package com.orodent.ecos.launcher.update;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public record UpdateCatalog(
        ReleaseVersion version,
        String runtime,
        String archiveHash,
        Map<String, String> files,
        RuntimeBundle runtimeBundle) {
    public static final int MAX_CATALOG = 1_048_576;

    public UpdateCatalog(
            ReleaseVersion version, String runtime, String archiveHash, Map<String, String> files) {
        this(version, runtime, archiveHash, files, null);
    }

    // The legacy property name is frozen for launchers already installed in the field.
    // It identifies the update contract, not the application's migration counter.
    public static final String DATA_COMPATIBILITY = "1";

    public static PublicKey publicKey(String pem) throws Exception {
        String base64 =
                pem.replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");
        return KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    public static UpdateCatalog verify(
            byte[] bytes, byte[] signature, PublicKey key, String expectedRuntime)
            throws Exception {
        if (bytes.length > MAX_CATALOG || signature.length > 256)
            throw new IOException("Catalogo troppo grande");
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(key);
        verifier.update(bytes);
        if (!verifier.verify(Base64.getMimeDecoder().decode(signature)))
            throw new IOException("Firma del catalogo non valida");
        Properties data = new Properties();
        data.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
        String protocol = data.getProperty("protocol");
        if (!("1".equals(protocol) || "2".equals(protocol))
                || !DATA_COMPATIBILITY.equals(data.getProperty("database.schema")))
            throw new IOException("Release incompatibile con il contratto del launcher");
        RuntimeBundle bundle = null;
        if ("1".equals(protocol)) {
            if (bytes.length > 65536) throw new IOException("Catalogo legacy troppo grande");
            if (!expectedRuntime.equals(data.getProperty("runtime")))
                throw new IOException("Runtime incompatibile: serve un nuovo pacchetto completo");
        } else bundle = RuntimeBundle.read(data, expectedRuntime);
        verifyMigrationMetadata(data);
        String archiveHash = hash(data.getProperty("archive.sha256"));
        Map<String, String> files = new LinkedHashMap<>();
        String[] names = data.getProperty("files", "").split(",");
        if (names.length > 128) throw new IOException("Troppi file nel catalogo");
        for (String name : names) {
            if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]*\\.jar")
                    || files.put(name, hash(data.getProperty("sha256." + name))) != null)
                throw new IOException("Nome file non valido o duplicato");
        }
        if (files.keySet().stream().noneMatch(name -> name.startsWith("orodent-app-")))
            throw new IOException("Componente app assente");
        return new UpdateCatalog(
                ReleaseVersion.parse(data.getProperty("version")),
                expectedRuntime,
                archiveHash,
                Map.copyOf(files),
                bundle);
    }

    private static void verifyMigrationMetadata(Properties data) throws IOException {
        String minimum = data.getProperty("database.migration.minimum");
        String target = data.getProperty("database.migration.target");
        if (minimum == null && target == null)
            return; // Catalogs published before migration metadata existed.
        try {
            int first = Integer.parseInt(minimum);
            int last = Integer.parseInt(target);
            if (first < 1 || last < first) throw new NumberFormatException();
        } catch (NumberFormatException failure) {
            throw new IOException("Intervallo migrazioni database non valido", failure);
        }
    }

    static String hash(String value) throws IOException {
        if (value == null || !value.matches("[a-f0-9]{64}"))
            throw new IOException("Checksum mancante o non valido");
        return value;
    }
}
