package com.orodent.ecos.core.csv;

import java.util.Set;

@FunctionalInterface
public interface CsvRowMapper<T> {

    T map(CsvRow row);

    default Set<String> requiredColumns() {
        return Set.of();
    }
}
