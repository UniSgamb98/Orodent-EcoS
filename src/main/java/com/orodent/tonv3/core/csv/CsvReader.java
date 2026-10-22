package com.orodent.tonv3.core.csv;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

public final class CsvReader {

    public CsvDocument read(Path path, CsvReadOptions options) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(options, "options");

        try (Reader source = Files.newBufferedReader(path, options.charset())) {
            return read(source, options);
        } catch (IOException exception) {
            throw new CsvParseException(1, "Impossibile leggere il file CSV", exception);
        }
    }

    public CsvDocument read(Reader source, CsvReadOptions options) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(options, "options");

        String content = readContent(source);
        List<RawRecord> records = parseRecords(stripBom(content), options);

        if (options.ignoreEmptyLines()) {
            records = records.stream()
                    .filter(record -> !isEmpty(record.values()))
                    .toList();
        }
        if (records.isEmpty()) {
            return new CsvDocument(List.of(), List.of());
        }

        List<String> headers;
        int dataStart;
        if (options.firstRowIsHeader()) {
            headers = normalizeHeaders(records.getFirst(), options.trimValues());
            dataStart = 1;
        } else {
            headers = generatedHeaders(records.getFirst().values().size());
            dataStart = 0;
        }

        List<CsvRow> rows = createRows(records, dataStart, headers, options.trimValues());
        return new CsvDocument(headers, rows);
    }

    private String readContent(Reader source) {
        try {
            StringBuilder content = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = source.read(buffer)) != -1) {
                content.append(buffer, 0, count);
            }
            return content.toString();
        } catch (IOException exception) {
            throw new CsvParseException(1, "Impossibile leggere il contenuto CSV", exception);
        }
    }

    private List<RawRecord> parseRecords(String content, CsvReadOptions options) {
        List<RawRecord> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean quoteClosed = false;
        int lineNumber = 1;
        int recordLineNumber = 1;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);

            if (quoted) {
                if (current == options.quote()) {
                    if (isEscapedQuote(content, index, options.quote())) {
                        field.append(options.quote());
                        index++;
                    } else {
                        quoted = false;
                        quoteClosed = true;
                    }
                } else {
                    field.append(current);
                    if (isLineBreak(content, index)) {
                        lineNumber++;
                        if (current == '\r' && index + 1 < content.length()
                                && content.charAt(index + 1) == '\n') {
                            field.append('\n');
                            index++;
                        }
                    }
                }
                continue;
            }

            if (current == options.quote()) {
                if (!field.isEmpty() || quoteClosed) {
                    throw new CsvParseException(lineNumber, "Virgolette inattese in un campo non delimitato");
                }
                quoted = true;
            } else if (current == options.delimiter()) {
                fields.add(field.toString());
                field.setLength(0);
                quoteClosed = false;
            } else if (isLineBreak(content, index)) {
                fields.add(field.toString());
                records.add(new RawRecord(recordLineNumber, List.copyOf(fields)));
                fields.clear();
                field.setLength(0);
                quoteClosed = false;
                if (current == '\r' && index + 1 < content.length()
                        && content.charAt(index + 1) == '\n') {
                    index++;
                }
                lineNumber++;
                recordLineNumber = lineNumber;
            } else if (quoteClosed) {
                if (!options.trimValues() || !Character.isWhitespace(current)) {
                    throw new CsvParseException(lineNumber, "Carattere inatteso dopo un campo delimitato");
                }
            } else {
                field.append(current);
            }
        }

        if (quoted) {
            throw new CsvParseException(recordLineNumber, "Campo delimitato non chiuso");
        }
        if (hasLastRecord(content, fields, field, options.delimiter())) {
            fields.add(field.toString());
            records.add(new RawRecord(recordLineNumber, List.copyOf(fields)));
        }
        return records;
    }

    private List<CsvRow> createRows(
            List<RawRecord> records,
            int dataStart,
            List<String> headers,
            boolean trimValues
    ) {
        List<CsvRow> rows = new ArrayList<>();

        for (int index = dataStart; index < records.size(); index++) {
            RawRecord record = records.get(index);
            if (record.values().size() != headers.size()) {
                throw new CsvParseException(
                        record.lineNumber(),
                        "Attese " + headers.size() + " colonne, trovate " + record.values().size()
                );
            }

            Map<String, String> values = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                String value = record.values().get(column);
                values.put(headers.get(column), trimValues ? value.trim() : value);
            }
            rows.add(new CsvRow(record.lineNumber(), values));
        }
        return rows;
    }

    private List<String> normalizeHeaders(RawRecord header, boolean trimValues) {
        List<String> headers = header.values().stream()
                .map(value -> trimValues ? value.trim() : value)
                .toList();
        Set<String> uniqueHeaders = new LinkedHashSet<>();

        for (String value : headers) {
            if (value.isBlank()) {
                throw new CsvParseException(header.lineNumber(), "L'intestazione contiene una colonna vuota");
            }
            if (!uniqueHeaders.add(value)) {
                throw new CsvParseException(header.lineNumber(), "Colonna CSV duplicata: " + value);
            }
        }
        return headers;
    }

    private List<String> generatedHeaders(int columns) {
        return IntStream.range(0, columns)
                .mapToObj(index -> "column" + (index + 1))
                .toList();
    }

    private boolean isEscapedQuote(String content, int index, char quote) {
        return index + 1 < content.length() && content.charAt(index + 1) == quote;
    }

    private boolean isLineBreak(String content, int index) {
        char value = content.charAt(index);
        return value == '\n' || value == '\r';
    }

    private boolean hasLastRecord(
            String content,
            List<String> fields,
            StringBuilder field,
            char delimiter
    ) {
        return !field.isEmpty()
                || !fields.isEmpty()
                || (!content.isEmpty() && content.charAt(content.length() - 1) == delimiter);
    }

    private boolean isEmpty(List<String> values) {
        return values.stream().allMatch(String::isBlank);
    }

    private String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private record RawRecord(int lineNumber, List<String> values) {
    }
}
