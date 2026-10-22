package com.orodent.tonv3.core.database.csv;

import com.orodent.tonv3.core.csv.CsvParseException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Ripara le denominazioni multilinea non quotate prodotte dall'esportazione della rubrica. */
public final class ClienteCsvSourceNormalizer {

    private static final int EXPECTED_COLUMNS = 7;

    public String normalize(Path path, Charset charset) {
        try {
            return normalize(Files.readAllLines(path, charset));
        } catch (IOException exception) {
            throw new CsvParseException(1, "Impossibile leggere l'anagrafica clienti", exception);
        }
    }

    String normalize(List<String> physicalLines) {
        if (physicalLines.isEmpty()) return "";
        List<String> normalized = new ArrayList<>();
        normalized.add(physicalLines.getFirst());
        String[] current = null;
        int currentLine = 1;

        for (int index = 1; index < physicalLines.size(); index++) {
            String line = physicalLines.get(index);
            String[] fields = splitFirstColumns(line);
            if (fields != null) {
                if (current != null) normalized.add(toCsv(current));
                current = fields;
                currentLine = index + 1;
            } else if (!line.isBlank()) {
                if (current == null) {
                    throw new CsvParseException(index + 1, "Continuazione della ragione sociale senza cliente");
                }
                current[EXPECTED_COLUMNS - 1] = (current[EXPECTED_COLUMNS - 1] + " " + line.trim()).trim();
            }
        }
        if (current != null) normalized.add(toCsv(current));
        if (normalized.size() == 1 && physicalLines.size() > 1) {
            throw new CsvParseException(currentLine, "Nessun cliente valido trovato nell'anagrafica");
        }
        return String.join("\n", normalized);
    }

    private String[] splitFirstColumns(String line) {
        String[] fields = new String[EXPECTED_COLUMNS];
        int start = 0;
        for (int column = 0; column < EXPECTED_COLUMNS - 1; column++) {
            int delimiter = line.indexOf(';', start);
            if (delimiter < 0) return null;
            fields[column] = line.substring(start, delimiter);
            start = delimiter + 1;
        }
        fields[EXPECTED_COLUMNS - 1] = unwrap(line.substring(start).trim());
        return fields;
    }

    private String unwrap(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1).replace("\"\"", "\"")
                : value;
    }

    private String toCsv(String[] fields) {
        List<String> encoded = new ArrayList<>(EXPECTED_COLUMNS);
        for (int index = 0; index < fields.length; index++) {
            String field = index == EXPECTED_COLUMNS - 1 ? unwrap(fields[index].trim()) : fields[index];
            encoded.add("\"" + field.replace("\"", "\"\"") + "\"");
        }
        return String.join(";", encoded);
    }
}
