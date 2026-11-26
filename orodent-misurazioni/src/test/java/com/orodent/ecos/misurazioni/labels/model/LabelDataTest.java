package com.orodent.ecos.misurazioni.labels.model;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.orodent.ecos.misurazioni.jobs.model.MeasurementJobs.*;
import com.orodent.ecos.misurazioni.labels.model.Labels.Source;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

class LabelDataTest {
    @Test
    void expiryAlwaysComesFromProductionIncludingLeapDay() {
        var job =
                new Job(
                        new Key(1, 1, 1),
                        "PROD",
                        "Prodotto",
                        "Linea",
                        "ART",
                        BigDecimal.valueOf(98),
                        BigDecimal.valueOf(16),
                        "LOT",
                        "Forno",
                        null,
                        null,
                        List.of());
        for (var dates :
                List.of(
                        List.of("2026-09-22", "2031-09-22", "22/09/2031", "310922"),
                        List.of("2024-02-29", "2029-02-28", "28/02/2029", "290228"),
                        List.of("2026-12-03", "2031-12-03", "03/12/2031", "311203"))) {
            var source =
                    new Source(
                            job,
                            null,
                            List.of(),
                            LocalDate.parse(dates.get(0)),
                            "",
                            "Prodotto",
                            "Linea",
                            1,
                            null,
                            1,
                            1);
            assertEquals(LocalDate.parse(dates.get(1)), source.expiry());
            var payload = JsonParser.parseString(LabelData.payload(source)).getAsJsonObject();
            assertEquals(dates.get(2), payload.get("expiryDate").getAsString());
            assertEquals(dates.get(3), payload.get("expiryDateGs1").getAsString());
        }
    }
}
