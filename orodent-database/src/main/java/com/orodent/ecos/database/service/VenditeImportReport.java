package com.orodent.ecos.database.service;

import com.orodent.ecos.core.csv.CsvImportResult;

import java.time.LocalDate;
import java.util.List;

public record VenditeImportReport(
        LocalDate fromDate,
        LocalDate throughDate,
        int totalRows,
        int deletedRows,
        int insertedRows,
        List<CsvImportResult.RowError> rowErrors,
        List<CsvImportResult.DocumentError> documentErrors
) {

    public VenditeImportReport {
        if (totalRows < 0 || deletedRows < 0 || insertedRows < 0) {
            throw new IllegalArgumentException("I contatori di importazione non possono essere negativi");
        }
        if ((fromDate == null) != (throughDate == null)) {
            throw new IllegalArgumentException("L'intervallo importato deve avere entrambe le date");
        }
        if (fromDate != null && throughDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("L'intervallo importato non è valido");
        }
        rowErrors = List.copyOf(rowErrors);
        documentErrors = List.copyOf(documentErrors);
    }

    public boolean imported() {
        return rowErrors.isEmpty()
                && documentErrors.isEmpty()
                && fromDate != null
                && throughDate != null
                && insertedRows == totalRows;
    }
}
