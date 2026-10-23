package com.orodent.ecos.database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/** Consistent Derby snapshots, taken before schema writes and before network publication. */
final class DatabaseBackup {
    static final String FAILURE_STATE = "XEC02";
    private static final System.Logger LOG = System.getLogger(DatabaseBackup.class.getName());
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("uuuuMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private final Path home;

    DatabaseBackup(Path home) { this.home = home; }

    void create(Connection connection) throws SQLException {
        Path destination = null;
        try {
            Path backups = Files.createDirectories(home.resolve("backups"));
            destination = Files.createTempDirectory(backups,
                    "before-schema-" + DatabaseSchema.CURRENT_VERSION + "-" + TIMESTAMP.format(Instant.now()) + "-");
            // Derby flushes/checkpoints its own files; copying an open database is not a backup.
            // This connection has no uncommitted schema/data changes.
            try (var statement = connection.prepareCall("CALL SYSCS_UTIL.SYSCS_BACKUP_DATABASE(?)")) {
                statement.setString(1, destination.toString());
                statement.execute();
            }
            Path database = destination.resolve(DatabaseConfiguration.DATABASE_NAME);
            if (!Files.isRegularFile(database.resolve("service.properties"))
                    || !Files.isDirectory(database.resolve("seg0"))
                    || !Files.isDirectory(database.resolve("log"))) {
                throw new IOException("Il backup Derby non contiene tutti i file richiesti.");
            }
            Properties metadata = new Properties();
            metadata.setProperty("format", "1");
            metadata.setProperty("database", DatabaseConfiguration.DATABASE_NAME);
            metadata.setProperty("source.database", home.resolve(DatabaseConfiguration.DATABASE_NAME).toString());
            metadata.setProperty("migration.target", Integer.toString(DatabaseSchema.CURRENT_VERSION));
            metadata.setProperty("created.utc", Instant.now().toString());
            // Only this final marker identifies a completed snapshot. Partial backups stay unmarked.
            Path pending = destination.resolve("backup.properties.pending");
            Path completed = destination.resolve("backup.properties");
            try (var output = Files.newOutputStream(pending)) {
                metadata.store(output, "Completed pre-migration Derby backup");
            }
            try {
                Files.move(pending, completed, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(pending, completed);
            }
            LOG.log(System.Logger.Level.INFO, "Backup prima della migrazione completato: " + destination);
        } catch (IOException | SQLException failure) {
            throw new SQLException("Backup prima della migrazione non riuscito. Database non modificato. "
                    + "Verificare spazio e permessi in " + home.resolve("backups")
                    + (destination == null ? "." : ". Copia incompleta: " + destination), FAILURE_STATE, failure);
        }
    }
}
