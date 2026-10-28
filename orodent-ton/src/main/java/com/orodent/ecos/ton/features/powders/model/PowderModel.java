package com.orodent.ecos.ton.features.powders.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Immutable values exchanged between workers and the powder editor. */
public final class PowderModel {
    private PowderModel() {}

    public enum State {
        BOZZA("Bozza"),
        APPROVATA("Approvata"),
        RITIRATA("Ritirata");
        private final String label;

        State(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum PowderColor {
        BIANCA("Bianca"),
        GIALLA("Gialla"),
        ROSA("Rosa"),
        GRIGIA("Grigia");

        private final String label;

        PowderColor(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Color may be unknown when reading a catalogue record created before V5. */
    public record IdentityDraft(String code, String name, PowderColor color, boolean active) {
        public IdentityDraft {
            code = text(code).toUpperCase(Locale.ROOT);
            name = text(name);
            if (code.isEmpty() || code.length() > 50)
                throw invalid("Il codice è obbligatorio, massimo 50 caratteri.");
            if (name.isEmpty() || name.length() > 100)
                throw invalid("Il nome è obbligatorio, massimo 100 caratteri.");
        }

        public void requireColor() {
            if (color == null) throw invalid("Seleziona il colore della polvere.");
        }
    }

    public record Oxide(String code, BigDecimal weightPercent) {
        public Oxide {
            code =
                    Normalizer.normalize(text(code), Normalizer.Form.NFKC)
                            .replaceAll("\\s+", "")
                            .toUpperCase(Locale.ROOT);
            if (!code.matches("[A-Z][A-Z0-9]{0,29}"))
                throw invalid("Inserisci un ossido per riga, per esempio Y2O3.");
            weightPercent = decimal(weightPercent, 7, 4, false, "Percentuale di " + code);
            if (weightPercent == null || weightPercent.compareTo(BigDecimal.valueOf(100)) > 0)
                throw invalid(
                        "La percentuale di "
                                + code
                                + " deve essere maggiore di 0 e non superiore a 100 wt%.");
        }
    }

    public record TechnicalDraft(
            BigDecimal strength,
            BigDecimal translucency,
            Integer yttriaClass,
            String notes,
            List<Oxide> oxides) {
        public TechnicalDraft {
            strength = decimal(strength, 10, 3, false, "Resistenza");
            translucency = decimal(translucency, 10, 4, true, "Traslucenza");
            if (yttriaClass != null && yttriaClass <= 0)
                throw invalid("La classe Y deve essere un intero positivo, per esempio 6Y.");
            notes = text(notes);
            if (notes.length() > 1000)
                throw invalid("Le note possono contenere al massimo 1000 caratteri.");
            oxides = List.copyOf(oxides);
            var codes = new HashSet<String>();
            for (Oxide oxide : oxides)
                if (!codes.add(oxide.code())) throw invalid("Ossido duplicato: " + oxide.code());
        }

        public BigDecimal total() {
            return oxides.stream()
                    .map(Oxide::weightPercent)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public void requireApprovable() {
            if (oxides.isEmpty())
                throw invalid("Inserisci almeno un ossido prima di approvare la scheda.");
            if (total().compareTo(BigDecimal.valueOf(100)) > 0)
                throw invalid(
                        "Il totale degli ossidi supera il 100 wt%. Correggi la bozza prima di"
                                + " approvarla.");
            if (translucency != null && notes.isBlank())
                throw invalid(
                        "Indica nelle note il metodo e la scala di misura della traslucenza.");
        }
    }

    public record Powder(
            long id, String code, String name, PowderColor color, boolean active, int revision) {
        public IdentityDraft identity() {
            return new IdentityDraft(code, name, color, active);
        }

        public String colorLabel() {
            return color == null ? "Da specificare" : color.label();
        }
    }

    public record Revision(
            long id,
            long powderId,
            int number,
            State state,
            int revision,
            LocalDateTime createdAt,
            LocalDateTime approvedAt,
            TechnicalDraft technical) {
        @Override
        public String toString() {
            return "v" + number + " · " + state.label();
        }
    }

    public record Detail(Powder powder, List<Revision> revisions) {
        public Detail {
            revisions = List.copyOf(revisions);
        }

        public boolean hasDraft() {
            return revisions.stream().anyMatch(r -> r.state() == State.BOZZA);
        }

        public Revision latest() {
            return revisions.isEmpty() ? null : revisions.getFirst();
        }

        public Revision revision(long id) {
            return revisions.stream().filter(r -> r.id() == id).findFirst().orElseThrow();
        }
    }

    public record Row(
            Powder powder, Integer yttriaClass, Integer approvedVersion, Integer draftVersion) {}

    public record Page(List<Row> rows, long total, int offset, int limit) {
        public Page {
            rows = List.copyOf(rows);
        }

        public boolean hasNext() {
            return offset + rows.size() < total;
        }
    }

    public static final class Conflict extends RuntimeException {
        public Conflict() {
            super(
                    "La scheda è stata modificata da un altro operatore. Ricaricala prima di"
                            + " riprovare; i tuoi dati non sono stati sovrascritti.");
        }
    }

    private static BigDecimal decimal(
            BigDecimal value, int precision, int scale, boolean zeroAllowed, String name) {
        if (value == null) return null;
        if (zeroAllowed ? value.signum() < 0 : value.signum() <= 0)
            throw invalid(name + ": valore non valido.");
        try {
            if (value.setScale(scale, RoundingMode.UNNECESSARY).precision() > precision)
                throw invalid(name + ": valore troppo grande.");
        } catch (ArithmeticException failure) {
            throw invalid(
                    name
                            + ": massimo "
                            + scale
                            + " cifre decimali, senza arrotondamento automatico.");
        }
        return value.stripTrailingZeros();
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
