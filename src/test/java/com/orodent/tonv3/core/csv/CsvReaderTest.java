package com.orodent.tonv3.core.csv;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvReaderTest {

    private final CsvReader reader = new CsvReader();

    @Test
    void readsSemicolonSeparatedDocument() {
        CsvDocument document = reader.read(
                new StringReader("codice;descrizione\nA1; Prodotto uno \n"),
                CsvReadOptions.semicolonSeparated()
        );

        assertEquals(2, document.headers().size());
        assertEquals(1, document.rows().size());
        assertEquals("A1", document.rows().getFirst().requiredValue("codice"));
        assertEquals("Prodotto uno", document.rows().getFirst().requiredValue("descrizione"));
        assertEquals(2, document.rows().getFirst().lineNumber());
    }

    @Test
    void supportsSeparatorEscapedQuoteAndLineBreakInsideQuotedField() {
        CsvDocument document = reader.read(
                new StringReader("id;note\r\n1;\"prima; \"\"parte\"\"\r\\nseconda parte\"\r\n".replace("\\n", "\n")),
                CsvReadOptions.semicolonSeparated()
        );

        assertEquals("prima; \"parte\"\r\nseconda parte", document.rows().getFirst().requiredValue("note"));
        assertEquals(2, document.rows().getFirst().lineNumber());
    }

    @Test
    void stripsBomAndCanGenerateHeaders() {
        CsvReadOptions options = new CsvReadOptions(';', '"', false, true, true, StandardCharsets.UTF_8);
        CsvDocument document = reader.read(new StringReader("\uFEFFA;B"), options);

        assertEquals("A", document.rows().getFirst().requiredValue("column1"));
        assertEquals("B", document.rows().getFirst().requiredValue("column2"));
    }

    @Test
    void rejectsDuplicateHeaders() {
        CsvParseException exception = assertThrows(
                CsvParseException.class,
                () -> reader.read(new StringReader("codice;codice\n1;2"), CsvReadOptions.semicolonSeparated())
        );

        assertEquals(1, exception.lineNumber());
        assertTrue(exception.getMessage().contains("duplicata"));
    }

    @Test
    void rejectsRowsWithUnexpectedColumnCount() {
        CsvParseException exception = assertThrows(
                CsvParseException.class,
                () -> reader.read(new StringReader("a;b\n1;2;3"), CsvReadOptions.semicolonSeparated())
        );

        assertEquals(2, exception.lineNumber());
    }

    @Test
    void rejectsUnclosedQuotedField() {
        CsvParseException exception = assertThrows(
                CsvParseException.class,
                () -> reader.read(new StringReader("a;b\n1;\"test"), CsvReadOptions.semicolonSeparated())
        );

        assertEquals(2, exception.lineNumber());
    }
}
