package com.orodent.tonv3.core.csv;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvImporterTest {

    @Test
    void mapsValidRowsAndCollectsMappingAndValidationErrors() {
        CsvRowMapper<Product> mapper = new CsvRowMapper<>() {
            @Override
            public Product map(CsvRow row) {
                String rawPrice = row.requiredValue("prezzo");
                try {
                    return new Product(row.requiredValue("codice"), Integer.parseInt(rawPrice));
                } catch (NumberFormatException exception) {
                    throw new CsvMappingException("prezzo", rawPrice, "Prezzo non valido", exception);
                }
            }

            @Override
            public Set<String> requiredColumns() {
                return Set.of("codice", "prezzo");
            }
        };
        ModelValidator<Product> validator = product -> product.price() < 0
                ? List.of(new ModelValidator.ValidationError("prezzo", "Il prezzo non può essere negativo"))
                : List.of();
        CsvImporter<Product> importer = new CsvImporter<>(new CsvReader(), mapper, validator);

        CsvImportResult<Product> result = importer.importReader(
                new StringReader("codice;prezzo\nA;10\nB;abc\nC;-1"),
                CsvReadOptions.semicolonSeparated()
        );

        assertEquals(3, result.totalRows());
        assertEquals(List.of(new CsvImportResult.ImportedRow<>(2, new Product("A", 10))), result.validRows());
        assertEquals(2, result.rowErrors().size());
        assertEquals(3, result.rowErrors().get(0).lineNumber());
        assertEquals("abc", result.rowErrors().get(0).rawValue());
        assertEquals(4, result.rowErrors().get(1).lineNumber());
        assertFalse(result.valid());
    }

    @Test
    void reportsMissingRequiredColumnsAsDocumentError() {
        CsvRowMapper<String> mapper = new CsvRowMapper<>() {
            @Override
            public String map(CsvRow row) {
                return row.requiredValue("codice");
            }

            @Override
            public Set<String> requiredColumns() {
                return Set.of("codice");
            }
        };
        CsvImporter<String> importer = new CsvImporter<>(new CsvReader(), mapper, ModelValidator.none());

        CsvImportResult<String> result = importer.importReader(
                new StringReader("descrizione\nProdotto"),
                CsvReadOptions.semicolonSeparated()
        );

        assertEquals(1, result.totalRows());
        assertTrue(result.validRows().isEmpty());
        assertEquals(1, result.documentErrors().size());
        assertFalse(result.valid());
    }

    @Test
    void convertsParseFailureIntoDocumentError() {
        CsvImporter<String> importer = new CsvImporter<>(
                new CsvReader(),
                row -> row.requiredValue("codice"),
                ModelValidator.none()
        );

        CsvImportResult<String> result = importer.importReader(
                new StringReader("codice\n\"non chiuso"),
                CsvReadOptions.semicolonSeparated()
        );

        assertEquals(0, result.totalRows());
        assertEquals(2, result.documentErrors().getFirst().lineNumber());
        assertFalse(result.valid());
    }

    private record Product(String code, int price) {
    }
}
