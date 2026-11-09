package com.orodent.ecos.core.csv;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class CsvExporterTest {
    @TempDir Path directory;

    private record Price(String code, String name, BigDecimal cost, LocalDate date) {}

    private final CsvExporter<Price> prices =
            new CsvExporter<>(
                    new CsvWriter(),
                    new CsvExportMapper<>() {
                        @Override
                        public List<String> headers() {
                            return List.of("Codice", "Descrizione", "Costo (EUR)", "Data prezzi");
                        }

                        @Override
                        public List<?> map(Price value) {
                            return Arrays.asList(
                                    value.code(), value.name(), value.cost(), value.date());
                        }
                    });

    @Test
    void writesExcelItalianWithExactNumbersDatesAndEscapedText() throws IOException {
        Path file = directory.resolve("costi.csv");
        String description = "Polvere è; \"gialla\"\r\nseconda riga";
        CsvExporter.Result result =
                prices.exportFile(
                        file,
                        List.of(
                                new Price(
                                        "000123",
                                        description,
                                        new BigDecimal("1234.567890123"),
                                        LocalDate.of(2026, 11, 9))),
                        CsvWriteOptions.excelItalian());

        byte[] bytes = Files.readAllBytes(file);
        assertArrayEquals(
                new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf}, Arrays.copyOf(bytes, 3));
        String content = Files.readString(file);
        assertTrue(content.contains("1234,567890123;2026-11-09\r\n"));
        assertFalse(content.contains("1.234"));
        CsvDocument parsed = new CsvReader().read(file, CsvReadOptions.semicolonSeparated());
        assertEquals("000123", parsed.rows().getFirst().requiredValue("Codice"));
        assertEquals(description, parsed.rows().getFirst().requiredValue("Descrizione"));
        assertEquals("1234,567890123", parsed.rows().getFirst().requiredValue("Costo (EUR)"));
        assertEquals(file.toAbsolutePath(), result.path());
        assertEquals(1, result.exportedRows());
    }

    @Test
    void keepsMissingValuesEmptyAndZeroNumericWithoutClosingCallerWriter() {
        class TrackingWriter extends StringWriter {
            boolean closed;

            @Override
            public void close() {
                closed = true;
            }
        }
        TrackingWriter target = new TrackingWriter();
        long count =
                prices.exportWriter(
                        target,
                        List.of(
                                new Price("A", "", null, null),
                                new Price("B", "Zero", BigDecimal.ZERO, LocalDate.of(2026, 11, 9))),
                        CsvWriteOptions.commaSeparated());

        assertEquals(2, count);
        assertFalse(target.closed);
        assertTrue(target.toString().contains("\"A\",\"\",,\r\n"));
        assertTrue(target.toString().contains("\"B\",\"Zero\",0,2026-11-09\r\n"));
        assertFalse(target.toString().startsWith("\uFEFF"));
    }

    @Test
    void protectsSpreadsheetTextWithoutChangingNegativeNumbers() {
        StringWriter target = new StringWriter();
        new CsvWriter()
                .write(
                        target,
                        List.of("Testo", "Numero"),
                        List.of(
                                List.of("=1+1", new BigDecimal("-12.50")),
                                List.of("  @SUM(A1)", -3),
                                List.of("+Comando", 2)),
                        CsvWriteOptions.excelItalian());
        CsvDocument document =
                new CsvReader()
                        .read(
                                new StringReader(target.toString()),
                                CsvReadOptions.semicolonSeparated());
        assertEquals("'=1+1", document.rows().get(0).requiredValue("Testo"));
        assertEquals("-12,50", document.rows().get(0).requiredValue("Numero"));
        assertEquals("'  @SUM(A1)", document.rows().get(1).requiredValue("Testo"));
        assertEquals("-3", document.rows().get(1).requiredValue("Numero"));
        assertEquals("'+Comando", document.rows().get(2).requiredValue("Testo"));
    }

    @Test
    void supportsCommaDelimitedNumbersAndCustomTextQuote() {
        StringWriter target = new StringWriter();
        CsvWriteOptions options =
                new CsvWriteOptions(',', '\'', StandardCharsets.UTF_8, false, ',', false);
        new CsvWriter()
                .write(
                        target,
                        List.of("Testo", "Valore"),
                        List.of(List.of("l'oro, è", new BigDecimal("10.25"))),
                        options);
        assertTrue(target.toString().contains("'l''oro, è','10,25'\r\n"));
        CsvDocument parsed =
                new CsvReader()
                        .read(
                                new StringReader(target.toString()),
                                new CsvReadOptions(
                                        ',', '\'', true, false, false, StandardCharsets.UTF_8));
        assertEquals("l'oro, è", parsed.rows().getFirst().requiredValue("Testo"));
        assertEquals("10,25", parsed.rows().getFirst().requiredValue("Valore"));
    }

    @Test
    void consumesSourceOnceAndMapsRowsLazily() {
        AtomicInteger mapped = new AtomicInteger();
        AtomicInteger iterators = new AtomicInteger();
        Iterable<Integer> source =
                () -> {
                    assertEquals(1, iterators.incrementAndGet());
                    return new Iterator<>() {
                        int current;

                        @Override
                        public boolean hasNext() {
                            return current < 10_000;
                        }

                        @Override
                        public Integer next() {
                            assertEquals(current, mapped.get());
                            return current++;
                        }
                    };
                };
        CsvExporter<Integer> exporter =
                new CsvExporter<>(
                        new CsvWriter(),
                        new CsvExportMapper<>() {
                            @Override
                            public List<String> headers() {
                                return List.of("Numero");
                            }

                            @Override
                            public List<?> map(Integer value) {
                                mapped.incrementAndGet();
                                return List.of(value);
                            }
                        });
        assertEquals(
                10_000,
                exporter.exportWriter(Writer.nullWriter(), source, CsvWriteOptions.excelItalian()));
        assertEquals(10_000, mapped.get());
    }

    @Test
    void failedMappingKeepsExistingFileAndCleansTemporaryFile() throws IOException {
        Path file = directory.resolve("costi.csv");
        Files.writeString(file, "previous content");
        CsvExportException error =
                assertThrows(
                        CsvExportException.class,
                        () ->
                                malformed()
                                        .exportFile(
                                                file,
                                                List.of(1, 2),
                                                CsvWriteOptions.excelItalian()));
        assertEquals(3, error.recordNumber());
        assertEquals("previous content", Files.readString(file));
        try (var files = Files.list(directory)) {
            assertEquals(List.of(file), files.toList());
        }
    }

    @Test
    void failedExportDoesNotLeaveANewPartialFileAndSuccessfulExportReplacesWholeFile()
            throws IOException {
        Path file = directory.resolve("costi.csv");
        assertThrows(
                CsvExportException.class,
                () -> malformed().exportFile(file, List.of(1, 2), CsvWriteOptions.excelItalian()));
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }

        Files.writeString(file, "old content that must disappear completely");
        assertEquals(
                0,
                prices.exportFile(file, List.of(), CsvWriteOptions.commaSeparated())
                        .exportedRows());
        assertEquals(
                "\"Codice\",\"Descrizione\",\"Costo (EUR)\",\"Data prezzi\"\r\n",
                Files.readString(file));
    }

    @Test
    void rejectsMalformedHeadersAndUnsupportedOrNonfiniteValues() {
        CsvWriter writer = new CsvWriter();
        for (List<String> headers : List.of(List.<String>of(), List.of(""), List.of("A", "A"))) {
            assertEquals(
                    1,
                    assertThrows(
                                    CsvExportException.class,
                                    () ->
                                            writer.write(
                                                    new StringWriter(),
                                                    headers,
                                                    List.of(),
                                                    CsvWriteOptions.excelItalian()))
                            .recordNumber());
        }
        for (Object value : List.of(Double.NaN, Float.POSITIVE_INFINITY, new Object())) {
            assertEquals(
                    2,
                    assertThrows(
                                    CsvExportException.class,
                                    () ->
                                            writer.write(
                                                    new StringWriter(),
                                                    List.of("A"),
                                                    List.of(List.of(value)),
                                                    CsvWriteOptions.excelItalian()))
                            .recordNumber());
        }
    }

    @Test
    void reportsDiskWriterAndEncodingErrorsWithoutReplacingFile() throws IOException {
        Writer failing =
                new Writer() {
                    @Override
                    public void write(char[] c, int start, int count) throws IOException {
                        throw new IOException("Disk full");
                    }

                    @Override
                    public void flush() {}

                    @Override
                    public void close() {}
                };
        assertInstanceOf(
                IOException.class,
                assertThrows(
                                CsvExportException.class,
                                () ->
                                        prices.exportWriter(
                                                failing, List.of(), CsvWriteOptions.excelItalian()))
                        .getCause());

        Path file = directory.resolve("costi.csv");
        Files.writeString(file, "existing");
        CsvWriteOptions ascii =
                new CsvWriteOptions(';', '"', StandardCharsets.US_ASCII, false, '.', false);
        assertThrows(
                CsvExportException.class,
                () ->
                        prices.exportFile(
                                file, List.of(new Price("A", "è", BigDecimal.ONE, null)), ascii));
        assertEquals("existing", Files.readString(file));
        assertThrows(
                CsvExportException.class,
                () ->
                        prices.exportFile(
                                directory.resolve("missing/costi.csv"),
                                List.of(),
                                CsvWriteOptions.excelItalian()));
    }

    @Test
    void rejectsInvalidFormatConventions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new CsvWriteOptions(';', ';', null, false, '.', true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CsvWriteOptions('\n', '"', null, false, '.', true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CsvWriteOptions(';', '"', null, false, ':', true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CsvWriteOptions(';', '"', StandardCharsets.US_ASCII, true, '.', true));
    }

    @Test
    void mappingExceptionReportsTheFailingRecordAndKeepsExistingFile() throws IOException {
        Path file = directory.resolve("mapping-error.csv");
        Files.writeString(file, "existing");
        CsvExporter<Integer> exporter =
                new CsvExporter<>(
                        new CsvWriter(),
                        new CsvExportMapper<>() {
                            @Override
                            public List<String> headers() {
                                return List.of("Valore");
                            }

                            @Override
                            public List<?> map(Integer value) {
                                if (value == 2)
                                    throw new IllegalStateException("Missing cost calculation");
                                return List.of(value);
                            }
                        });
        CsvExportException failure =
                assertThrows(
                        CsvExportException.class,
                        () ->
                                exporter.exportFile(
                                        file, List.of(1, 2, 3), CsvWriteOptions.excelItalian()));
        assertEquals(3, failure.recordNumber());
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals("existing", Files.readString(file));
        try (var files = Files.list(directory)) {
            assertEquals(List.of(file), files.toList());
        }
    }

    private CsvExporter<Integer> malformed() {
        return new CsvExporter<>(
                new CsvWriter(),
                new CsvExportMapper<>() {
                    @Override
                    public List<String> headers() {
                        return List.of("Valore");
                    }

                    @Override
                    public List<?> map(Integer value) {
                        return value == 1 ? List.of(value) : List.of(value, "too many columns");
                    }
                });
    }
}
