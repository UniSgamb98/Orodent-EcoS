package com.orodent.ecos.costi;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.costi.consumption.*;
import com.orodent.ecos.costi.consumption.Consumption.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

class SupplyProjectionTest {
    final LocalDate today = LocalDate.of(2026, 11, 10);

    BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    Series series(String stock, Map<LocalDate, BigDecimal> arrivals) {
        return new Series(
                new Powder(
                        1, "P", "Bianca", "BIANCA", stock == null ? null : n(stock), today, null),
                Basis.PRODUZIONI,
                new TreeMap<>(),
                n("10"),
                n("100"),
                true,
                new TreeMap<>(arrivals));
    }

    Request request() {
        return new Request(
                today.minusMonths(1), today.minusDays(1), today, 10, CalendarMode.CALENDARIO, true);
    }

    @Test
    void laterReceiptDoesNotHideEarlierShortage() {
        var s = series("20", Map.of(today.plusDays(5), n("200")));
        var balance = SupplyProjection.forecast(s, request());
        assertEquals(today.plusDays(3), balance.firstShortage());
        assertEquals(0, n("20").compareTo(balance.buy()));
        assertEquals(0, n("120").compareTo(balance.residual()));
        assertEquals(0, n("2").compareTo(balance.beforeDays()));
    }

    @Test
    void planUsesProductionDateAndReceiptsWithoutMovingStock() {
        var s = series("100", Map.of(today.plusDays(3), n("100")));
        var b = SupplyProjection.order(s, request(), today.plusDays(4), n("50"));
        assertEquals(0, n("160").compareTo(b.current()));
        assertEquals(0, n("110").compareTo(b.residual()));
        assertEquals(0, n("11").compareTo(b.afterDays()));
        assertEquals(0, n("100").compareTo(s.powder().stock()));
    }

    @Test
    void unknownStockIsNotReplacedByIncomingQuantity() {
        var b =
                SupplyProjection.forecast(
                        series(null, Map.of(today.plusDays(1), n("100"))), request());
        assertNull(b.current());
        assertNull(b.residual());
        assertNull(b.buy());
    }
}
