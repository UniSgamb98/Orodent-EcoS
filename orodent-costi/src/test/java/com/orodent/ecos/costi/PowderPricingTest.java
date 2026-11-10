package com.orodent.ecos.costi;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.costi.products.model.PowderPricing;
import com.orodent.ecos.costi.products.model.PowderPricing.*;
import com.orodent.ecos.costi.products.model.ProductCosts.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

class PowderPricingTest {
    final LocalDate today = LocalDate.of(2026, 11, 10);

    BigDecimal n(String s) {
        return new BigDecimal(s);
    }

    Stock stock(String ref, String total, String allocated, String value, List<Receipt> orders) {
        return new Stock(
                2,
                today,
                ref == null ? null : n(ref),
                total == null ? null : n(total),
                n(allocated),
                n(value),
                orders);
    }

    void same(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, n(expected).compareTo(actual));
    }

    @Test
    void stockInformationDistinguishesUnknownAndZeroInSimpleMode() {
        var selection = new Selection(Mode.SEMPLICE, false);
        assertTrue(
                PowderPricing.value(1, stock("100", null, "0", "0", List.of()), selection, today)
                        .details()
                        .contains("magazzino: Non dichiarata"));
        assertTrue(
                PowderPricing.value(1, stock("100", "0", "0", "0", List.of()), selection, today)
                        .details()
                        .contains("magazzino: 0 kg"));
    }

    @Test
    void referenceAndWeightedPricesRemainSeparateAndPartialStockRaisesWarning() {
        var stock = stock("100", "100", "80", "6400", List.of());
        var simple = PowderPricing.value(1, stock, new Selection(Mode.SEMPLICE, true), today);
        same("100", simple.price());
        assertFalse(simple.estimated());
        assertTrue(simple.details().contains("Disponibilità dichiarata a magazzino: 100 kg"));
        assertFalse(new Selection(Mode.SEMPLICE, true).includeOrders());
        var advanced = PowderPricing.value(1, stock, new Selection(Mode.AVANZATA, false), today);
        same("84", advanced.price());
        assertTrue(advanced.estimated());
        assertTrue(advanced.details().contains("20 kg × 100 €/kg"));
        assertTrue(advanced.details().contains("8400 € ÷ 100 kg"));
        var complete =
                PowderPricing.value(
                        1,
                        stock(null, "80", "80", "6400", List.of()),
                        new Selection(Mode.AVANZATA, false),
                        today);
        same("80", complete.price());
        assertFalse(complete.estimated());
    }

    @Test
    void zeroUnknownAndOverAllocatedDoNotProduceInventedPrices() {
        for (Stock stock :
                List.of(
                        stock("100", null, "10", "1000", List.of()),
                        stock("100", "5", "10", "1000", List.of()),
                        stock(null, "100", "80", "6400", List.of()),
                        stock("100", "0", "0", "0", List.of()))) {
            var price = PowderPricing.value(1, stock, new Selection(Mode.AVANZATA, false), today);
            assertNull(price.price());
            assertFalse(price.estimated());
            assertTrue(price.details().contains("incompleto"));
        }
    }

    @Test
    void ordersAddOnlyOutstandingDatedAmountsAndAreOptional() {
        var receipts =
                List.of(
                        new Receipt(1, 2, "A / 1", today, n("20"), n("150")),
                        new Receipt(2, 1, "B / 1", today.plusDays(100), n("80"), n("50")),
                        new Receipt(3, 1, "C / 1", today.minusDays(1), n("999"), n("1")),
                        new Receipt(4, 1, "D / 1", null, n("999"), n("1")));
        var stock = stock("100", "100", "80", "6400", receipts);
        same(
                "84",
                PowderPricing.value(1, stock, new Selection(Mode.AVANZATA, false), today).price());
        var with = PowderPricing.value(1, stock, new Selection(Mode.AVANZATA, true), today);
        same("77", with.price());
        assertTrue(with.details().contains("escluse: 2"));
        assertTrue(with.details().contains("revisione 2"));
        same(
                "150",
                PowderPricing.value(
                                1,
                                stock(null, "0", "0", "0", List.of(receipts.getFirst())),
                                new Selection(Mode.AVANZATA, true),
                                today)
                        .price());
    }

    @Test
    void policySwitchingIsReversibleAndKeepsUnsavedOrSavedTransformationParameters() {
        var stock = stock("100", "100", "80", "6400", List.of());
        var raw =
                new Catalogue(
                        Instant.now(),
                        List.of(),
                        List.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(1L, new PowderPrice(1, 2, today, "RIFERIMENTO", n("100"))),
                        Map.of(),
                        Map.of(1L, stock),
                        new Selection(Mode.SEMPLICE, false));
        var advanced = PowderPricing.apply(raw, new Selection(Mode.AVANZATA, false));
        same("84", advanced.prices().get(1L).price());
        advanced = advanced.withParameters(1, new Parameters(1, n("30"), Map.of()));
        assertEquals(Mode.AVANZATA, advanced.pricing().mode());
        var simple = PowderPricing.apply(advanced, new Selection(Mode.SEMPLICE, false));
        same("100", simple.prices().get(1L).price());
        same("30", simple.parameters(1).commonCost());
        same("100", raw.prices().get(1L).price());
    }
}
