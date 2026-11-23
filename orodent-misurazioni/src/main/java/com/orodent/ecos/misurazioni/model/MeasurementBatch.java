package com.orodent.ecos.misurazioni.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Keeps the Marzia pairing/export order, independently of the active instrument. */
public final class MeasurementBatch {
    public record Measurement(String micrometer, String scale) {
        String value(Instrument instrument) {
            return instrument == Instrument.MICROMETER ? micrometer : scale;
        }

        Measurement with(Instrument instrument, String value) {
            return instrument == Instrument.MICROMETER
                    ? new Measurement(value, scale)
                    : new Measurement(micrometer, value);
        }
    }

    private final List<Measurement> rows = new ArrayList<>();

    public int acquire(Instrument instrument, String value) {
        if (value == null || value.isBlank() || value.equals("NaN"))
            throw new IllegalArgumentException("Nessuna misura valida ricevuta.");
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).value(instrument).isEmpty()) {
                rows.set(i, rows.get(i).with(instrument, value));
                return i;
            }
        }
        rows.add(new Measurement("", "").with(instrument, value));
        return rows.size() - 1;
    }

    public void clearValue(int index, Instrument instrument) {
        rows.set(index, rows.get(index).with(instrument, ""));
    }

    public void reset() {
        rows.clear();
    }

    public List<Measurement> rows() {
        return List.copyOf(rows);
    }

    public String clipboardText() {
        return rows.stream()
                .map(row -> row.micrometer() + "\t" + row.scale())
                .collect(Collectors.joining("\n"));
    }
}
