package com.orodent.ecos.costi.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Economic snapshots use powder identities, never duplicate technical revisions from TON. */
public final class PowderPrices {
    public static final int PAGE_SIZE = 40;
    public static final int MAX_LOTS = 500;

    private PowderPrices() {}

    public enum Mode {
        SEMPLICE("Semplice"),
        AVANZATA("Avanzata");
        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Filter {
        TUTTE("Tutte le modalità"),
        SEMPLICE("Semplice"),
        AVANZATA("Avanzata"),
        MANCANTI("Da compilare");
        private final String label;

        Filter(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record Powder(long id, String code, String name, String color, boolean active) {}

    public record Lot(String code, BigDecimal quantity, BigDecimal unitPrice) {
        public Lot {
            code = code == null ? "" : code.strip();
            if (code.length() > 100)
                throw new IllegalArgumentException("Lotto: massimo 100 caratteri.");
            quantity = decimal(quantity, 3, true, "Quantità del lotto");
            unitPrice = decimal(unitPrice, 4, false, "Prezzo del lotto");
            if (quantity == null || unitPrice == null)
                throw new IllegalArgumentException(
                        "Inserisci quantità e prezzo per ogni riga del lotto.");
        }

        public BigDecimal value() {
            return quantity.multiply(unitPrice);
        }
    }

    /** Both modes are retained so switching does not erase the alternative inputs. */
    public record Draft(
            Mode mode,
            LocalDate referenceDate,
            BigDecimal simplePrice,
            BigDecimal simpleQuantity,
            List<Lot> lots) {
        public Draft {
            Objects.requireNonNull(mode, "Modalità obbligatoria.");
            if (referenceDate == null
                    || referenceDate.getYear() < 1900
                    || referenceDate.getYear() > 9999)
                throw new IllegalArgumentException("Inserisci una data di riferimento valida.");
            simplePrice = decimal(simplePrice, 4, false, "Prezzo semplice");
            simpleQuantity = decimal(simpleQuantity, 3, true, "Quantità semplice");
            lots = List.copyOf(lots);
            if (lots.size() > MAX_LOTS)
                throw new IllegalArgumentException("Massimo 500 righe per polvere.");
            if (mode == Mode.SEMPLICE && simplePrice == null)
                throw new IllegalArgumentException("Inserisci il prezzo di riferimento in €/kg.");
            if (mode == Mode.AVANZATA && lots.isEmpty())
                throw new IllegalArgumentException(
                        "Inserisci almeno una riga nella modalità avanzata.");
        }

        public Totals totals() {
            return aggregate(lots);
        }

        public BigDecimal quantity() {
            return mode == Mode.SEMPLICE ? simpleQuantity : totals().quantity();
        }

        public BigDecimal unitPrice() {
            return mode == Mode.SEMPLICE ? simplePrice : totals().unitPrice();
        }
    }

    public record Totals(BigDecimal quantity, BigDecimal value, BigDecimal unitPrice) {}

    public static Totals aggregate(List<Lot> lots) {
        BigDecimal quantity = BigDecimal.ZERO, value = BigDecimal.ZERO;
        for (Lot lot : lots) {
            quantity = quantity.add(lot.quantity());
            value = value.add(lot.value());
        }
        return new Totals(
                quantity,
                value,
                quantity.signum() == 0 ? null : value.divide(quantity, 8, RoundingMode.HALF_UP));
    }

    public record Detail(Powder powder, int version, Draft draft) {}

    public record Row(
            Powder powder,
            Mode mode,
            LocalDate referenceDate,
            BigDecimal quantity,
            BigDecimal unitPrice) {}

    public record Page(List<Row> rows, int total, int offset) {
        public Page {
            rows = List.copyOf(rows);
        }

        public boolean hasNext() {
            return offset + rows.size() < total;
        }
    }

    public static final class Conflict extends RuntimeException {
        public Conflict() {
            super(
                    "I prezzi sono stati modificati da un altro operatore. Ricarica la scheda prima"
                            + " di salvare; le tue modifiche sono ancora visibili.");
        }
    }

    private static BigDecimal decimal(BigDecimal value, int scale, boolean zero, String label) {
        if (value == null) return null;
        if (zero ? value.signum() < 0 : value.signum() <= 0)
            throw new IllegalArgumentException(
                    label
                            + (zero
                                    ? ": usa un valore maggiore o uguale a zero."
                                    : ": usa un valore maggiore di zero."));
        try {
            if (value.setScale(scale, RoundingMode.UNNECESSARY).precision() > 15)
                throw new ArithmeticException();
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException(
                    label
                            + ": massimo "
                            + scale
                            + " decimali e "
                            + (15 - scale)
                            + " cifre intere, senza arrotondamenti automatici.");
        }
        return value.stripTrailingZeros();
    }
}
