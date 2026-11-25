package com.orodent.ecos.ton.features.firing.model;

import static com.orodent.ecos.ton.features.firing.model.Firing.*;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.ton.features.firingprograms.model.FiringPrograms.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

class FiringScheduleTest {
    final Furnace furnace = new Furnace(1, 1, "F1", "Forno uno");
    final LocalDate start = LocalDate.of(2026, 11, 25);

    Program program(long id, int ramp, int hold) {
        return new Program(id, "P", "Ciclo", (int) id, 1, List.of(new Step(null, ramp, hold)));
    }

    Load load(long id, long oven, LocalDate date, Program program) {
        return new Load(
                id,
                oven,
                "F" + oven,
                "Forno",
                1,
                "LOT-" + id,
                date,
                date.atStartOfDay(),
                "",
                1,
                program,
                new BigDecimal("1450"),
                null);
    }

    Workspace data(Load... loads) {
        return new Workspace(List.of(), List.of(furnace), List.of(loads), List.of());
    }

    @Test
    void tenDayProgramWarnsThroughDayNineAndFreesTheOvenOnDayTen() {
        var tenDays = program(1, 6000, 8400);
        var load = load(1, 1, start, tenDays);
        var data = data(load);
        assertEquals(LocalDate.of(2026, 12, 5), load.availableDate());
        assertEquals(List.of(load), data.overlappingLoads(furnace, start, tenDays));
        assertEquals(List.of(load), data.overlappingLoads(furnace, start.plusDays(9), tenDays));
        assertTrue(data.overlappingLoads(furnace, start.plusDays(10), tenDays).isEmpty());
        assertTrue(
                data.overlappingLoads(new Furnace(2, 1, "F2", "Altro"), start, tenDays).isEmpty());
    }

    @Test
    void partialDaysAreRoundedUpAndZeroDurationProgramsDoNotOccupyADay() {
        var partial = program(1, 1440, 1);
        assertEquals(start.plusDays(2), availableDate(start, partial));
        assertEquals(start.plusDays(1), availableDate(start, program(2, 90, 120)));
        assertTrue(
                data(load(1, 1, start, partial))
                                .overlappingLoads(furnace, start.plusDays(1), null)
                                .size()
                        == 1);
        assertTrue(
                data(load(1, 1, start, program(3, 0, 0)))
                        .overlappingLoads(furnace, start.minusDays(1), partial)
                        .isEmpty());
    }

    @Test
    void earlierStartsThatRunIntoAFutureLoadAlsoWarnAndAdjacentLoadsDoNot() {
        var oneDay = program(1, 1440, 0);
        var existing = load(1, 1, start, oneDay);
        var data = data(existing);
        assertTrue(data.overlappingLoads(furnace, start.minusDays(1), oneDay).isEmpty());
        assertEquals(
                List.of(existing),
                data.overlappingLoads(furnace, start.minusDays(1), program(2, 2880, 0)));
        assertEquals(List.of(existing), data.overlappingLoads(furnace, start, null));
        assertTrue(data.overlappingLoads(null, start, oneDay).isEmpty());
        assertTrue(data.overlappingLoads(furnace, null, oneDay).isEmpty());
    }

    @Test
    void estimatesUseTheSavedProgramAndSkipLegacyLoadsWithNoKnownDuration() {
        var original = program(1, 14400, 0);
        var newer = program(2, 60, 0);
        var oldLoad = load(1, 1, start, original);
        var workspace =
                new Workspace(
                        List.of(),
                        List.of(furnace),
                        List.of(oldLoad, load(2, 1, start, null)),
                        List.of(newer));
        assertEquals(
                List.of(oldLoad), workspace.overlappingLoads(furnace, start.plusDays(8), newer));
        assertNull(availableDate(start, null));
        // Durations and date arithmetic must not overflow the step's integer minute storage.
        assertTrue(
                availableDate(start, program(3, Integer.MAX_VALUE, Integer.MAX_VALUE))
                        .isAfter(start.plusYears(1000)));
    }
}
