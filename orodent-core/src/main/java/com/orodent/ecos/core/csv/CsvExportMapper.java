package com.orodent.ecos.core.csv;

import java.util.List;

/**
 * Export counterpart of CsvRowMapper. Headers have a stable order and include measurement units.
 * Return typed numbers (preferably BigDecimal), dates, booleans, strings or null, never UI labels.
 * Null cells are empty; zero remains a numeric zero. Each row must match the header count.
 */
public interface CsvExportMapper<T> {
    List<String> headers();

    List<?> map(T value);
}
