import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.zip.*;
import java.util.jar.JarFile;

/** Run with Java 21 source-file mode. Never prints private signing material. */
public class ReleaseTool {
    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("keygen")) {
            Path privateFile = Path.of(".release-keys/update-private-key.pem");
            Path publicFile = Path.of("config/update-public-key.pem");
            if (Files.exists(privateFile) || Files.exists(publicFile)) throw new IllegalStateException("Chiave gia presente: non sovrascrivo");
            KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            Files.createDirectories(privateFile.getParent()); Files.createDirectories(publicFile.getParent());
            Files.writeString(privateFile, pem("PRIVATE KEY", pair.getPrivate().getEncoded()), StandardOpenOption.CREATE_NEW);
            try { Files.setPosixFilePermissions(privateFile, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); }
            catch (UnsupportedOperationException ignored) { /* Windows: protect the local folder with user ACLs. */ }
            Files.writeString(publicFile, pem("PUBLIC KEY", pair.getPublic().getEncoded()), StandardOpenOption.CREATE_NEW);
            System.out.println("Creata chiave pubblica: " + publicFile + "; chiave privata locale: " + privateFile);
            System.out.println("Non pubblicare la chiave privata. Conservarne un backup protetto.");
            return;
        }
        if ((args.length != 4 && args.length != 5) || !args[0].equals("release")) throw new IllegalArgumentException("Uso: keygen | release <versione> <app/lib> <output> [runtime]");
        String version = args[1];
        if (!version.matches("(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})")) throw new IllegalArgumentException("Versione non valida");
        String secret = System.getenv("UPDATE_SIGNING_PRIVATE_KEY");
        if (secret == null || secret.isBlank()) throw new IllegalStateException("Secret UPDATE_SIGNING_PRIVATE_KEY mancante");
        var factory = KeyFactory.getInstance("Ed25519");
        var privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(decode(secret, "PRIVATE KEY")));
        var publicKey = factory.generatePublic(new X509EncodedKeySpec(decode(Files.readString(Path.of("config/update-public-key.pem")), "PUBLIC KEY")));
        Path libraries = Path.of(args[2]); Path output = Files.createDirectories(Path.of(args[3]));
        List<Path> jars;
        try (var files = Files.list(libraries)) { jars = files.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList(); }
        if (jars.isEmpty() || jars.size() > 128 || jars.stream().noneMatch(p -> p.getFileName().toString().startsWith("orodent-app-")))
            throw new IllegalStateException("Componenti mancanti");
        Properties manifest = new Properties();
        boolean managedRuntime=args.length==5;
        manifest.setProperty("protocol", managedRuntime?"2":"1");
        // Keep the contract accepted by already installed launchers. Internal DB migrations
        // are handled by the application HOST and declared separately in the signed catalog.
        manifest.setProperty("database.schema", "1");
        Properties database = databaseVersion(jars);
        manifest.setProperty("database.migration.minimum", database.getProperty("minimum"));
        manifest.setProperty("database.migration.target", database.getProperty("current"));
        manifest.setProperty("runtime", managedRuntime?"managed":"windows-x64-java21-v2"); manifest.setProperty("version", version);
        if(managedRuntime) bundleRuntime(Path.of(args[4]),output,manifest);
        List<String> names = new ArrayList<>();
        Path archive = output.resolve("Orodent-EcoS-components.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive, StandardOpenOption.CREATE_NEW))) {
            for (Path jar : jars) {
                String name = jar.getFileName().toString();
                if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]*\\.jar")) throw new IllegalArgumentException("Nome JAR non valido");
                names.add(name); manifest.setProperty("sha256." + name, sha(jar));
                zip.putNextEntry(new ZipEntry("lib/" + name)); Files.copy(jar, zip); zip.closeEntry();
            }
        }
        manifest.setProperty("files", String.join(",", names)); manifest.setProperty("archive.sha256", sha(archive));
        Path catalog = output.resolve("update.properties");
        try (var writer = Files.newBufferedWriter(catalog, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) { manifest.store(writer, "Orodent-EcoS signed release"); }
        byte[] bytes = Files.readAllBytes(catalog);
        if(bytes.length>(managedRuntime?1_048_576:65536))throw new IllegalStateException("Catalogo troppo grande");
        Signature signer = Signature.getInstance("Ed25519"); signer.initSign(privateKey); signer.update(bytes); byte[] signature = signer.sign();
        Signature verifier = Signature.getInstance("Ed25519"); verifier.initVerify(publicKey); verifier.update(bytes);
        if (!verifier.verify(signature)) throw new IllegalStateException("Chiave privata e pubblica non corrispondono");
        Files.writeString(output.resolve("update.properties.sig"), Base64.getEncoder().encodeToString(signature), StandardOpenOption.CREATE_NEW);
        System.out.println("Creati catalogo firmato e componenti " + version + " in " + output);
    }
    private static void bundleRuntime(Path root, Path output, Properties catalog) throws Exception {
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) throw new IllegalStateException("Runtime Java non valido");
        var inventory=new TreeMap<String,String>();var folded=new HashSet<String>();var sources=new TreeMap<String,Path>();
        long total=0;
        try(var paths=Files.walk(root)) {
            for(Path path:paths.toList()) {
                if(Files.isSymbolicLink(path)) throw new IllegalStateException("Il runtime non deve contenere collegamenti simbolici");
                if(Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)) continue;
                String name=root.relativize(path).toString().replace('\\','/');
                if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || !safeRuntimePath(name) || !folded.add(name.toLowerCase(Locale.ROOT)))
                    throw new IllegalStateException("Percorso non valido nel runtime");
                total+=Files.size(path);
                if(total>1024L*1024*1024 || sources.size()>=4096) throw new IllegalStateException("Runtime troppo grande");
                inventory.put(name,sha(path));sources.put(name,path);
            }
        }
        if(!inventory.keySet().containsAll(Set.of("bin/java.exe","bin/server/jvm.dll","lib/modules","release"))) throw new IllegalStateException("Serve il runtime Windows completo generato dal packaging");
        Properties release=new Properties();try(var in=Files.newBufferedReader(root.resolve("release"))) {release.load(in);}
        String javaVersion=release.getProperty("JAVA_VERSION","").replace("\"","");
        if(!javaVersion.matches("[2-9][0-9]([.+-].*)?")) throw new IllegalStateException("Versione Java del runtime non valida");
        int feature=Integer.parseInt(javaVersion.split("[.+-]",2)[0]);if(feature<21)throw new IllegalStateException("Serve Java 21 o successivo");
        Path archive=output.resolve("Orodent-EcoS-runtime.zip");
        try(var zip=new ZipOutputStream(Files.newOutputStream(archive,StandardOpenOption.CREATE_NEW))) {
            for(var file:sources.entrySet()) {
                var entry=new ZipEntry(file.getKey());entry.setTime(0);zip.putNextEntry(entry);Files.copy(file.getValue(),zip);zip.closeEntry();
            }
        }
        if(Files.size(archive)>512L*1024*1024)throw new IllegalStateException("Archivio runtime troppo grande");
        var identity=MessageDigest.getInstance("SHA-256");
        for(var file:inventory.entrySet()) identity.update((file.getKey()+"\n"+file.getValue()+"\n").getBytes(StandardCharsets.UTF_8));
        catalog.setProperty("platform","windows-x64");catalog.setProperty("runtime.id",HexFormat.of().formatHex(identity.digest()));
        catalog.setProperty("runtime.java.feature",Integer.toString(feature));catalog.setProperty("runtime.archive.sha256",sha(archive));
        catalog.setProperty("runtime.files",String.join(",",inventory.keySet()));
        inventory.forEach((name,hash)->catalog.setProperty("runtime.sha256."+name,hash));
    }
    private static boolean safeRuntimePath(String name) {
        if(name.length()>240 || !name.matches("[A-Za-z0-9][A-Za-z0-9._/-]*") || name.endsWith("/"))return false;
        for(String part:name.split("/",-1)) if(part.isEmpty() || part.equals(".") || part.equals("..") || part.endsWith(".")
                || part.toUpperCase(Locale.ROOT).matches("(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?"))return false;
        return true;
    }
    private static String pem(String type, byte[] value) {
        return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(value) + "\n-----END " + type + "-----\n";
    }
    private static Properties databaseVersion(List<Path> jars) throws Exception {
        List<Path> databases = jars.stream()
                .filter(path -> path.getFileName().toString().startsWith("orodent-database-"))
                .toList();
        if (databases.size() != 1) throw new IllegalStateException("Componente database mancante o duplicato");
        try (var jar = new JarFile(databases.getFirst().toFile())) {
            var entry = jar.getJarEntry("database/version.properties");
            if (entry == null) throw new IllegalStateException("Metadati migrazioni database assenti");
            Properties properties = new Properties();
            try (var input = jar.getInputStream(entry)) { properties.load(input); }
            int minimum = Integer.parseInt(properties.getProperty("minimum"));
            int current = Integer.parseInt(properties.getProperty("current"));
            if (minimum < 1 || current < minimum) throw new IllegalStateException("Intervallo migrazioni database non valido");
            return properties;
        }
    }
    private static byte[] decode(String pem, String type) {
        return Base64.getDecoder().decode(pem.replace("-----BEGIN " + type + "-----", "").replace("-----END " + type + "-----", "").replaceAll("\\s", ""));
    }
    private static String sha(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) { byte[] bytes = new byte[65536]; for (int count; (count = input.read(bytes)) != -1;) digest.update(bytes, 0, count); }
        return HexFormat.of().formatHex(digest.digest());
    }
}
