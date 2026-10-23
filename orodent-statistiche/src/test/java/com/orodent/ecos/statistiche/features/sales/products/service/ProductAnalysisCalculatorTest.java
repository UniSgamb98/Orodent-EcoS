package com.orodent.ecos.statistiche.features.sales.products.service;

import com.orodent.ecos.statistiche.features.sales.products.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProductAnalysisCalculatorTest {
    private final ProductAnalysisCalculator calculator = new ProductAnalysisCalculator();
    private ProductSale sale(String code, String date, String customer, String document, int revenue, int quantity, boolean sale) {
        return new ProductSale(code, code, LocalDate.parse(date), customer, "Cliente " + customer, document, sale, BigDecimal.valueOf(revenue), BigDecimal.valueOf(quantity));
    }
    private ProductSalesSnapshot snapshot(List<ProductSale> sales) {
        return new ProductSalesSnapshot(List.of(2026,2025), 2026, LocalDate.of(2026,10,7), LocalDate.of(2020,1,1), sales);
    }
    private List<ProductSale> sales() {
        return List.of(sale("ZR9812MSTA2","2025-01-01","C1","P1",100,10,true),
                sale("ZR9812MSTA2","2025-10-08","C1","P2",9000,900,true),
                sale("ZR9812MSTA2","2026-01-01","C1","N1",100,10,true),
                sale("ZR9518MSTA2","2026-01-01","C1","N1",200,20,true),
                sale("ZR9825MSTA3","2026-10-07","C2","N2",300,30,true),
                sale("ZR9812MSTA2","2026-10-07","C1","R1",-50,-5,false),
                sale("ZR9812MSTA2","2026-10-08","C3","FUTURE",9000,900,true),
                sale("ZS9812MSTA2","2026-01-01","C1","N1",70,7,true),
                sale("ZR9814T","2026-01-01","C1","N1",20,2,true),
                sale("ZR9814TA2","2026-01-01","C1","N1",30,3,true),
                sale("ZRPC","2026-01-01","C1","N1",500,50,true));
    }
    @Test void aggregatesDimensionsAndDeduplicatesCustomersAndOrdersAcrossCodes() {
        var page = calculator.overview(snapshot(sales()), ProductGrouping.FAMILY);
        var group = page.groups().stream().filter(g -> g.key().material().equals("ZR") && g.key().family() == ProductFamily.MST).findFirst().orElseThrow();
        assertEquals(BigDecimal.valueOf(550), group.current().revenue());
        assertEquals(BigDecimal.valueOf(55), group.current().quantity());
        assertEquals(2, group.current().customers()); assertEquals(2, group.current().orders());
        assertEquals(BigDecimal.valueOf(50), group.current().returnedRevenue());
        assertEquals(BigDecimal.valueOf(5), group.current().returnedQuantity());
        assertEquals(new BigDecimal("450.0"), group.revenueChange());
        assertEquals(BigDecimal.valueOf(100), group.previous().revenue());
        assertEquals(4, page.groups().size());
        assertEquals(2, page.classifiedTotals().customers()); assertEquals(2, page.classifiedTotals().orders());
        assertEquals(BigDecimal.valueOf(670), page.classifiedTotals().revenue());
        assertEquals("ZRPC", page.unclassified().getFirst().code());
        assertEquals(BigDecimal.valueOf(500), page.unclassifiedTotals().revenue());
    }
    @Test void colorGroupingSplitsOnlyColoredFamiliesAndPreservesTotals() {
        var data = snapshot(sales());
        var family = calculator.overview(data, ProductGrouping.FAMILY);
        var colors = calculator.overview(data, ProductGrouping.FAMILY_COLOR);
        assertEquals(family.classifiedTotals(), colors.classifiedTotals());
        assertEquals(5, colors.groups().size());
        assertEquals(1, colors.groups().stream().filter(g -> g.key().family() == ProductFamily.T).count());
        var a2 = colors.groups().stream().filter(g -> g.key().material().equals("ZR") && g.key().family() == ProductFamily.MST && g.key().color().equals("A2")).findFirst().orElseThrow();
        assertEquals(BigDecimal.valueOf(250), a2.current().revenue());
        var detail = calculator.detail(data, a2);
        assertFalse(detail.variants().containsKey(ProductVariantDimension.COLOR));
        assertEquals(2, detail.variants().get(ProductVariantDimension.DIAMETER).size());
    }
    @Test void detailUsesTheSameScopeAndRetainsMonthlyZerosAndExactCutoffs() {
        var data = snapshot(sales());
        var group = calculator.overview(data, ProductGrouping.FAMILY).groups().getFirst();
        var detail = calculator.detail(data, group);
        assertEquals(22, detail.monthly().size());
        assertEquals(10, detail.monthly().getLast().month());
        assertEquals(BigDecimal.valueOf(250), detail.monthly().getLast().totals().revenue());
        assertEquals(BigDecimal.ZERO, detail.monthly().stream().filter(m -> m.year() == 2026 && m.month() == 2).findFirst().orElseThrow().totals().revenue());
        assertEquals(2, detail.customers().size());
        assertEquals(BigDecimal.valueOf(550), detail.customers().stream().map(c -> c.totals().revenue()).reduce(BigDecimal.ZERO,BigDecimal::add));
        for (var values : detail.variants().values()) {
            assertEquals(BigDecimal.valueOf(55), values.stream().map(v -> v.totals().quantity()).reduce(BigDecimal.ZERO,BigDecimal::add));
        }
    }
    @Test void incompleteHistoryOrNonpositiveBaselineNeverCreatesAnArtificialPercentage() {
        var data = new ProductSalesSnapshot(List.of(2026),2026,LocalDate.of(2026,10,7),LocalDate.of(2025,2,1),sales());
        assertTrue(calculator.overview(data,ProductGrouping.FAMILY).groups().stream().allMatch(g -> g.revenueChange() == null && g.quantityChange() == null));
        var totals = new ProductTotalsCalculator();
        assertNull(totals.change(BigDecimal.TEN,BigDecimal.ZERO,true));
        assertNull(totals.change(BigDecimal.TEN,BigDecimal.ONE.negate(),true));
        assertNull(ProductTotalsCalculator.share(BigDecimal.TEN,BigDecimal.ZERO));
    }
    @Test void materialGroupingCombinesAllClassifiedFamiliesAndKeepsUnrelatedProductsSeparate() {
        var data = snapshot(sales());
        var page = calculator.overview(data, ProductGrouping.MATERIAL);
        assertEquals(2, page.groups().size());
        var zr = page.groups().stream().filter(g -> g.key().material().equals("ZR")).findFirst().orElseThrow();
        assertEquals("ZR", zr.key().label());
        assertTrue(zr.key().allFamilies());
        assertEquals(BigDecimal.valueOf(600), zr.current().revenue());
        assertEquals(BigDecimal.valueOf(60), zr.current().quantity());
        assertEquals(2, zr.current().customers());
        assertEquals(2, zr.current().orders());
        assertEquals(BigDecimal.valueOf(500), page.unclassifiedTotals().revenue());
        assertEquals(calculator.overview(data, ProductGrouping.FAMILY).classifiedTotals(), page.classifiedTotals());
        var detail = calculator.detail(data, zr);
        assertEquals(3, detail.variants().get(ProductVariantDimension.FAMILY).size());
        assertTrue(detail.variants().get(ProductVariantDimension.COLOR).stream().anyMatch(v -> v.variant().equals("Non previsto")));
        assertEquals(BigDecimal.valueOf(600), detail.customers().stream().map(c -> c.totals().revenue()).reduce(BigDecimal.ZERO,BigDecimal::add));
        for (var variants : detail.variants().values()) {
            assertEquals(BigDecimal.valueOf(60), variants.stream().map(v -> v.totals().quantity()).reduce(BigDecimal.ZERO,BigDecimal::add));
        }
        var family = calculator.overview(data, ProductGrouping.FAMILY).groups().getFirst().key();
        assertTrue(zr.key().relatedTo(family));
        assertTrue(family.relatedTo(zr.key()));
        assertFalse(zr.key().relatedTo(page.groups().stream().filter(g -> g.key().material().equals("ZS")).findFirst().orElseThrow().key()));
    }

    @Test void anEmptyArchiveHasEmptyRankingsAndZeroTotals() {
        var page = calculator.overview(snapshot(List.of()), ProductGrouping.FAMILY);
        assertTrue(page.groups().isEmpty()); assertTrue(page.unclassified().isEmpty());
        assertEquals(0,page.classifiedTotals().customers());
        assertEquals(BigDecimal.ZERO,page.classifiedTotals().revenue());
    }
}
