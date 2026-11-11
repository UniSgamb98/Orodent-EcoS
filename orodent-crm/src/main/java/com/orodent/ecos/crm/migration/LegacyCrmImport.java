package com.orodent.ecos.crm.migration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/** CLI for the reviewed CRM2 export. Never starts Derby or alters the EcoS schema. */
public final class LegacyCrmImport {
    static final List<String> FILES =
            List.of(
                    "import_operatori.sql",
                    "import_cataloghi.sql",
                    "import_clienti.sql",
                    "import_contatti.sql",
                    "import_indirizzi.sql",
                    "import_telefoni.sql",
                    "import_email.sql",
                    "import_siti.sql",
                    "import_note_interazioni.sql");
    static final List<String> DATA_TABLES =
            List.of(
                    "CLIENTI",
                    "CONTATTI_CLIENTE",
                    "INDIRIZZI_CLIENTE",
                    "TELEFONI_CLIENTE",
                    "EMAIL_CLIENTE",
                    "SITI_WEB_CLIENTE",
                    "NOTE_CLIENTE",
                    "INTERAZIONI",
                    "CLIENTI_FORNI",
                    "CLIENTI_FRESATORI",
                    "CLIENTI_MATERIALI",
                    "CLIENTI_PREFERITI");
    private static final Pattern INSERT =
            Pattern.compile(
                    "(?is)^INSERT\\s+INTO\\s+(ECOS\\.OPERATORI|CRM\\.(CLIENTI|TIPI_CLIENTE|STATI_TRATTATIVA|CONTATTI_CLIENTE|INDIRIZZI_CLIENTE|TELEFONI_CLIENTE|EMAIL_CLIENTE|SITI_WEB_CLIENTE|NOTE_CLIENTE|INTERAZIONI))\\s*\\(");
    private static final long MAX_FILE = 128L * 1024 * 1024;
    private static final int MAX_STATEMENTS = 1_000_000;

    private LegacyCrmImport() {}

    public record Command(String file, int ordinal, String sql) {}

    public record Dataset(
            Path directory,
            int clients,
            List<String> operators,
            List<Command> commands,
            boolean acceptedAnomalies) {
        public Dataset {
            operators = List.copyOf(operators);
            commands = List.copyOf(commands);
        }

        public static Dataset load(Path directory) throws IOException {
            Path dir = directory.toRealPath();
            Properties p = new Properties();
            try (var reader =
                    Files.newBufferedReader(
                            dir.resolve("migration.properties"), StandardCharsets.UTF_8)) {
                p.load(reader);
            }
            if (!"1".equals(p.getProperty("format"))
                    || !"CRM2".equals(p.getProperty("source"))
                    || !"OrodentEcoS".equals(p.getProperty("target"))
                    || !"13".equals(p.getProperty("schema.minimum")))
                throw new IOException(
                        "Pacchetto non riconosciuto: rigenera gli script con il generatore per"
                            + " EcoS.");
            if (!"false".equals(p.getProperty("blocked")))
                throw new IOException(
                        "Preparazione bloccata da anomalie: consulta import_report.txt e correggi i"
                            + " dati prima dell'importazione.");
            if (!Integer.toString(FILES.size()).equals(p.getProperty("files.count")))
                throw new IOException("Elenco file incompleto.");
            int clients = number(p, "clients", 1, MAX_STATEMENTS);
            int operators = number(p, "operators.count", 1, MAX_STATEMENTS);
            var names = new ArrayList<String>();
            for (int i = 1; i <= operators; i++) {
                String name = p.getProperty("operator." + i + ".username");
                if (name == null || name.isBlank() || name.length() > 80)
                    throw new IOException("Nome operatore non valido nel manifest.");
                names.add(name);
            }
            var commands = new ArrayList<Command>();
            for (int i = 0; i < FILES.size(); i++) {
                String file = FILES.get(i);
                if (!file.equals(p.getProperty("file." + (i + 1) + ".name")))
                    throw new IOException("Ordine file SQL non valido.");
                Path path = dir.resolve(file).toRealPath();
                if (!path.getParent().equals(dir) || Files.size(path) > MAX_FILE)
                    throw new IOException("File SQL non valido: " + file);
                byte[] bytes = Files.readAllBytes(path);
                String hash = HexFormat.of().formatHex(sha256(bytes));
                if (!hash.equals(p.getProperty("file." + (i + 1) + ".sha256")))
                    throw new IOException(
                            "Il file "
                                    + file
                                    + " è stato modificato dopo la preparazione. Rigenera il"
                                    + " pacchetto.");
                List<String> statements = splitSql(new String(bytes, StandardCharsets.UTF_8));
                if (statements.size()
                        != number(p, "file." + (i + 1) + ".statements", 0, MAX_STATEMENTS))
                    throw new IOException("Conteggio istruzioni errato: " + file);
                for (int j = 0; j < statements.size(); j++) {
                    String sql = statements.get(j);
                    if (!INSERT.matcher(sql).find())
                        throw new IOException("Istruzione non ammessa: " + file + " #" + (j + 1));
                    commands.add(new Command(file, j + 1, sql));
                    if (commands.size() > MAX_STATEMENTS)
                        throw new IOException("Pacchetto troppo grande.");
                }
            }
            long inserted =
                    commands.stream()
                            .filter(
                                    c ->
                                            c.sql()
                                                    .matches(
                                                            "(?is)^INSERT\\s+INTO\\s+CRM\\.CLIENTI\\s*\\(.*"))
                            .count();
            if (inserted != clients) throw new IOException("Conteggio anagrafiche incoerente.");
            return new Dataset(
                    dir,
                    clients,
                    names,
                    commands,
                    Boolean.parseBoolean(p.getProperty("anomalies.accepted", "false")));
        }
    }

    static int number(Properties p, String key, int min, int max) throws IOException {
        try {
            int value = Integer.parseInt(p.getProperty(key));
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (RuntimeException error) {
            throw new IOException("Valore non valido nel manifest: " + key, error);
        }
    }

    static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    /** Handles semicolons, quotes, comments and newlines inside the original note text. */
    static List<String> splitSql(String input) throws IOException {
        var result = new ArrayList<String>();
        var current = new StringBuilder();
        boolean quoted = false, lineComment = false, blockComment = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i), next = i + 1 < input.length() ? input.charAt(i + 1) : '\0';
            if (lineComment) {
                if (c == '\n' || c == '\r') {
                    lineComment = false;
                    current.append(' ');
                }
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i++;
                    current.append(' ');
                }
                continue;
            }
            if (!quoted && c == '-' && next == '-') {
                lineComment = true;
                i++;
                continue;
            }
            if (!quoted && c == '/' && next == '*') {
                blockComment = true;
                i++;
                continue;
            }
            if (c == '\'') {
                current.append(c);
                if (quoted && next == '\'') {
                    current.append(next);
                    i++;
                    continue;
                }
                quoted = !quoted;
                continue;
            }
            if (c == ';' && !quoted) {
                String sql = current.toString().strip();
                if (!sql.isEmpty()) result.add(sql);
                current.setLength(0);
            } else current.append(c);
        }
        if (quoted || blockComment || !current.toString().isBlank())
            throw new IOException("SQL incompleto: apici, commenti o terminatore mancanti.");
        return List.copyOf(result);
    }

    public record Plan(
            int existingOperators, int statements, int clients, boolean acceptedAnomalies) {}

    public static Plan inspect(Connection c, Dataset data) throws SQLException {
        if (!c.getAutoCommit())
            throw new SQLException("La verifica richiede una connessione dedicata.");
        verifySchema(c);
        emptyCrm(c);
        int existing = operators(c, data);
        return new Plan(existing, data.commands().size(), data.clients(), data.acceptedAnomalies());
    }

    private static void verifySchema(Connection c) throws SQLException {
        try (var q = c.createStatement();
                var r = q.executeQuery("SELECT MAX(version) FROM ECOS.ECOS_SCHEMA_HISTORY")) {
            r.next();
            if (r.getInt(1) < 13)
                throw new SQLException(
                        "Avvia EcoS aggiornato sull'HOST per preparare lo schema CRM.");
        }
        try (var q = c.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT ID,VERSIONE_MODIFICA,CODICE_GESTIONALE FROM CRM.CLIENTI"
                                    + " WHERE 1=0")) {}
    }

    private static void emptyCrm(Connection c) throws SQLException {
        for (String table : DATA_TABLES)
            try (var q = c.createStatement();
                    var r = q.executeQuery("SELECT COUNT(*) FROM CRM." + table)) {
                r.next();
                if (r.getLong(1) != 0)
                    throw new SQLException(
                            "Il CRM contiene già dati ("
                                    + table
                                    + "). Importazione iniziale interrotta: nessun dato esistente"
                                    + " verrà cancellato o sovrascritto.");
            }
    }

    private static int operators(Connection c, Dataset data) throws SQLException {
        int existing = 0;
        try (var q =
                c.prepareStatement(
                        "SELECT ID,ATTIVO FROM ECOS.OPERATORI WHERE UPPER(TRIM(USERNAME))=?")) {
            for (String name : data.operators()) {
                q.setString(1, name);
                try (var r = q.executeQuery()) {
                    if (r.next()) {
                        if (r.getInt("ATTIVO") != 1)
                            throw new SQLException(
                                    "Un operatore del backup è disattivato in EcoS: " + name);
                        existing++;
                        if (r.next())
                            throw new SQLException(
                                    "Nome operatore ambiguo in EcoS: "
                                            + name
                                            + ". Rinomina gli omonimi prima di importare.");
                    }
                }
            }
        }
        return existing;
    }

    public record Result(Path backup, int executed, int clients) {}

    /**
     * Backup finishes before the first INSERT; all data changes commit together, without retries.
     */
    public static Result apply(Connection c, Dataset data, Path backupDirectory)
            throws SQLException, IOException {
        inspect(c, data);
        Path backup = backupDirectory.toAbsolutePath().normalize();
        // The JDBC call runs on the Derby HOST. The CLI is intentionally restricted to that
        // machine.
        try (var q = c.prepareCall("CALL SYSCS_UTIL.SYSCS_BACKUP_DATABASE(?)")) {
            q.setString(1, backup.toString());
            q.execute();
        }
        c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
        c.setAutoCommit(false);
        int executed = 0;
        try {
            try (var q = c.createStatement()) {
                // Same order on every importer; prevents two preparations from importing
                // concurrently.
                q.executeUpdate("LOCK TABLE ECOS.OPERATORI IN EXCLUSIVE MODE");
                for (String table : DATA_TABLES)
                    q.executeUpdate("LOCK TABLE CRM." + table + " IN EXCLUSIVE MODE");
                q.executeUpdate("LOCK TABLE CRM.TIPI_CLIENTE IN EXCLUSIVE MODE");
                q.executeUpdate("LOCK TABLE CRM.STATI_TRATTATIVA IN EXCLUSIVE MODE");
            }
            emptyCrm(c);
            operators(c, data);
            for (Command command : data.commands()) {
                try (var q = c.createStatement()) {
                    q.executeUpdate(command.sql());
                    executed++;
                } catch (SQLException error) {
                    throw new SQLException(
                            "Errore in "
                                    + command.file()
                                    + " (istruzione "
                                    + command.ordinal()
                                    + ", SQLState "
                                    + error.getSQLState()
                                    + ").",
                            error.getSQLState(),
                            error);
                }
            }
            try (var q = c.createStatement();
                    var r = q.executeQuery("SELECT COUNT(*) FROM CRM.CLIENTI")) {
                r.next();
                if (r.getInt(1) != data.clients())
                    throw new SQLException(
                            "Verifica finale del numero di anagrafiche non riuscita.");
            }
            c.commit();
            return new Result(backup, executed, data.clients());
        } catch (SQLException | RuntimeException error) {
            try {
                c.rollback();
            } catch (SQLException rollback) {
                error.addSuppressed(rollback);
            }
            throw error;
        }
        // The caller closes this dedicated connection. Never set auto-commit after a failed
        // rollback.
    }

    public static void main(String[] args) {
        int code = run(args, System.out, System.err);
        if (code != 0) System.exit(code);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            var options = options(args);
            Path dir = Path.of(options.getOrDefault("--dir", "output"));
            Dataset data = Dataset.load(dir);
            Properties config = new Properties();
            String configName = options.get("--config");
            if (configName != null)
                try (var reader =
                        Files.newBufferedReader(Path.of(configName), StandardCharsets.UTF_8)) {
                    config.load(reader);
                }
            String port = config.getProperty("database.port", "1528");
            if (!port.matches("[0-9]{1,5}")
                    || Integer.parseInt(port) < 1
                    || Integer.parseInt(port) > 65535)
                throw new IOException("Porta database non valida.");
            String url =
                    options.getOrDefault(
                            "--url", "jdbc:derby://localhost:" + port + "/OrodentEcoS");
            if (!url.matches(
                    "jdbc:derby://(localhost|127\\.0\\.0\\.1|\\[::1\\]):[0-9]{1,5}/OrodentEcoS"))
                throw new IOException(
                        "Eseguire sul PC HOST di EcoS con URL locale"
                            + " jdbc:derby://localhost:PORTA/OrodentEcoS, senza create=true.");
            String user = System.getenv().getOrDefault("ECOS_DB_USER", "APP"),
                    password = System.getenv().getOrDefault("ECOS_DB_PASSWORD", "pw");
            out.println("Destinazione: " + url);
            out.println("Origine: backup CRM2 preparato in " + data.directory());
            try (Connection c = DriverManager.getConnection(url, user, password)) {
                Plan plan = inspect(c, data);
                out.println(
                        "Anagrafiche: "
                                + plan.clients()
                                + " · Istruzioni: "
                                + plan.statements()
                                + " · Operatori esistenti riutilizzati: "
                                + plan.existingOperators());
                if (plan.acceptedAnomalies())
                    out.println(
                            "ATTENZIONE: la preparazione accetta esplicitamente anomalie. Leggere"
                                + " import_report.txt.");
                if (!options.containsKey("--apply")) {
                    out.println(
                            "Solo verifica: nessun dato modificato. Per importare usare --apply.");
                    return 0;
                }
                String home = config.getProperty("database.home");
                String selected = options.get("--backup-dir");
                if (selected == null && (home == null || home.isBlank()))
                    throw new IOException(
                            "Indicare --config di EcoS o --backup-dir per il backup preventivo.");
                Path backup =
                        selected == null
                                ? Path.of(home)
                                        .resolve("backups")
                                        .resolve(
                                                "before-crm2-import-"
                                                        + DateTimeFormatter.ofPattern(
                                                                        "uuuuMMdd-HHmmss")
                                                                .format(LocalDateTime.now())
                                                        + "-"
                                                        + UUID.randomUUID()
                                                                .toString()
                                                                .substring(0, 8))
                                : Path.of(selected);
                Result result = apply(c, data, backup);
                out.println(
                        "Importazione completata: "
                                + result.clients()
                                + " anagrafiche. Backup preventivo: "
                                + result.backup());
            }
            return 0;
        } catch (Exception error) {
            boolean uncertain = false;
            for (Throwable e = error; e != null; e = e.getCause())
                if (e instanceof SQLException sql
                        && sql.getSQLState() != null
                        && sql.getSQLState().startsWith("08")) uncertain = true;
            err.println("ERRORE: " + error.getMessage());
            err.println(
                    uncertain
                            ? "Connessione interrotta: verificare i dati sul database prima di"
                                  + " riprovare; l'esito potrebbe non essere confermato."
                            : "Importazione non completata. Se era iniziata, tutte le modifiche"
                                  + " sono state annullate salvo errori di connessione; consultare"
                                  + " il backup e verificare prima di ripetere.");
            return 2;
        }
    }

    static Map<String, String> options(String[] args) throws IOException {
        var result = new LinkedHashMap<String, String>();
        Set<String> values = Set.of("--dir", "--config", "--url", "--backup-dir");
        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            if (result.containsKey(option)) throw new IOException("Opzione ripetuta: " + option);
            if (option.equals("--apply")) {
                result.put(option, "true");
                continue;
            }
            if (!values.contains(option) || ++i >= args.length || args[i].startsWith("--"))
                throw new IOException(
                        "Uso: --dir CARTELLA --config application.properties [--url JDBC_LOCALE]"
                            + " [--apply] [--backup-dir CARTELLA]");
            result.put(option, args[i]);
        }
        return result;
    }
}
