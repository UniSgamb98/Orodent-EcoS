package com.orodent.ecos.ton.features.compositions.model;

import com.orodent.ecos.ton.features.technical.model.PageData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/** Recipes store mass fractions and exact powder revisions; geometry belongs to disc models. */
public final class Compositions {
    private Compositions() {}

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

    public record Ingredient(long powderRevisionId, BigDecimal mass) {
        public Ingredient {
            if (powderRevisionId <= 0) throw invalid("Seleziona una revisione di polvere valida.");
            if (mass == null || mass.signum() <= 0 || mass.compareTo(BigDecimal.valueOf(100)) > 0)
                throw invalid("La percentuale deve essere maggiore di zero e non superare 100%.");
            try {
                if (mass.setScale(4, RoundingMode.UNNECESSARY).precision() > 7)
                    throw invalid("Percentuale troppo grande.");
            } catch (ArithmeticException failure) {
                throw invalid("Percentuali: massimo quattro decimali, senza arrotondamento.");
            }
            mass = mass.stripTrailingZeros();
        }
    }

    public record Layer(int number, String name, List<Ingredient> ingredients) {
        public Layer {
            if (number <= 0) throw invalid("Il numero dello strato deve essere positivo.");
            name = text(name);
            if (name.length() > 100) throw invalid("Nome strato: massimo 100 caratteri.");
            ingredients = List.copyOf(ingredients);
            var seen = new HashSet<Long>();
            for (Ingredient i : ingredients)
                if (!seen.add(i.powderRevisionId()))
                    throw invalid(
                            "La stessa revisione di polvere compare più volte nello strato "
                                    + number
                                    + ".");
        }

        public BigDecimal total() {
            return ingredients.stream()
                    .map(Ingredient::mass)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public boolean complete() {
            return !ingredients.isEmpty() && total().compareTo(BigDecimal.valueOf(100)) == 0;
        }

        public String label() {
            return "Strato " + number + (name.isBlank() ? "" : " · " + name);
        }
    }

    public record Draft(long productId, String notes, List<Layer> layers) {
        public Draft {
            if (productId <= 0) throw invalid("Seleziona un prodotto valido.");
            notes = text(notes);
            if (notes.length() > 1000) throw invalid("Note: massimo 1000 caratteri.");
            layers = List.copyOf(layers);
            var seen = new HashSet<Integer>();
            for (Layer layer : layers)
                if (!seen.add(layer.number())) throw invalid("Due strati hanno lo stesso numero.");
        }

        public void requireApprovable() {
            if (layers.isEmpty()) throw invalid("Aggiungi almeno uno strato alla composizione.");
            for (int i = 0; i < layers.size(); i++) {
                Layer layer = layers.get(i);
                if (layer.number() != i + 1)
                    throw invalid("Gli strati devono essere consecutivi, a partire da 1.");
                if (!layer.complete())
                    throw invalid(
                            "Strato "
                                    + layer.number()
                                    + ": il totale deve essere esattamente 100% con almeno una"
                                    + " polvere.");
            }
        }
    }

    public record ProductRef(
            long id,
            String code,
            String name,
            Long lineId,
            String lineCode,
            String lineName,
            String color,
            boolean available) {
        public String label() {
            return code + " · " + name;
        }
    }

    public record PowderRef(
            long id,
            long powderId,
            String code,
            String name,
            String color,
            Integer yttria,
            int number,
            State state,
            boolean active,
            Map<String, BigDecimal> oxides) {
        public PowderRef {
            oxides = Collections.unmodifiableMap(new TreeMap<>(oxides));
        }

        public boolean usable() {
            return active && state == State.APPROVATA;
        }

        public String label() {
            return code
                    + " · v"
                    + number
                    + " · "
                    + name
                    + (yttria == null ? "" : " · " + yttria + "Y");
        }

        @Override
        public String toString() {
            return label() + (usable() ? "" : " · non disponibile");
        }
    }

    public record Revision(
            long id, int number, State state, int editVersion, LocalDateTime updated, Draft data) {
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

    public record Detail(
            Revision revision,
            ProductRef product,
            Map<Long, PowderRef> powders,
            Page<Version> versions,
            boolean deletable) {
        public Detail {
            powders = Map.copyOf(powders);
        }
    }

    public record Summary(
            long id,
            long productId,
            String code,
            String name,
            String lineCode,
            String color,
            int layers,
            Integer approvedVersion,
            Integer draftVersion,
            boolean available) {}

    public record ModelMatch(
            long id,
            String code,
            int version,
            BigDecimal diameter,
            List<Integer> layers,
            boolean matches) {
        public ModelMatch {
            layers = List.copyOf(layers);
        }
    }

    public record Usage(
            long articleId,
            String articleCode,
            BigDecimal diameter,
            BigDecimal height,
            int specification,
            String state,
            String modelCode,
            int modelVersion,
            boolean preferred) {}

    public record Page<T>(List<T> rows, long total, int offset, int limit) implements PageData<T> {
        public Page {
            rows = List.copyOf(rows);
        }
    }

    public static final class Conflict extends RuntimeException {
        public Conflict() {
            super(
                    "La composizione è stata modificata da un altro operatore. Ricarica la scheda:"
                            + " le tue modifiche sono ancora visibili.");
        }
    }

    public static String text(String value) {
        return value == null ? "" : value.strip();
    }

    public static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}
