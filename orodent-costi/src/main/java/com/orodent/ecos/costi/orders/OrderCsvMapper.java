package com.orodent.ecos.costi.orders;

import com.orodent.ecos.core.csv.*;
import com.orodent.ecos.costi.orders.PowderOrders.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.*;
import java.util.*;

public final class OrderCsvMapper implements CsvRowMapper<Row> {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    public Set<String> requiredColumns() {
        return Set.of(
                "Cons. prev.",
                "Data ordine",
                "Ordine",
                "Pos.",
                "Fornitore",
                "Articolo",
                "Descrizione",
                "Un. Mis.",
                "Quantita'",
                "UnitValue",
                "Consegnata",
                "Da consegn.");
    }

    public Row map(CsvRow row) {
        String unit =
                row.requiredValue("Un. Mis.").strip().replace(".", "").toUpperCase(Locale.ROOT);
        if (!unit.equals("KG"))
            throw new CsvMappingException(
                    "Un. Mis.", unit, "Unità non supportata: occorrono quantità in kg.");
        int position;
        try {
            position = Integer.parseInt(row.requiredValue("Pos.").strip());
        } catch (NumberFormatException e) {
            throw new CsvMappingException(
                    "Pos.", row.requiredValue("Pos."), "Posizione: numero intero positivo.");
        }
        try {
            return new Row(
                    new Key(row.requiredValue("Fornitore"), row.requiredValue("Ordine"), position),
                    date(row, "Data ordine", false),
                    date(row, "Cons. prev.", true),
                    row.requiredValue("Articolo"),
                    row.requiredValue("Descrizione"),
                    number(row, "Quantita'"),
                    number(row, "Consegnata"),
                    number(row, "Da consegn."),
                    number(row, "UnitValue"));
        } catch (CsvMappingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CsvMappingException("Riga", null, e.getMessage());
        }
    }

    private static LocalDate date(CsvRow row, String col, boolean optional) {
        String text = row.requiredValue(col).strip();
        if (text.isEmpty() && optional) return null;
        try {
            return LocalDate.parse(text, DATE);
        } catch (DateTimeParseException e) {
            throw new CsvMappingException(col, text, "Data non valida: usa gg/mm/aaaa.");
        }
    }

    private static BigDecimal number(CsvRow row, String col) {
        String text = row.requiredValue(col).strip();
        if (!text.matches("[+]?[0-9]+([.,][0-9]+)?"))
            throw new CsvMappingException(
                    col,
                    text,
                    "Numero non valido: usa virgola o punto decimale, senza separatori delle"
                            + " migliaia.");
        return new BigDecimal(text.replace(',', '.'));
    }
}
