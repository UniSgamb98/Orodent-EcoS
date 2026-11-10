package com.orodent.ecos.costi.products.model;

import com.orodent.ecos.costi.products.model.ProductCosts.*;

import java.math.*;
import java.time.*;
import java.util.*;

/** Pure valuation policy shared by product costs and future scenario snapshots. */
public final class PowderPricing {
    private PowderPricing() {}

    public enum Mode {
        SEMPLICE,
        AVANZATA;

        public String toString() {
            return this == SEMPLICE ? "Semplice · Riferimento" : "Avanzata · Media ponderata";
        }
    }

    public record Selection(Mode mode, boolean includeOrders) {
        public Selection {
            Objects.requireNonNull(mode);
            includeOrders = mode == Mode.AVANZATA && includeOrders;
        }

        public String label() {
            return mode + (includeOrders ? " + ordini in arrivo" : "");
        }
    }

    public record Receipt(
            long id,
            int version,
            String document,
            LocalDate date,
            BigDecimal quantity,
            BigDecimal price) {}

    public record Stock(
            int version,
            LocalDate date,
            BigDecimal reference,
            BigDecimal total,
            BigDecimal allocated,
            BigDecimal lotValue,
            List<Receipt> receipts) {
        public Stock {
            receipts = List.copyOf(receipts);
        }
    }

    public static PowderPrice value(
            long powder, Stock stock, Selection selection, LocalDate today) {
        StringBuilder details =
                new StringBuilder("Fonte: ")
                        .append(selection.label())
                        .append("\nFotografia magazzino: v")
                        .append(stock.version())
                        .append(" · ")
                        .append(stock.date() == null ? "Non compilata" : stock.date());
        details.append("\nDisponibilità dichiarata a magazzino: ")
                .append(stock.total() == null ? "Non dichiarata" : n(stock.total()) + " kg")
                .append(
                        "\n"
                            + "Quantità della fotografia manuale; non aggiornata automaticamente"
                            + " dai consumi.");
        if (selection.mode() == Mode.SEMPLICE)
            return new PowderPrice(
                    powder,
                    stock.version(),
                    stock.date(),
                    selection.label(),
                    stock.reference(),
                    false,
                    details + "\nPrezzo di riferimento: " + n(stock.reference()) + " €/kg");
        BigDecimal total = stock.total(), allocated = stock.allocated();
        details.append("\nQuantità nei lotti: ")
                .append(n(allocated))
                .append(" kg\nValore dei lotti: ")
                .append(n(stock.lotValue()))
                .append(" €");
        String error =
                total == null
                        ? "Quantità totale non dichiarata."
                        : allocated.compareTo(total) > 0
                                ? "I lotti superano il totale dichiarato."
                                : null;
        BigDecimal unassigned = total == null ? null : total.subtract(allocated);
        if (unassigned != null)
            details.append("\nNon assegnata ai lotti: ").append(n(unassigned)).append(" kg");
        if (error == null && unassigned.signum() > 0 && stock.reference() == null)
            error = "Manca il prezzo di riferimento per la quantità non assegnata.";
        BigDecimal quantity = total, value = stock.lotValue();
        if (error == null && unassigned.signum() > 0) {
            value = value.add(unassigned.multiply(stock.reference()));
            details.append("\nParte non assegnata valorizzata al riferimento: ")
                    .append(n(unassigned))
                    .append(" kg × ")
                    .append(n(stock.reference()))
                    .append(" €/kg");
        }
        int excluded = 0;
        if (selection.includeOrders())
            for (Receipt receipt : stock.receipts()) {
                if (receipt.date() == null || receipt.date().isBefore(today)) {
                    excluded++;
                    continue;
                }
                details.append("\nOrdine ")
                        .append(receipt.document())
                        .append(" · revisione ")
                        .append(receipt.version())
                        .append(" · ")
                        .append(receipt.date())
                        .append(": ")
                        .append(n(receipt.quantity()))
                        .append(" kg × ")
                        .append(n(receipt.price()))
                        .append(" €/kg");
                if (error == null) {
                    quantity = quantity.add(receipt.quantity());
                    value = value.add(receipt.quantity().multiply(receipt.price()));
                }
            }
        if (excluded > 0)
            details.append("\nRighe scadute o senza data escluse: ")
                    .append(excluded)
                    .append(". Aggiornare la data in Ordini in arrivo.");
        if (error == null && quantity.signum() == 0)
            error = "Nessuna quantità positiva da valorizzare.";
        BigDecimal price = error == null ? value.divide(quantity, 8, RoundingMode.HALF_UP) : null;
        if (error != null) details.append("\nCalcolo incompleto: ").append(error);
        else
            details.append("\nPrezzo applicato = ")
                    .append(n(value))
                    .append(" € ÷ ")
                    .append(n(quantity))
                    .append(" kg = ")
                    .append(n(price))
                    .append(" €/kg");
        return new PowderPrice(
                powder,
                stock.version(),
                stock.date(),
                selection.label(),
                price,
                error == null && unassigned.signum() > 0,
                details.toString());
    }

    public static Catalogue apply(Catalogue source, Selection selection) {
        LocalDate today = source.capturedAt().atZone(ZoneId.systemDefault()).toLocalDate();
        var stocks = new HashMap<>(source.stocks());
        source.prices()
                .forEach(
                        (id, p) ->
                                stocks.putIfAbsent(
                                        id,
                                        new Stock(
                                                p.version(),
                                                p.date(),
                                                p.price(),
                                                null,
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO,
                                                List.of())));
        var prices = new HashMap<Long, PowderPrice>();
        stocks.forEach((id, stock) -> prices.put(id, value(id, stock, selection, today)));
        return new Catalogue(
                source.capturedAt(),
                source.products(),
                source.articles(),
                source.specifications(),
                source.recipes(),
                source.models(),
                prices,
                source.parameters(),
                stocks,
                selection);
    }

    private static String n(BigDecimal value) {
        return value == null
                ? "Non disponibile"
                : value.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
