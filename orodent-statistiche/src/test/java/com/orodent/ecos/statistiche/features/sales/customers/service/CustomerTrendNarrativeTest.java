package com.orodent.ecos.statistiche.features.sales.customers.service;

import com.orodent.ecos.statistiche.features.sales.customers.model.CustomerTrendDirection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CustomerTrendNarrativeTest {
    private final CustomerTrendNarrative narrative = new CustomerTrendNarrative();

    @Test
    void distinguishesAllTwentySevenCombinations() {
        Set<String> descriptions = new HashSet<>();
        for (int orders : new int[]{-15, 0, 15}) {
            for (int revenue : new int[]{-12, 0, 12}) {
                for (int quantity : new int[]{-12, 0, 12}) {
                    String text = narrative.describe(BigDecimal.valueOf(orders),
                            BigDecimal.valueOf(revenue), BigDecimal.valueOf(quantity));
                    assertFalse(text.contains("non è ancora sufficiente"));
                    assertTrue(descriptions.add(text), text);
                }
            }
        }
        assertEquals(27, descriptions.size());
    }

    @Test
    void usesApprovedGrowthDescriptions() {
        assertEquals("Il cliente mantiene una frequenza regolare e aumenta il volume di affari.",
                narrative.describe(BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.TEN));
        assertEquals("Il cliente ordina più spesso e aumenta il volume di affari.",
                narrative.describe(BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN));
        assertEquals("Il cliente ordina meno spesso, ma aumenta quantità e fatturato, concentrando gli acquisti in ordini mediamente più grandi.",
                narrative.describe(BigDecimal.valueOf(-15), BigDecimal.TEN, BigDecimal.TEN));
    }

    @Test
    void distinguishesLowerUnitValueFromFewerUnits() {
        assertEquals("Il cliente ordina regolarmente e mantiene quantità simili, ma il fatturato cala: il valore medio per unità è tendenzialmente più basso.",
                narrative.describe(BigDecimal.ZERO, BigDecimal.valueOf(-12), BigDecimal.ZERO));
        assertEquals("Il cliente ordina regolarmente, ma acquista meno unità e genera meno fatturato.",
                narrative.describe(BigDecimal.ZERO, BigDecimal.valueOf(-12), BigDecimal.valueOf(-12)));
    }

    @Test
    void handlesUnavailableComparisons() {
        for (BigDecimal[] values : new BigDecimal[][]{
                {null, BigDecimal.ZERO, BigDecimal.ZERO},
                {BigDecimal.ZERO, null, BigDecimal.ZERO},
                {BigDecimal.ZERO, BigDecimal.ZERO, null}}) {
            assertEquals("Lo storico non è ancora sufficiente per confrontare frequenza degli ordini, fatturato e quantità acquistata.",
                    narrative.describe(values[0], values[1], values[2]));
        }
    }

    @Test
    void classifiesInclusiveThresholdsConsistently() {
        assertEquals(CustomerTrendDirection.UP, CustomerTrendDirection.forValue(new BigDecimal("3")));
        assertEquals(CustomerTrendDirection.DOWN, CustomerTrendDirection.forValue(new BigDecimal("-3")));
        assertEquals(CustomerTrendDirection.STABLE, CustomerTrendDirection.forValue(new BigDecimal("2.9")));
        assertEquals(CustomerTrendDirection.STABLE, CustomerTrendDirection.forValue(new BigDecimal("-2.9")));
        assertEquals(CustomerTrendDirection.UP, CustomerTrendDirection.forOrders(BigDecimal.TEN));
        assertEquals(CustomerTrendDirection.DOWN, CustomerTrendDirection.forOrders(BigDecimal.TEN.negate()));
        assertEquals(CustomerTrendDirection.STABLE, CustomerTrendDirection.forOrders(new BigDecimal("9.9")));
        assertEquals(CustomerTrendDirection.UNAVAILABLE, CustomerTrendDirection.forValue(null));
    }
}
