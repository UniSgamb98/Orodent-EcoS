package com.orodent.ecos.launcher.update;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Signed inventory of one immutable Windows runtime. Its identity is derived from every file. */
public record RuntimeBundle(String id, int feature, String archiveHash, Map<String, String> files) {
    public RuntimeBundle {
        files = Map.copyOf(files);
    }

    static RuntimeBundle read(Properties p, String expectedRuntime) throws Exception {
        if (!"managed".equals(p.getProperty("runtime")))
            throw new IOException("Contratto runtime non valido");
        if (!"windows-x64".equals(p.getProperty("platform"))
                || !expectedRuntime.startsWith("windows-x64-"))
            throw new IOException("Piattaforma runtime incompatibile");
        int feature;
        try {
            feature = Integer.parseInt(p.getProperty("runtime.java.feature"));
        } catch (RuntimeException invalid) {
            throw new IOException("Versione Java non valida", invalid);
        }
        if (feature < 21 || feature > 99) throw new IOException("Versione Java non supportata");
        var files = new TreeMap<String, String>();
        var folded = new HashSet<String>();
        String[] names = p.getProperty("runtime.files", "").split(",");
        if (names.length > 4096) throw new IOException("Troppi file nel runtime");
        for (String name : names) {
            if (!safePath(name) || !folded.add(name.toLowerCase(Locale.ROOT)))
                throw new IOException("Percorso runtime non valido o duplicato");
            files.put(name, UpdateCatalog.hash(p.getProperty("runtime.sha256." + name)));
        }
        if (!files.keySet()
                .containsAll(
                        Set.of("bin/java.exe", "bin/server/jvm.dll", "lib/modules", "release")))
            throw new IOException("Runtime incompleto");
        for (String name : files.keySet()) {
            String parent = name;
            while (parent.contains("/")) {
                parent = parent.substring(0, parent.lastIndexOf('/'));
                if (folded.contains(parent.toLowerCase(Locale.ROOT)))
                    throw new IOException("Percorsi runtime in conflitto");
            }
        }
        String id = UpdateCatalog.hash(p.getProperty("runtime.id"));
        if (!id.equals(identity(files))) throw new IOException("Identità runtime non valida");
        return new RuntimeBundle(
                id, feature, UpdateCatalog.hash(p.getProperty("runtime.archive.sha256")), files);
    }

    static boolean safePath(String name) {
        if (name.length() > 240
                || !name.matches("[A-Za-z0-9][A-Za-z0-9._/-]*")
                || name.endsWith("/")) return false;
        for (String part : name.split("/", -1)) {
            if (part.isEmpty()
                    || part.equals(".")
                    || part.equals("..")
                    || part.endsWith(".")
                    || part.toUpperCase(Locale.ROOT)
                            .matches("(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) return false;
        }
        return true;
    }

    public static String identity(Map<String, String> files) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        for (var entry : new TreeMap<>(files).entrySet())
            digest.update(
                    (entry.getKey() + "\n" + entry.getValue() + "\n")
                            .getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }
}
