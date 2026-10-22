package com.orodent.ecos.core.csv;

import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CsvImporter<T> {

    private final CsvReader reader;
    private final CsvRowMapper<T> mapper;
    private final ModelValidator<T> validator;

    public CsvImporter(
            CsvReader reader,
            CsvRowMapper<T> mapper,
            ModelValidator<T> validator
    ) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public CsvImportResult<T> importFile(Path path, CsvReadOptions options) {
        try {
            return importDocument(reader.read(path, options));
        } catch (CsvParseException exception) {
            return documentFailure(exception);
        }
    }

    public CsvImportResult<T> importReader(Reader source, CsvReadOptions options) {
        try {
            return importDocument(reader.read(source, options));
        } catch (CsvParseException exception) {
            return documentFailure(exception);
        }
    }

    private CsvImportResult<T> importDocument(CsvDocument document) {
        Set<String> missingColumns = new LinkedHashSet<>(mapper.requiredColumns());
        document.headers().forEach(missingColumns::remove);

        if (!missingColumns.isEmpty()) {
            CsvImportResult.DocumentError error = new CsvImportResult.DocumentError(
                    1,
                    "Colonne CSV obbligatorie mancanti: " + String.join(", ", missingColumns)
            );
            return new CsvImportResult<>(document.rows().size(), List.of(), List.of(), List.of(error));
        }

        List<CsvImportResult.ImportedRow<T>> validRows = new ArrayList<>();
        List<CsvImportResult.RowError> rowErrors = new ArrayList<>();

        for (CsvRow row : document.rows()) {
            importRow(row, validRows, rowErrors);
        }

        return new CsvImportResult<>(document.rows().size(), validRows, rowErrors, List.of());
    }

    private void importRow(
            CsvRow row,
            List<CsvImportResult.ImportedRow<T>> validRows,
            List<CsvImportResult.RowError> rowErrors
    ) {
        try {
            T value = mapper.map(row);
            List<ModelValidator.ValidationError> validationErrors = validator.validate(value);

            if (validationErrors.isEmpty()) {
                validRows.add(new CsvImportResult.ImportedRow<>(row.lineNumber(), value));
                return;
            }

            for (ModelValidator.ValidationError error : validationErrors) {
                rowErrors.add(new CsvImportResult.RowError(
                        row.lineNumber(),
                        error.field(),
                        row.values().get(error.field()),
                        error.message()
                ));
            }
        } catch (CsvMappingException exception) {
            rowErrors.add(new CsvImportResult.RowError(
                    row.lineNumber(),
                    exception.column(),
                    exception.rawValue(),
                    exception.getMessage()
            ));
        }
    }

    private CsvImportResult<T> documentFailure(CsvParseException exception) {
        CsvImportResult.DocumentError error = new CsvImportResult.DocumentError(
                exception.lineNumber(),
                exception.getMessage()
        );
        return new CsvImportResult<>(0, List.of(), List.of(), List.of(error));
    }
}
