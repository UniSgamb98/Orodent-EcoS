package com.orodent.ecos.misurazioni;

import static org.junit.jupiter.api.Assertions.*;

import com.orodent.ecos.misurazioni.config.MeasurementSettings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

class MisurazioniModuleTest {
    @TempDir Path temporary;

    @Test
    void onlyAnExplicitTrueEnablesTheMeasuringStation() {
        var settings = new Properties();
        assertFalse(MeasurementSettings.enabled(settings));
        for (String value : new String[] {"false", "", "yes", "1", "tru"}) {
            settings.setProperty("misurazioni.enabled", value);
            assertFalse(MeasurementSettings.enabled(settings));
        }
        settings.setProperty("misurazioni.enabled", " true ");
        assertTrue(MeasurementSettings.enabled(settings));
    }

    @Test
    void directOpeningIsBlockedBeforeCreatingTheUiOrReadingInstrumentEndpoints() throws Exception {
        String previous = System.getProperty("orodent.ecos.config");
        Path file = temporary.resolve("application.properties");
        try {
            System.setProperty("orodent.ecos.config", file.toString());
            var module = new MisurazioniModule();
            for (String flag :
                    new String[] {
                        "", "misurazioni.enabled=false\n", "misurazioni.enabled=invalid\n"
                    }) {
                Files.writeString(
                        file,
                        flag + "misurazioni.micrometro.port=invalid\n",
                        StandardCharsets.UTF_8);
                assertFalse(module.enabled());
                // A null context and invalid endpoint must never be accessed while disabled.
                var error = assertThrows(IllegalStateException.class, () -> module.open(null));
                assertTrue(error.getMessage().contains("misurazioni.enabled=true"));
            }
            Files.writeString(file, "misurazioni.enabled=true\n", StandardCharsets.UTF_8);
            assertTrue(module.enabled());
        } finally {
            if (previous == null) System.clearProperty("orodent.ecos.config");
            else System.setProperty("orodent.ecos.config", previous);
        }
    }
}
