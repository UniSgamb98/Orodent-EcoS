package com.orodent.ecos.database.csv;

import com.orodent.ecos.core.csv.CsvMappingException;
import com.orodent.ecos.core.csv.CsvRow;
import com.orodent.ecos.core.csv.CsvRowMapper;
import com.orodent.ecos.database.model.TipoOperazione;
import com.orodent.ecos.database.model.VenditaDettaglio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.Set;

public final class VenditaDettaglioCsvMapper implements CsvRowMapper<VenditaDettaglio> {

    public static final String DATA_REGISTRAZIONE = "Data reg.";
    public static final String NUMERO_DOCUMENTO = "Nr. doc.";
    public static final String SORGENTE = "MA_InventoryEntries_StoragePhase1";
    public static final String CAUSALE = "MA_InventoryReasons_Reason";
    public static final String CODICE_CLIENTE = "MA_InventoryEntries_CustSupp";
    public static final String CODICE_ARTICOLO = "Articolo";
    public static final String DESCRIZIONE = "Descrizione";
    public static final String QUANTITA = "Quantità";
    public static final String PREZZO_UNITARIO = "Valore unitario";
    public static final String SCONTO = "Sconto";
    public static final String IMPORTO_SCONTO = "Imp. sconto";
    public static final String IMPORTO_RIGA = "Importo riga";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/uuuu", Locale.ITALY)
            .withResolverStyle(ResolverStyle.STRICT);

    private static final Set<String> REQUIRED_COLUMNS = Set.of(
            DATA_REGISTRAZIONE,
            NUMERO_DOCUMENTO,
            SORGENTE,
            CAUSALE,
            CODICE_CLIENTE,
            CODICE_ARTICOLO,
            DESCRIZIONE,
            QUANTITA,
            PREZZO_UNITARIO,
            SCONTO,
            IMPORTO_SCONTO,
            IMPORTO_RIGA
    );

    @Override
    public VenditaDettaglio map(CsvRow row) {
        String numeroDocumento = requiredText(row, NUMERO_DOCUMENTO);
        LocalDate dataVendita = parseDate(row, DATA_REGISTRAZIONE);

        return new VenditaDettaglio(
                null,
                requiredText(row, SORGENTE),
                documentId(dataVendita, numeroDocumento),
                numeroDocumento,
                row.lineNumber() - 1,
                dataVendita,
                requiredText(row, CODICE_CLIENTE),
                requiredText(row, CODICE_ARTICOLO),
                requiredText(row, DESCRIZIONE),
                null,
                parseDecimal(row, QUANTITA, 3),
                parseDecimal(row, PREZZO_UNITARIO, 4),
                parseDiscount(row, SCONTO),
                parseDecimal(row, IMPORTO_SCONTO, 2),
                parseDecimal(row, IMPORTO_RIGA, 2),
                parseOperation(row, CAUSALE),
                null,
                null,
                null
        );
    }

    private String documentId(LocalDate saleDate, String documentNumber) {
        return saleDate.getYear() + ":" + documentNumber;
    }

    @Override
    public Set<String> requiredColumns() {
        return REQUIRED_COLUMNS;
    }

    private String requiredText(CsvRow row, String column) {
        String value = row.requiredValue(column);
        if (value.isBlank()) {
            throw mappingError(column, value, "Il valore è obbligatorio");
        }
        return value;
    }

    private LocalDate parseDate(CsvRow row, String column) {
        String value = requiredText(row, column);
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new CsvMappingException(
                    column,
                    value,
                    "Data non valida, formato richiesto: gg/mm/aaaa",
                    exception
            );
        }
    }

    private BigDecimal parseDecimal(CsvRow row, String column, int scale) {
        String value = requiredText(row, column);
        try {
            return new BigDecimal(value.replace(',', '.'))
                    .setScale(scale, RoundingMode.HALF_UP)
                    .stripTrailingZeros();
        } catch (NumberFormatException exception) {
            throw new CsvMappingException(column, value, "Numero decimale non valido", exception);
        }
    }

    private BigDecimal parseDiscount(CsvRow row, String column) {
        String value = row.requiredValue(column);
        if (value.isBlank()) {
            return BigDecimal.ZERO;
        }

        String expression = value.trim();
        if (expression.startsWith("+")) {
            expression = expression.substring(1).trim();
        }
        if (expression.isEmpty()) {
            throw mappingError(column, value, "Espressione di sconto non valida");
        }

        BigDecimal remainingPercentage = BigDecimal.valueOf(100);
        for (String component : expression.split("\\+", -1)) {
            BigDecimal percentage = parseDiscountComponent(column, value, component);
            remainingPercentage = remainingPercentage
                    .multiply(BigDecimal.valueOf(100).subtract(percentage))
                    .divide(BigDecimal.valueOf(100));
        }

        return BigDecimal.valueOf(100)
                .subtract(remainingPercentage)
                .setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }

    private BigDecimal parseDiscountComponent(String column, String rawValue, String component) {
        try {
            String number = component.trim();
            if (number.endsWith("%")) {
                number = number.substring(0, number.length() - 1).trim();
            }
            BigDecimal percentage = new BigDecimal(number.replace(',', '.'));
            if (percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw mappingError(column, rawValue, "Ogni percentuale di sconto deve essere compresa tra 0 e 100");
            }
            return percentage;
        } catch (NumberFormatException exception) {
            throw new CsvMappingException(column, rawValue, "Espressione di sconto non valida", exception);
        }
    }

    private TipoOperazione parseOperation(CsvRow row, String column) {
        String value = requiredText(row, column).toUpperCase(Locale.ROOT);
        if (value.equals("RESO") || value.startsWith("RES-")) {
            return TipoOperazione.RESO;
        }
        if (value.equals("NOTA_CREDITO") || value.equals("NOTA CREDITO") || value.startsWith("NC-")) {
            return TipoOperazione.NOTA_CREDITO;
        }

        // La causale proviene dal gestionale e può contenere codici aziendali
        // (ad esempio VEND, VEN-O o VMAG10). Se non identifica esplicitamente
        // un reso o una nota di credito, la riga rappresenta una vendita.
        return TipoOperazione.VENDITA;
    }

    private CsvMappingException mappingError(String column, String rawValue, String message) {
        return new CsvMappingException(column, rawValue, message);
    }
}
