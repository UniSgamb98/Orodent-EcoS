package com.orodent.ecos.core.csv;

/** Record numbers include the header (1), independent of embedded newlines in text cells. */
public final class CsvExportException extends IllegalArgumentException {
    private final long recordNumber;

    public CsvExportException(long recordNumber, String message, Throwable cause) {
        super(message, cause);
        this.recordNumber = recordNumber;
    }

    public long recordNumber() {
        return recordNumber;
    }
}
