package com.orodent.ecos.statistiche.features.sales.products.service;

import com.orodent.ecos.statistiche.features.sales.products.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductSalesPaceCalculatorTest {
    private final ProductSalesPaceCalculator calculator = new ProductSalesPaceCalculator();

    @Test
    void includesDaysWithoutSalesAndCombinesLinesBeforeMeasuringDocumentSizes() {
        var sales = List.of(sale("2026-01-14","A",1),sale("2026-01-14","A",1),
                sale("2026-01-20","B",4),sale("2026-01-28","C",22));
        var pace = calculator.calculate(snapshot("2026-01-28","2020-01-01",sales),sales);
        assertEquals(LocalDate.of(2026,1,1),pace.from());
        assertEquals(28,pace.days());
        assertEquals(3,pace.documents());
        number("28",pace.soldQuantity());
        number("7",pace.weeklyQuantity());
        number("4",pace.medianDocumentQuantity());
        number("9.33333333",pace.meanDocumentQuantity());
    }

    @Test
    void medianShowsHabitualPurchasesEvenWithOneExceptionalDocument() {
        var sales = List.of(sale("2026-01-01","A",2),sale("2026-01-02","B",2),sale("2026-01-03","C",100));
        var pace = calculator.calculate(snapshot("2026-01-28","2020-01-01",sales),sales);
        number("2",pace.medianDocumentQuantity());
        number("34.66666667",pace.meanDocumentQuantity());
    }

    @Test
    void evenSizedSamplesAverageTheTwoMiddleDocumentTotals() {
        var sales = List.of(sale("2026-01-01","A",2),sale("2026-01-02","B",4),
                sale("2026-01-03","C",10),sale("2026-01-04","D",100));
        var pace = calculator.calculate(snapshot("2026-01-28","2020-01-01",sales),sales);
        number("7",pace.medianDocumentQuantity());
        number("29",pace.meanDocumentQuantity());
    }

    @Test
    void ignoresReturnsCreditsAndNonpositiveSaleQuantitiesWithoutChangingNetTotals() {
        var sales = List.of(sale("2026-01-01","A",5),
                new ProductSale("ZR9812MSTA2","",LocalDate.of(2026,1,2),"C1","","RESO",false,BigDecimal.ZERO,BigDecimal.valueOf(-20)),
                sale("2026-01-03","NEGATIVE",-3),sale("2026-01-04","ZERO",0));
        var pace = calculator.calculate(snapshot("2026-01-28","2020-01-01",sales),sales);
        assertEquals(1,pace.documents());
        number("5",pace.soldQuantity());
        number("5",pace.meanDocumentQuantity());
        number("5",pace.medianDocumentQuantity());
        number("-18",new ProductTotalsCalculator().total(sales).quantity());
    }

    @Test
    void recentPaceUsesExactlyNinetyOneCalendarDaysEndingAtTheArchiveCutoff() {
        var sales = List.of(sale("2025-12-31","OLD",999),sale("2026-03-31","BEFORE",1000),
                sale("2026-04-01","START",91),sale("2026-06-30","END",91),sale("2026-07-01","FUTURE",999));
        var pace = calculator.calculate(snapshot("2026-06-30","2020-01-01",sales),sales);
        assertEquals(LocalDate.of(2026,4,1),pace.recentFrom());
        assertEquals(91,pace.recentDays());
        assertEquals(181,pace.days());
        number("14",pace.recentWeeklyQuantity());
        number("1182",pace.soldQuantity());
        assertEquals(3,pace.documents());
    }

    @Test
    void partialArchiveUsesItsSharedCoverageRatherThanEachProductsFirstSale() {
        var sales = List.of(sale("2026-01-28","A",28));
        var pace = calculator.calculate(snapshot("2026-01-28","2026-01-15",sales),sales);
        assertEquals(LocalDate.of(2026,1,15),pace.from());
        assertEquals(14,pace.days());
        assertEquals(14,pace.recentDays());
        number("14",pace.weeklyQuantity());
        number("14",pace.recentWeeklyQuantity());
    }

    @Test
    void handlesLeapYearsAndSingleObservedDaysWithoutDroppingTheLastDay() {
        var sales = List.of(sale("2024-02-29","A",366));
        var leap = new ProductSalesSnapshot(List.of(2024),2024,LocalDate.of(2024,12,31),LocalDate.of(2020,1,1),sales);
        var pace = calculator.calculate(leap,sales);
        assertEquals(366,pace.days());
        number("7",pace.weeklyQuantity());
        var day = new ProductSalesSnapshot(List.of(2024),2024,LocalDate.of(2024,2,29),LocalDate.of(2024,2,29),sales);
        var single = calculator.calculate(day,sales);
        assertEquals(1,single.days());
        number("2562",single.weeklyQuantity());
    }

    @Test
    void noPositiveSalesHasZeroPaceButNoInventedTypicalDocument() {
        var pace = calculator.calculate(snapshot("2026-01-28","2020-01-01",List.of()),List.of());
        number("0",pace.weeklyQuantity());
        number("0",pace.recentWeeklyQuantity());
        assertNull(pace.meanDocumentQuantity());
        assertNull(pace.medianDocumentQuantity());
        assertEquals(0,pace.documents());
        var invalid = calculator.calculate(snapshot("2026-01-28","2027-01-01",List.of()),List.of());
        assertEquals(0,invalid.days());
        assertNull(invalid.weeklyQuantity());
        assertNull(invalid.recentWeeklyQuantity());
    }

    @Test
    void documentIdentitySeparatesCustomersAndYearsButNotMultipleLinesOfOneDocument() {
        var sales = List.of(sale("2026-01-01","A",2),sale("2026-01-02","A",3),
                new ProductSale("ZR9812MSTA2","",LocalDate.of(2026,1,1),"C2","","A",true,BigDecimal.ZERO,BigDecimal.valueOf(20)),
                sale("2025-01-01","A",999));
        var pace = calculator.calculate(snapshot("2026-01-28","2020-01-01",sales),sales);
        assertEquals(2,pace.documents());
        number("25",pace.soldQuantity());
        number("12.5",pace.medianDocumentQuantity());
        assertEquals(3,new ProductTotalsCalculator().total(sales).orders());
    }

    @Test
    void groupAndVariantPaceUseTheirOwnDocumentTotalsAndTheSameCalendarWindow() {
        var sales = List.of(sale("2026-01-28","A",2),
                new ProductSale("ZR9512MSTA2","",LocalDate.of(2026,1,28),"C1","","A",true,BigDecimal.ZERO,BigDecimal.valueOf(3)),
                new ProductSale("ZR9818MSTA2","",LocalDate.of(2026,1,28),"C1","","A",true,BigDecimal.ZERO,BigDecimal.valueOf(9)));
        var snapshot = snapshot("2026-01-28","2020-01-01",sales);
        var analysis = new ProductAnalysisCalculator();
        var overview = analysis.overview(snapshot,ProductGrouping.FAMILY_COLOR);
        var detail = analysis.detail(snapshot,overview.groups().getFirst());
        assertEquals(1,detail.pace().documents());
        number("14",detail.pace().medianDocumentQuantity());
        number("3.5",detail.pace().weeklyQuantity());
        var heights = detail.variants().get(ProductVariantDimension.HEIGHT);
        var twelve = heights.stream().filter(v -> v.variant().equals("12")).findFirst().orElseThrow();
        number("5",twelve.pace().medianDocumentQuantity());
        number("1.25",twelve.pace().weeklyQuantity());
        assertEquals(28,twelve.pace().days());
        assertEquals(1,twelve.pace().documents());
    }

    private static ProductSale sale(String date, String document, int quantity) {
        return new ProductSale("ZR9812MSTA2","MST A2",LocalDate.parse(date),"C1","Cliente",document,true,BigDecimal.ZERO,BigDecimal.valueOf(quantity));
    }

    private static ProductSalesSnapshot snapshot(String through, String from, List<ProductSale> sales) {
        return new ProductSalesSnapshot(List.of(2026,2025),2026,LocalDate.parse(through),LocalDate.parse(from),sales);
    }

    private static void number(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0,new BigDecimal(expected).compareTo(actual), () -> expected + " != " + actual);
    }
}
