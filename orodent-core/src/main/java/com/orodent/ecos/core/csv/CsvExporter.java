package com.orodent.ecos.core.csv;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/** Export counterpart of CsvImporter. The calling service chooses data and worker executor. */
public final class CsvExporter<T> {
    private final CsvWriter writer;
    private final CsvExportMapper<T> mapper;

    public CsvExporter(CsvWriter writer, CsvExportMapper<T> mapper) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * Stages the complete file in the destination directory before replacing the target. A
     * mapping/write failure leaves any existing file intact and removes the temporary file. The
     * caller owns the choice of path and overwrite confirmation.
     */
    public Result exportFile(Path path, Iterable<? extends T> rows, CsvWriteOptions options) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(options, "options");
        Path destination = path.toAbsolutePath().normalize();
        Path temporary = null;
        try {
            temporary = Files.createTempFile(destination.getParent(), ".ecos-csv-", ".tmp");
            long count;
            try (Writer target = Files.newBufferedWriter(temporary, options.charset())) {
                count = exportWriter(target, rows, options);
            }
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            return new Result(destination, count);
        } catch (IOException error) {
            throw new CsvExportException(
                    0, "Impossibile salvare il file CSV: " + error.getMessage(), error);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Preserve the original export error; an external process may hold the file.
                }
            }
        }
    }

    /** Does not close the caller's writer. Only exportFile provides staged replacement. */
    public long exportWriter(Writer target, Iterable<? extends T> rows, CsvWriteOptions options) {
        Objects.requireNonNull(rows, "rows");
        Iterable<List<?>> mapped =
                () ->
                        new Iterator<>() {
                            private final Iterator<? extends T> source = rows.iterator();

                            @Override
                            public boolean hasNext() {
                                return source.hasNext();
                            }

                            @Override
                            public List<?> next() {
                                return mapper.map(source.next());
                            }
                        };
        return writer.write(target, mapper.headers(), mapped, options);
    }

    public record Result(Path path, long exportedRows) {}
}
