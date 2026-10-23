package com.orodent.ecos.database.csv;

import com.orodent.ecos.core.csv.CsvMappingException;
import com.orodent.ecos.core.csv.CsvRow;
import com.orodent.ecos.core.csv.CsvRowMapper;
import com.orodent.ecos.database.model.Cliente;

import java.util.Locale;
import java.util.Set;

public final class ClienteCsvMapper implements CsvRowMapper<Cliente> {

    public static final String CODICE = "Codice";
    public static final String CODICE_ISO = "ISO_CODE";
    public static final String CATEGORIA = "Categoria";
    public static final String LISTINO = "PriceList";
    public static final String AGENTE = "Agente";
    public static final String TIPO_CLIENTE = "MA_CustSupp_CustSuppKind";
    public static final String RAGIONE_SOCIALE = "Ragione sociale";

    @Override
    public Cliente map(CsvRow row) {
        return new Cliente(
                required(row, CODICE),
                optional(row, CODICE_ISO, true),
                optional(row, CATEGORIA, false),
                optional(row, LISTINO, false),
                optional(row, AGENTE, false),
                optional(row, TIPO_CLIENTE, false),
                required(row, RAGIONE_SOCIALE),
                null
        );
    }

    @Override
    public Set<String> requiredColumns() {
        return Set.of(CODICE, CODICE_ISO, CATEGORIA, LISTINO, AGENTE, TIPO_CLIENTE, RAGIONE_SOCIALE);
    }

    private String required(CsvRow row, String column) {
        String value = row.requiredValue(column).trim();
        if (value.isEmpty()) {
            throw new CsvMappingException(column, value, "Il valore è obbligatorio");
        }
        return value;
    }

    private String optional(CsvRow row, String column, boolean uppercase) {
        String value = row.requiredValue(column).trim();
        if (value.isEmpty()) return null;
        return uppercase ? value.toUpperCase(Locale.ROOT) : value;
    }
}
