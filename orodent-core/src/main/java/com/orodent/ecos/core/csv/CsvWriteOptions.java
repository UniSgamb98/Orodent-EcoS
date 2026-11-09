package com.orodent.ecos.core.csv;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** File conventions only; columns and business calculations belong to the exporting module. */
public record CsvWriteOptions(
        char delimiter,
        char quote,
        Charset charset,
        boolean utf8Bom,
        char decimalSeparator,
        boolean protectSpreadsheetFormulas) {
    public CsvWriteOptions {
        charset = charset == null ? StandardCharsets.UTF_8 : charset;
        if (delimiter == quote || invalid(delimiter) || invalid(quote)) {
            throw new IllegalArgumentException("Separatore e virgolette CSV non validi");
        }
        if (decimalSeparator != '.' && decimalSeparator != ',') {
            throw new IllegalArgumentException(
                    "Il separatore decimale deve essere punto o virgola");
        }
        if (utf8Bom && !charset.equals(StandardCharsets.UTF_8)) {
            throw new IllegalArgumentException("Il BOM UTF-8 richiede la codifica UTF-8");
        }
    }

    /** Italian Excel: semicolons, decimal comma, UTF-8 BOM, Windows line endings. */
    public static CsvWriteOptions excelItalian() {
        return new CsvWriteOptions(';', '"', StandardCharsets.UTF_8, true, ',', true);
    }

    /** Interchange format with decimal point and no BOM. */
    public static CsvWriteOptions commaSeparated() {
        return new CsvWriteOptions(',', '"', StandardCharsets.UTF_8, false, '.', true);
    }

    private static boolean invalid(char value) {
        return value == '\r' || value == '\n' || value == '\0' || value == '\uFEFF';
    }
}
