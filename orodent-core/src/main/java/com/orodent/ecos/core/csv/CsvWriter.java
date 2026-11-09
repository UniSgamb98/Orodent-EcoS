package com.orodent.ecos.core.csv;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Streaming CSV encoding, independent of JavaFX, JDBC, and domain models. */
public final class CsvWriter {
    /** Flushes, but never closes, the caller's writer. Returns data rows, excluding the header. */
    public long write(
            Writer target,
            List<String> headers,
            Iterable<? extends List<?>> rows,
            CsvWriteOptions options) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(rows, "rows");
        long recordNumber = 1;
        try {
            List<String> columns = List.copyOf(headers);
            if (columns.isEmpty()
                    || columns.stream().anyMatch(String::isBlank)
                    || new HashSet<>(columns).size() != columns.size()) {
                throw new IllegalArgumentException("Intestazioni CSV vuote o duplicate");
            }
            if (options.utf8Bom()) target.write('\uFEFF');
            writeRecord(target, columns, options);
            recordNumber = 2;
            for (List<?> row : rows) {
                if (row == null || row.size() != columns.size()) {
                    throw new IllegalArgumentException(
                            "Attese "
                                    + columns.size()
                                    + " colonne CSV, trovate "
                                    + (row == null ? "nessuna" : row.size()));
                }
                writeRecord(target, row, options);
                recordNumber++;
            }
            target.flush();
            return recordNumber - 2;
        } catch (IOException | RuntimeException error) {
            throw new CsvExportException(
                    recordNumber,
                    "Esportazione CSV non riuscita al record "
                            + recordNumber
                            + ": "
                            + error.getMessage(),
                    error);
        }
    }

    private void writeRecord(Writer target, List<?> cells, CsvWriteOptions options)
            throws IOException {
        for (int index = 0; index < cells.size(); index++) {
            if (index != 0) target.write(options.delimiter());
            Object cell = cells.get(index);
            String text = format(cell, options);
            // Protect text cells only: negative numeric values must remain numbers in Excel.
            if (options.protectSpreadsheetFormulas() && cell instanceof String && formula(text)) {
                text = "'" + text;
            }
            // Always quote text, including whitespace and leading zeroes, to preserve CSV content.
            boolean quoted =
                    cell instanceof String
                            || text.indexOf(options.delimiter()) >= 0
                            || text.indexOf(options.quote()) >= 0
                            || text.indexOf('\r') >= 0
                            || text.indexOf('\n') >= 0;
            if (quoted) target.write(options.quote());
            for (int offset = 0; offset < text.length(); offset++) {
                char ch = text.charAt(offset);
                if (ch == options.quote()) target.write(ch);
                target.write(ch);
            }
            if (quoted) target.write(options.quote());
        }
        target.write("\r\n");
    }

    private String format(Object value, CsvWriteOptions options) {
        if (value == null) return "";
        String decimal;
        if (value instanceof BigDecimal number) decimal = number.toPlainString();
        else if (value instanceof Float number && Float.isFinite(number)) {
            decimal = new BigDecimal(Float.toString(number)).toPlainString();
        } else if (value instanceof Double number && Double.isFinite(number)) {
            decimal = BigDecimal.valueOf(number).toPlainString();
        } else if (value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof BigInteger) {
            decimal = value.toString();
        } else if (value instanceof String
                || value instanceof Boolean
                || value instanceof LocalDate
                || value instanceof LocalDateTime
                || value instanceof LocalTime
                || value instanceof Instant
                || value instanceof OffsetDateTime
                || value instanceof OffsetTime
                || value instanceof ZonedDateTime) {
            return value.toString();
        } else {
            throw new IllegalArgumentException(
                    "Tipo di cella CSV non supportato: " + value.getClass().getSimpleName());
        }
        return options.decimalSeparator() == ',' ? decimal.replace('.', ',') : decimal;
    }

    private boolean formula(String value) {
        String leading = value.stripLeading();
        if (leading.isEmpty()) return false;
        char first = leading.charAt(0);
        return first == '='
                || first == '+'
                || first == '-'
                || first == '@'
                || value.startsWith("\t")
                || value.startsWith("\r")
                || value.startsWith("\n");
    }
}
