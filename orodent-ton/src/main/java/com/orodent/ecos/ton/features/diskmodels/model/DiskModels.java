package com.orodent.ecos.ton.features.diskmodels.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/** Immutable technical versions. Heights belong to articles, recipes to compositions. */
public final class DiskModels {
    private DiskModels() {}

    public enum State {
        BOZZA("Bozza"),
        APPROVATA("Approvata"),
        RITIRATA("Ritirata");
        private final String label;

        State(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record Range(BigDecimal from, BigDecimal to, BigDecimal top, BigDecimal bottom) {
        public Range {
            from = decimal(from, 6, 2, true, "Altezza iniziale");
            to = decimal(to, 6, 2, false, "Altezza finale");
            top = decimal(top, 6, 2, true, "Sovramateriale superiore della fascia");
            bottom = decimal(bottom, 6, 2, true, "Sovramateriale inferiore della fascia");
            if (to.compareTo(from) <= 0)
                throw invalid("La fine della fascia deve superare l'inizio.");
        }
    }

    /** Equipment metadata frozen with a particular model version. */
    public record Mould(long id, int revision, String code, String name, BigDecimal diameter) {
        public Mould {
            if (id <= 0 || revision <= 0) throw invalid("Seleziona uno stampo registrato.");
            code = text(code);
            name = text(name);
            if (code.isEmpty() || code.length() > 50 || name.isEmpty() || name.length() > 100)
                throw invalid("Anagrafica dello stampo non valida.");
            diameter = decimal(diameter, 10, 3, false, "Diametro stampo");
        }

        @Override
        public String toString() {
            return code + " · " + name + " · Ø " + diameter.toPlainString() + " mm · r" + revision;
        }
    }

    public record Moulds(Mould reference, List<Mould> admitted) {
        public Moulds {
            Objects.requireNonNull(reference);
            admitted = admitted.stream().sorted(Comparator.comparingLong(Mould::id)).toList();
            var ids = new HashSet<Long>();
            ids.add(reference.id());
            for (var mould : admitted)
                if (!ids.add(mould.id()))
                    throw invalid("Ogni stampo può essere selezionato una sola volta.");
        }

        public List<Mould> all() {
            var all = new ArrayList<Mould>();
            all.add(reference);
            all.addAll(admitted);
            return all;
        }
    }

    /** One explicit nominal diameter; independent standard dose and manual mould selection. */
    public record DiameterGroup(BigDecimal articleDiameter, BigDecimal grams, Moulds moulds) {
        public DiameterGroup {
            articleDiameter = decimal(articleDiameter, 6, 2, false, "Diametro articolo");
            grams = decimal(grams, 12, 5, false, "Peso per millimetro");
            Objects.requireNonNull(moulds, "Seleziona lo stampo di riferimento.");
        }
    }

    public record Draft(
            String code,
            BigDecimal diameter,
            BigDecimal pressure,
            BigDecimal grams,
            BigDecimal top,
            BigDecimal bottom,
            String notes,
            List<BigDecimal> layers,
            List<Range> ranges,
            Moulds moulds,
            List<DiameterGroup> diameters) {
        /** Historical V19 model: physical mould known, nominal diameter not assigned. */
        public Draft(
                String code,
                BigDecimal diameter,
                BigDecimal pressure,
                BigDecimal grams,
                BigDecimal top,
                BigDecimal bottom,
                String notes,
                List<BigDecimal> layers,
                List<Range> ranges,
                Moulds moulds) {
            this(
                    code, diameter, pressure, grams, top, bottom, notes, layers, ranges, moulds,
                    List.of());
        }

        /** New model. The legacy header mirrors the first group only for old readers. */
        public Draft(
                String code,
                BigDecimal pressure,
                BigDecimal top,
                BigDecimal bottom,
                String notes,
                List<BigDecimal> layers,
                List<Range> ranges,
                List<DiameterGroup> diameters) {
            this(
                    code,
                    first(diameters).moulds().reference().diameter(),
                    pressure,
                    first(diameters).grams(),
                    top,
                    bottom,
                    notes,
                    layers,
                    ranges,
                    first(diameters).moulds(),
                    diameters);
        }

        private static DiameterGroup first(List<DiameterGroup> groups) {
            if (groups == null || groups.isEmpty())
                throw invalid("Aggiungi almeno un diametro articolo.");
            return groups.stream()
                    .min(Comparator.comparing(DiameterGroup::articleDiameter))
                    .orElseThrow();
        }

        /** Legacy rows and import fixtures have a diameter but no assigned physical mould. */
        public Draft(
                String code,
                BigDecimal diameter,
                BigDecimal pressure,
                BigDecimal grams,
                BigDecimal top,
                BigDecimal bottom,
                String notes,
                List<BigDecimal> layers,
                List<Range> ranges) {
            this(code, diameter, pressure, grams, top, bottom, notes, layers, ranges, null);
        }

        public Draft {
            diameters =
                    Objects.requireNonNull(diameters).stream()
                            .sorted(Comparator.comparing(DiameterGroup::articleDiameter))
                            .toList();
            for (int i = 1; i < diameters.size(); i++)
                if (diameters
                                .get(i)
                                .articleDiameter()
                                .compareTo(diameters.get(i - 1).articleDiameter())
                        == 0)
                    throw invalid(
                            "Ogni diametro articolo può comparire una sola volta nel modello.");
            if (!diameters.isEmpty()) {
                var first = diameters.getFirst();
                diameter = first.moulds().reference().diameter();
                grams = first.grams();
                moulds = first.moulds();
            }
            code = text(code).toUpperCase(Locale.ROOT);
            if (code.isEmpty() || code.length() > 100)
                throw invalid("Inserisci un codice modello di massimo 100 caratteri.");
            diameter = decimal(diameter, 10, 3, false, "Diametro dello stampo di riferimento");
            if (moulds != null && diameter.compareTo(moulds.reference().diameter()) != 0)
                throw invalid(
                        "Il diametro del modello deve corrispondere allo stampo di riferimento.");
            pressure = decimal(pressure, 10, 3, false, "Pressione");
            grams = decimal(grams, 12, 5, false, "Peso per millimetro");
            top = decimal(top, 6, 2, true, "Sovramateriale superiore");
            bottom = decimal(bottom, 6, 2, true, "Sovramateriale inferiore");
            notes = text(notes);
            if (notes.length() > 1000) throw invalid("Note: massimo 1000 caratteri.");
            layers =
                    Objects.requireNonNull(layers).stream()
                            .map(v -> decimal(v, 7, 4, false, "Percentuale strato"))
                            .toList();
            if (layers.stream().anyMatch(v -> v.compareTo(BigDecimal.valueOf(100)) > 0))
                throw invalid("La percentuale di uno strato non può superare 100%.");
            ranges =
                    Objects.requireNonNull(ranges).stream()
                            .sorted(Comparator.comparing(Range::from))
                            .toList();
            for (int i = 1; i < ranges.size(); i++)
                if (ranges.get(i).from().compareTo(ranges.get(i - 1).to()) < 0)
                    throw invalid("Le fasce di altezza non possono sovrapporsi.");
        }

        public void requireDiameters() {
            if (diameters.isEmpty())
                throw invalid(
                        "Configura almeno un diametro articolo con stampo di riferimento e g/mm.");
        }

        /**
         * Legacy models retain their historical coefficients; explicit groups require exact
         * matching.
         */
        public DiameterGroup forArticleDiameter(BigDecimal nominal) {
            if (diameters.isEmpty()) return null;
            return diameters.stream()
                    .filter(d -> nominal != null && d.articleDiameter().compareTo(nominal) == 0)
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    invalid(
                                            "Diametro articolo "
                                                    + (nominal == null
                                                            ? ""
                                                            : nominal.toPlainString())
                                                    + " mm non configurato nel modello "
                                                    + code
                                                    + "."));
        }

        public BigDecimal gramsFor(BigDecimal nominal) {
            var group = forArticleDiameter(nominal);
            return group == null ? grams : group.grams();
        }

        public BigDecimal mouldDiameterFor(BigDecimal nominal) {
            var group = forArticleDiameter(nominal);
            return group == null ? diameter : group.moulds().reference().diameter();
        }

        public BigDecimal total() {
            return layers.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public void requireApprovable() {
            if (layers.isEmpty() || total().compareTo(BigDecimal.valueOf(100)) != 0)
                throw invalid("Per approvare servono strati con totale esatto 100%.");
        }
    }

    public record Revision(
            long id,
            int number,
            State state,
            int editVersion,
            LocalDateTime created,
            LocalDateTime updated,
            LocalDateTime approved,
            Draft data) {
        public boolean frozen() {
            return state != State.BOZZA;
        }
    }

    public record Version(long id, int number, State state, LocalDateTime updated) {
        @Override
        public String toString() {
            return "v" + number + " · " + state;
        }
    }

    public record Summary(
            long id,
            String code,
            BigDecimal diameter,
            int layers,
            Integer approvedVersion,
            Integer draftVersion,
            List<BigDecimal> articleDiameters) {
        public Summary {
            articleDiameters = List.copyOf(articleDiameters);
        }
    }

    public enum Filter {
        ALL("Tutti gli stati"),
        DRAFT("Con una bozza"),
        APPROVED("Con approvata");
        private final String label;

        Filter(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record Usage(
            long articleId,
            String articleCode,
            String productCode,
            BigDecimal diameter,
            BigDecimal height,
            int specification,
            String state,
            boolean preferred) {}

    public record Page<T>(List<T> rows, long total, int offset, int limit)
            implements com.orodent.ecos.ton.features.technical.model.PageData<T> {
        public Page {
            rows = List.copyOf(rows);
        }

        public boolean hasNext() {
            return offset + rows.size() < total;
        }

        public String label() {
            return total == 0
                    ? "Nessun risultato"
                    : (offset + 1) + "–" + (offset + rows.size()) + " di " + total;
        }
    }

    public record Detail(Revision revision, Page<Version> versions, boolean deletable) {}

    public static final class Conflict extends RuntimeException {
        public Conflict() {
            super(
                    "Il modello è stato modificato da un altro operatore. Ricarica la scheda: le"
                            + " tue modifiche sono ancora visibili.");
        }
    }

    public static String text(String value) {
        return value == null ? "" : value.strip();
    }

    public static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    private static BigDecimal decimal(
            BigDecimal value, int precision, int scale, boolean zero, String label) {
        if (value == null || (zero ? value.signum() < 0 : value.signum() <= 0))
            throw invalid(
                    label
                            + ": inserisci un valore "
                            + (zero ? "maggiore o uguale a zero." : "maggiore di zero."));
        try {
            if (value.setScale(scale, RoundingMode.UNNECESSARY).precision() > precision)
                throw invalid(label + ": valore troppo grande.");
        } catch (ArithmeticException invalid) {
            throw invalid(label + ": massimo " + scale + " decimali, senza arrotondamento.");
        }
        return value.stripTrailingZeros();
    }
}
