package com.orodent.ecos.core.math;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

class FormulaProgramTest {
    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    @Test
    void namedResultsCanBeReusedAndBothAssignmentDirectionsWork() {
        var program =
                FormulaProgram.compile(
                        "# prova\nHmedia + D = a1\na2 = a1 * P\na2 / 10 = risultato");
        var output = program.evaluate(n("2"), n("3"), n("4"));
        assertEquals(List.of("a1", "a2", "risultato"), program.outputs());
        assertEquals(0, n("5").compareTo(output.get("a1")));
        assertEquals(0, n("2").compareTo(output.get("risultato")));
        assertThrows(UnsupportedOperationException.class, () -> output.put("extra", n("1")));
    }

    @Test
    void arithmeticHasUsualPrecedenceConstantsAndDecimalComma() {
        var values =
                FormulaProgram.compile(
                                "-2^2 = negativo\n"
                                    + "2^3^2 = potenza\n"
                                    + "sqrt(16) + cbrt(8) + abs(-2,5) = funzioni\n"
                                    + "(D/2)^2 * PI * Hmedia = volume\n"
                                    + "P / volume = densita")
                        .evaluate(n("2"), n("3"), n("12"));
        assertEquals(0, n("-4").compareTo(values.get("negativo")));
        assertEquals(0, n("512").compareTo(values.get("potenza")));
        assertEquals(0, n("8.5").compareTo(values.get("funzioni")));
        assertTrue(
                values.get("volume")
                                .subtract(n("9.424777960769379715387930149838509"))
                                .abs()
                                .compareTo(n("0.000000000000000000000000000001"))
                        < 0);
        assertTrue(values.get("densita").signum() > 0);
    }

    @Test
    void unknownForwardDuplicateAndReservedNamesReportTheLine() {
        for (String source :
                List.of(
                        "D + Hmedia = a\nz * P = b",
                        "D = a\nP = a",
                        "D = D + 1",
                        "a = a + 1",
                        "D + P == x",
                        "System.exit(0) = x")) {
            var failure =
                    assertThrows(
                            IllegalArgumentException.class, () -> FormulaProgram.compile(source));
            assertTrue(failure.getMessage().startsWith("Riga "));
        }
        assertThrows(IllegalArgumentException.class, () -> FormulaProgram.compile(" "));
    }

    @Test
    void runtimeErrorsAndInvalidInputsNeverReturnStaleOrNonFiniteNumbers() {
        for (String source : List.of("P/(D-D) = x", "sqrt(-D) = x", "(-D)^0.5 = x", "0^-1 = x")) {
            var program = FormulaProgram.compile(source);
            var error =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> program.evaluate(n("2"), n("3"), n("4")));
            assertTrue(error.getMessage().startsWith("Riga 1:"));
        }
        var program = FormulaProgram.compile("D = x");
        assertThrows(
                IllegalArgumentException.class,
                () -> program.evaluate(BigDecimal.ZERO, n("1"), n("1")));
        assertThrows(IllegalArgumentException.class, () -> program.evaluate(null, n("1"), n("1")));
    }

    @Test
    void excessiveProgramsAreRejectedWithoutExecutingCode() {
        assertThrows(
                IllegalArgumentException.class, () -> FormulaProgram.compile(" ".repeat(16001)));
        assertThrows(
                IllegalArgumentException.class,
                () -> FormulaProgram.compile("-".repeat(100) + "D = x"));
        assertThrows(
                IllegalArgumentException.class,
                () -> FormulaProgram.compile("D + ".repeat(500) + "D = x"));
        var program = FormulaProgram.compile("D^101 = x");
        assertThrows(
                IllegalArgumentException.class, () -> program.evaluate(n("2"), n("3"), n("4")));
    }
}
