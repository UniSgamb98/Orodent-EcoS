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
    private int capacity = -1;

    public void restore(List<Measurement> saved) {
        rows.clear();
        rows.addAll(saved);
        capacity = saved.size();
    }

    public int acquire(Instrument instrument, String value) {
        if (value == null || value.isBlank() || value.equals("NaN"))
            throw new IllegalArgumentException("Nessuna misura valida ricevuta.");
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).value(instrument).isEmpty()) {
                rows.set(i, rows.get(i).with(instrument, value));
                return i;
            }
        }
        if (capacity >= 0 && rows.size() >= capacity)
            throw new IllegalArgumentException(
                    "Tutti i dischi hanno già una misura di questo strumento.");
        rows.add(new Measurement("", "").with(instrument, value));
        return rows.size() - 1;
    }

    public void clearValue(int index, Instrument instrument) {
        rows.set(index, rows.get(index).with(instrument, ""));
    }

    public void reset() {
        rows.clear();
        if (capacity >= 0) for (int i = 0; i < capacity; i++) rows.add(new Measurement("", ""));
    }

    public List<Measurement> rows() {
        return List.copyOf(rows);
    }

    public String clipboardText() {
        int end = rows.size();
        while (end > 0
                && rows.get(end - 1).micrometer().isEmpty()
                && rows.get(end - 1).scale().isEmpty()) end--;
        return rows.stream()
                .limit(end)
                .map(row -> row.micrometer() + "\t" + row.scale())
                .collect(Collectors.joining("\n"));
    }
}
