package com.orodent.ecos.misurazioni.model;

import static com.orodent.ecos.misurazioni.model.Instrument.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MeasurementBatchTest {
    @Test
    void pairsSeparatePassesAndExportsTheExactExcelColumnOrder() {
        var batch = new MeasurementBatch();
        batch.acquire(SCALE, "125,0");
        batch.acquire(SCALE, "126,5");
        batch.acquire(MICROMETER, "98,123\t12,456");
        batch.acquire(MICROMETER, "98,124\t12,457");
        assertEquals("98,123\t12,456\t125,0\n98,124\t12,457\t126,5", batch.clipboardText());
        batch.clearValue(0, MICROMETER);
        batch.acquire(MICROMETER, "98,100\t12,400");
        assertEquals(2, batch.rows().size());
        assertEquals("125,0", batch.rows().getFirst().scale());
        assertEquals("98,100\t12,400", batch.rows().getFirst().micrometer());
        batch.reset();
        assertTrue(batch.rows().isEmpty());
        assertEquals("", batch.clipboardText());
    }

    @Test
    void missingReadingsNeverBecomeRealMeasurements() {
        var batch = new MeasurementBatch();
        for (String value : new String[] {null, "", " ", "NaN"})
            assertThrows(IllegalArgumentException.class, () -> batch.acquire(SCALE, value));
        assertTrue(batch.rows().isEmpty());
    }
}
