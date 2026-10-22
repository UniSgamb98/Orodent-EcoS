package com.orodent.tonv3.core.csv;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public record CsvReadOptions(
        char delimiter,
        char quote,
        boolean firstRowIsHeader,
        boolean trimValues,
        boolean ignoreEmptyLines,
        Charset charset
) {

    public CsvReadOptions {
        if (delimiter == quote) {
            throw new IllegalArgumentException("Separatore e delimitatore di testo devono essere diversi");
        }
        if (isLineBreak(delimiter) || isLineBreak(quote)) {
            throw new IllegalArgumentException("Separatore e delimitatore di testo non possono essere ritorni a capo");
        }
        charset = charset == null ? StandardCharsets.UTF_8 : charset;
    }

    public static CsvReadOptions semicolonSeparated() {
        return new CsvReadOptions(';', '"', true, true, true, StandardCharsets.UTF_8);
    }

    public static CsvReadOptions commaSeparated() {
        return new CsvReadOptions(',', '"', true, true, true, StandardCharsets.UTF_8);
    }

    private static boolean isLineBreak(char value) {
        return value == '\n' || value == '\r';
    }
}
