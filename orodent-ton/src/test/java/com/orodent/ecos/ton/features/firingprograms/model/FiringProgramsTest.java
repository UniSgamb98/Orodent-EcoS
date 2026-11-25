package com.orodent.ecos.ton.features.firingprograms.model;

import static com.orodent.ecos.ton.features.firingprograms.model.FiringPrograms.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

class FiringProgramsTest {
    @Test
    void hoursAndMinutesNeverUseDecimalHourArithmetic() {
        assertEquals(90, parseTime("1.30", "Tempo"));
        assertEquals(5, parseTime("0.05", "Tempo"));
        assertEquals(779, parseTime("12.59", "Tempo"));
        assertEquals(120, parseTime("2", "Tempo"));
        assertEquals(0, parseTime("0.00", "Tempo"));
        assertEquals("1.30", time(90));
        assertEquals("0.05", time(5));
        assertEquals("25.00", time(1500));
    }

    @Test
    void ambiguousOrInvalidDurationsAreRejected() {
        for (String text :
                List.of(
                        "1.60",
                        "1.3",
                        "1,30",
                        "-1.30",
                        "1.300",
                        "1.30.00",
                        "",
                        "99999999999999999999999999.00"))
            assertThrows(IllegalArgumentException.class, () -> parseTime(text, "Rampa"));
        assertThrows(IllegalArgumentException.class, () -> new Step(BigDecimal.TEN, -1, 0));
        assertThrows(
                IllegalArgumentException.class, () -> new Step(new BigDecimal("1200.001"), 0, 0));
    }

    @Test
    void finalTemperatureIsAnExplicitTokenResolvedOnlyWhenTheLoadProvidesItsValue() {
        var template = new Step(parseTarget("Tfinale"), 90, 30);
        assertNull(template.temperature());
        assertTrue(template.usesFinalTemperature());
        assertEquals("Tfinale", template.targetLabel());
        assertNull(parseTarget(" tFINALE "));
        assertEquals(0, new BigDecimal("1450.25").compareTo(parseTarget("1450,25")));
        assertEquals(
                0,
                new BigDecimal("1440")
                        .compareTo(template.resolve(new BigDecimal("1440")).temperature()));
        assertNull(template.temperature());
        assertThrows(IllegalArgumentException.class, () -> template.resolve(null));
        for (String invalid : List.of("", "T", "Tfinale + 10", "0", "-100", "1000.001"))
            assertThrows(IllegalArgumentException.class, () -> parseTarget(invalid));
    }

    @Test
    void coolingStepsAndZeroTimesAreSupportedAndEmptyProgramsCannotBeApproved() {
        var draft =
                new Draft(
                        " std ",
                        "Ciclo",
                        "",
                        true,
                        List.of(
                                new Step(new BigDecimal("1450"), 60, 90),
                                new Step(new BigDecimal("900"), 45, 0)));
        assertEquals("STD", draft.code());
        assertEquals("3.15", time(draft.steps().stream().mapToLong(Step::minutes).sum()));
        draft.requireApprovable();
        assertEquals(0, new Step(BigDecimal.TEN, 0, 0).minutes());
        assertThrows(
                IllegalArgumentException.class,
                () -> new Draft("S", "S", "", true, List.of()).requireApprovable());
    }
}
