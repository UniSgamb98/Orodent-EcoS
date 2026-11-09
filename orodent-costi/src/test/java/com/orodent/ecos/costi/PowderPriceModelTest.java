package com.orodent.ecos.costi;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.costi.model.PowderPrices;
import com.orodent.ecos.costi.model.PowderPrices.*;
import com.orodent.ecos.costi.view.PriceFormats;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

class PowderPriceModelTest {
    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    @Test
    void weightsByQuantityAndDoesNotRoundEachPurchaseBeforeAveraging() {
        var lots = List.of(new Lot("L1", n("100"), n("80")), new Lot("L2", n("50"), n("100")));
        var total = PowderPrices.aggregate(lots);
        assertEquals(0, n("150").compareTo(total.quantity()));
        assertEquals(0, n("13000").compareTo(total.value()));
        assertEquals(n("86.66666667"), total.unitPrice());
        var small =
                PowderPrices.aggregate(
                        List.of(
                                new Lot("", n("0.001"), n("0.0001")),
                                new Lot("", n("0.002"), n("0.0002"))));
        assertEquals(0, n("0.0000005").compareTo(small.value()));
        assertEquals(n("0.00016667"), small.unitPrice());
    }

    @Test
    void distinguishesUnknownQuantityFromZeroAndRetainsTheOtherMode() {
        var lots =
                List.of(new Lot("STESSO", n("10"), n("100")), new Lot("STESSO", n("5"), n("80")));
        var simple = new Draft(Mode.SEMPLICE, LocalDate.now(), n("90"), null, lots);
        assertNull(simple.quantity());
        assertEquals(0, n("90").compareTo(simple.unitPrice()));
        assertEquals(lots, simple.lots());
        var advanced =
                new Draft(
                        Mode.AVANZATA,
                        LocalDate.now(),
                        simple.simplePrice(),
                        simple.simpleQuantity(),
                        lots);
        assertEquals(0, n("15").compareTo(advanced.quantity()));
        assertEquals(0, n("90").compareTo(advanced.simplePrice()));
        assertNull(
                PowderPrices.aggregate(List.of(new Lot("Esaurito", BigDecimal.ZERO, n("80"))))
                        .unitPrice());
    }

    @Test
    void rejectsInexactOrInvalidValuesWithoutSilentlyRoundingInputs() {
        assertThrows(IllegalArgumentException.class, () -> new Lot("L", n("1.0001"), n("80")));
        assertThrows(IllegalArgumentException.class, () -> new Lot("L", n("1"), n("80.00001")));
        assertThrows(IllegalArgumentException.class, () -> new Lot("L", n("-1"), n("80")));
        assertThrows(IllegalArgumentException.class, () -> new Lot("L", n("1"), n("0")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Draft(Mode.SEMPLICE, LocalDate.now(), null, null, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Draft(Mode.AVANZATA, LocalDate.now(), null, null, List.of()));
        assertThrows(
                IllegalArgumentException.class, () -> new Lot("L", n("9999999999999"), n("80")));
        assertEquals(n("1.25"), PriceFormats.decimal("1,25", "Prezzo"));
        assertEquals(n("1.25"), PriceFormats.decimal("1.25", "Prezzo"));
        assertThrows(
                IllegalArgumentException.class, () -> PriceFormats.decimal("1.000,25", "Prezzo"));
        assertNull(PriceFormats.decimal("", "Quantità"));
    }
}
