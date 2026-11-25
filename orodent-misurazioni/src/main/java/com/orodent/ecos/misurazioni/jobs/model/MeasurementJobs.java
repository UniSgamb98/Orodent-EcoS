package com.orodent.ecos.misurazioni.jobs.model;

import com.orodent.ecos.misurazioni.model.MeasurementBatch.Measurement;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Work identity retains production row and load so distinct technical versions are never merged.
 */
public final class MeasurementJobs {
    private MeasurementJobs() {}

    public record Key(long cycleId, long planId, int row) {}

    public record Reading(long discId, int revision, Measurement value, Long ruleVersionId) {
        public boolean complete() {
            return !value.micrometer().isBlank() && !value.scale().isBlank();
        }

        public boolean touched() {
            return !value.micrometer().isBlank() || !value.scale().isBlank();
        }
    }

    public record Job(
            Key key,
            String product,
            String name,
            String line,
            String article,
            BigDecimal diameter,
            BigDecimal height,
            String lot,
            String furnace,
            LocalDate finished,
            List<Reading> readings) {
        public Job {
            readings = List.copyOf(readings);
        }

        public long done() {
            return readings.stream().filter(Reading::complete).count();
        }

        public long remaining() {
            return readings.size() - done();
        }

        public boolean touched() {
            return readings.stream().anyMatch(Reading::touched);
        }

        public boolean complete() {
            return !readings.isEmpty() && remaining() == 0;
        }

        public String status() {
            return complete() ? "Completato" : touched() ? "In corso" : "Da iniziare";
        }

        public String format() {
            return number(diameter) + " × " + number(height) + " mm";
        }

        public String production() {
            return "PR-" + String.format(Locale.ROOT, "%06d", key.planId());
        }
    }

    public record Detail(Job job, String ruleLabel) {}

    public record Operator(String id, String name) {}

    public static String number(BigDecimal n) {
        return n.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    public static final class Conflict extends IllegalStateException {
        public Conflict() {
            super(
                    "Le misure sono state modificate da un’altra postazione. Ricarica il lavoro"
                        + " prima di continuare.");
        }
    }
}
