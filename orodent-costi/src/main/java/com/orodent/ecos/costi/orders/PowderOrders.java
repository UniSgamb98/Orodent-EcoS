package com.orodent.ecos.costi.orders;

import com.orodent.ecos.core.csv.CsvImportResult;

import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** Immutable imported rows. Supplier/order/position identify a line, never the powder lot. */
public final class PowderOrders {
    private PowderOrders() {}

    public record Key(String supplier, String order, int position) implements Comparable<Key> {
        public Key {
            supplier = text(supplier, 80, "Fornitore");
            order = text(order, 80, "Ordine");
            if (position < 1) throw new IllegalArgumentException("Posizione ordine non valida.");
        }

        public int compareTo(Key o) {
            int c = supplier.compareTo(o.supplier);
            if (c == 0) c = order.compareTo(o.order);
            return c == 0 ? Integer.compare(position, o.position) : c;
        }
    }

    public record Code(String supplier, String code) {}

    public record Row(
            Key key,
            LocalDate orderDate,
            LocalDate expected,
            String code,
            String description,
            BigDecimal ordered,
            BigDecimal delivered,
            BigDecimal remaining,
            BigDecimal price) {
        public Row {
            Objects.requireNonNull(key);
            Objects.requireNonNull(orderDate, "Data ordine obbligatoria.");
            code = text(code, 80, "Articolo").toUpperCase(Locale.ROOT);
            description = Objects.toString(description, "").strip();
            if (description.length() > 500)
                throw new IllegalArgumentException("Descrizione: massimo 500 caratteri.");
            ordered = number(ordered, 3, "Quantità ordinata");
            delivered = number(delivered, 3, "Consegnata");
            remaining = number(remaining, 3, "Da consegnare");
            price = number(price, 4, "Prezzo €/kg");
            if (remaining.compareTo(ordered) > 0
                    || delivered.add(remaining).compareTo(ordered) != 0
                            && !(remaining.signum() == 0 && delivered.compareTo(ordered) >= 0))
                throw new IllegalArgumentException(
                        "Quantità ordinate, consegnate e residue non coerenti.");
        }

        public Code source() {
            return new Code(key.supplier(), code);
        }
    }

    public record Powder(long id, String code, String name, String color) {
        public Powder {
            color = Objects.toString(color, "ALTRE").strip().toUpperCase(Locale.ROOT);
        }

        public Powder(long id, String code, String name) {
            this(id, code, name, "ALTRE");
        }

        @Override
        public String toString() {
            return code + " · " + name;
        }
    }

    public record Stored(
            long id,
            int version,
            long powderId,
            Row row,
            LocalDate overrideDate,
            boolean excluded) {
        public LocalDate date() {
            return overrideDate == null ? row.expected() : overrideDate;
        }

        public boolean open() {
            return !excluded && row.remaining().signum() > 0;
        }

        public String status(LocalDate today) {
            return excluded
                    ? "Escluso"
                    : row.remaining().signum() == 0
                            ? "Consegnato"
                            : date() == null
                                    ? "Data da compilare"
                                    : date().isBefore(today)
                                            ? "Scaduto · da sollecitare"
                                            : "Atteso";
        }
    }

    public record Preview(
            CsvImportResult<Row> csv,
            List<Powder> powders,
            Map<Code, Long> mappings,
            Map<Key, Stored> existing,
            List<String> problems) {
        public Preview {
            powders = List.copyOf(powders);
            mappings = Map.copyOf(mappings);
            existing = Map.copyOf(existing);
            problems = List.copyOf(problems);
        }

        public boolean valid() {
            return csv.valid() && problems.isEmpty() && !csv.validRows().isEmpty();
        }
    }

    public record Report(int created, int updated, int unchanged) {}

    public static final class Conflict extends IllegalStateException {
        public Conflict() {
            super(
                    "Gli ordini o i collegamenti sono cambiati dopo l'anteprima. Ricarica il CSV"
                            + " prima di importare.");
        }
    }

    private static String text(String value, int max, String label) {
        if (value == null || value.isBlank() || value.strip().length() > max)
            throw new IllegalArgumentException(
                    label + ": valore obbligatorio, massimo " + max + " caratteri.");
        return value.strip();
    }

    private static BigDecimal number(BigDecimal value, int scale, String label) {
        if (value == null || value.signum() < 0)
            throw new IllegalArgumentException(label + ": valore non negativo obbligatorio.");
        try {
            if (value.setScale(scale, RoundingMode.UNNECESSARY).precision() > 15)
                throw new ArithmeticException();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    label
                            + ": massimo "
                            + scale
                            + " decimali e "
                            + (15 - scale)
                            + " cifre intere.");
        }
        return value.stripTrailingZeros();
    }
}
