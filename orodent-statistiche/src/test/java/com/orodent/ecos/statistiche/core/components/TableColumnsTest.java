package com.orodent.ecos.statistiche.core.components;

import javafx.scene.control.TableColumn;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class TableColumnsTest {
    @Test void numericOrderingUsesExactValuesRatherThanCurrencyOrPercentText() {
        var currency = NumberFormat.getCurrencyInstance(Locale.ITALY);
        TableColumn<BigDecimal, BigDecimal> money = TableColumns.value("Fatturato", value -> value, currency::format);
        List<BigDecimal> input = Arrays.asList(new BigDecimal("1000"), new BigDecimal("2"), null,
                new BigDecimal("10"), new BigDecimal("-20"), new BigDecimal("1.25"),
                new BigDecimal("1.2501"), new BigDecimal("100000000000000000000.01"));
        var sorted = new ArrayList<>(input);
        sorted.sort(money.getComparator());
        assertEquals(Arrays.asList(null, new BigDecimal("-20"), new BigDecimal("1.25"), new BigDecimal("1.2501"),
                new BigDecimal("2"), new BigDecimal("10"), new BigDecimal("1000"), new BigDecimal("100000000000000000000.01")), sorted);
        sorted.sort(money.getComparator().reversed());
        assertNull(sorted.getLast());
        assertEquals(input.getLast(), sorted.getFirst());
        assertEquals(new BigDecimal("1000"), money.getCellValueFactory().call(
                new TableColumn.CellDataFeatures<>(null, money, new BigDecimal("1000"))).getValue());
        assertEquals(0, money.getComparator().compare(new BigDecimal("1.0"), new BigDecimal("1.00")));
    }

    @Test void datesAndCountsKeepTheirNaturalOrder() {
        TableColumn<LocalDate, LocalDate> date = TableColumns.value("Data", value -> value, LocalDate::toString);
        List<LocalDate> dates = new ArrayList<>(List.of(LocalDate.of(2026, 1, 2), LocalDate.of(2025, 12, 31), LocalDate.of(2026, 2, 1)));
        dates.sort(date.getComparator());
        assertEquals(List.of(LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 2), LocalDate.of(2026, 2, 1)), dates);
        TableColumn<Integer, Integer> count = TableColumns.value("Documenti", value -> value, value -> value + " documenti");
        List<Integer> counts = new ArrayList<>(List.of(100, 2, 10));
        counts.sort(count.getComparator());
        assertEquals(List.of(2, 10, 100), counts);
    }
}
