package com.orodent.ecos.core.gs1;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GtinTest {
    @Test
    void validatesCheckDigitsAndPadsWithoutLosingLeadingZeros() {
        assertEquals("09506000134352", Gtin.normalize("09506000134352"));
        assertEquals("04006381333931", Gtin.normalize("4006381333931"));
        assertNull(Gtin.normalize(" "));
        for (String bad : new String[] {"09506000134353", "123", "abc", "9".repeat(15)})
            assertThrows(IllegalArgumentException.class, () -> Gtin.normalize(bad));
    }
}
