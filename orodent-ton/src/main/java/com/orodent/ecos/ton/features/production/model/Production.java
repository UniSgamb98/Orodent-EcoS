package com.orodent.ecos.ton.features.production.model;

import com.orodent.ecos.database.PowderDisplayCatalogue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Draft quantities and confirmed output, independent of finished-goods CSV stock. */
public final class Production {
    private Production() {}

    public static final String RULE = "TON-PREPARAZIONE-1";

    public enum State {
        BOZZA("Bozza"),
        PREPARATA("Confermata");
        private final String label;

        State(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record LineChoice(long id, String code, String name) {
        @Override
        public String toString() {
            return code + " · " + name;
        }
    }

    public record ProductChoice(
            long id,
            long lineId,
            String lineCode,
            String lineName,
            String code,
            String name,
            String color) {
        @Override
        public String toString() {
            return code + " · " + name;
        }
    }

    public record TemplateChoice(long id, String code, int version, String name, String content) {
        @Override
        public String toString() {
            return name + " · v" + version;
        }
    }

    public record Catalogues(
            List<LineChoice> lines,
            List<ProductChoice> products,
            List<TemplateChoice> templates,
            PowderDisplayCatalogue powders) {
        public Catalogues(
                List<LineChoice> lines,
                List<ProductChoice> products,
                List<TemplateChoice> templates) {
            this(lines, products, templates, PowderDisplayCatalogue.empty());
        }

        public Catalogues {
            lines = List.copyOf(lines);
            products = List.copyOf(products);
            templates = List.copyOf(templates);
        }
    }

    public record PowderShare(long revisionId, String code, String name, BigDecimal percentage) {}

    public record Dose(int number, BigDecimal height, BigDecimal grams, List<PowderShare> powders) {
        public Dose {
            powders = List.copyOf(powders);
        }
    }

    public record ArticleChoice(
            long id,
            String code,
            BigDecimal diameter,
            BigDecimal height,
            long specificationId,
            int specificationVersion,
            long compositionId,
            int compositionVersion,
            long modelId,
            int modelVersion,
            String modelCode,
            BigDecimal pressure,
            BigDecimal pressDiameter,
            String reason,
            List<Dose> doses) {
        public ArticleChoice {
            doses = List.copyOf(doses);
            reason = reason == null ? "" : reason;
        }

        public boolean available() {
            return reason.isBlank();
        }

        public BigDecimal weight() {
            return doses.stream().map(Dose::grams).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public BigDecimal thickness() {
            return doses.stream().map(Dose::height).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    public record Workspace(
            ProductChoice product, BigDecimal diameter, List<ArticleChoice> articles) {
        public Workspace {
            articles = List.copyOf(articles);
        }
    }

    public record Request(
            LocalDate date,
            long productId,
            BigDecimal diameter,
            Long templateId,
            String notes,
            Map<Long, Integer> quantities) {
        public Request {
            if (date == null) throw invalid("Indica la data di produzione.");
            if (productId <= 0 || diameter == null || diameter.signum() <= 0)
                throw invalid("Seleziona prodotto e diametro.");
            notes = notes == null ? "" : notes.strip();
            if (notes.length() > 1000) throw invalid("Note: massimo 1000 caratteri.");
            quantities = Collections.unmodifiableMap(new TreeMap<>(quantities));
            for (var e : quantities.entrySet())
                if (e.getKey() <= 0 || e.getValue() == null || e.getValue() < 0)
                    throw invalid("Le quantità devono essere numeri interi non negativi.");
        }
    }

    public record Format(ArticleChoice article, int quantity) {}

    public record PowderWeight(
            long revisionId, String code, String name, BigDecimal percentage, BigDecimal grams) {}

    public record Mixture(
            int number,
            int count,
            BigDecimal needed,
            BigDecimal prepared,
            List<PowderWeight> powders) {
        public Mixture {
            powders = List.copyOf(powders);
        }
    }

    public record Plan(
            int schemaVersion,
            String calculation,
            ProductChoice product,
            BigDecimal diameter,
            String date,
            String notes,
            TemplateChoice template,
            List<Format> formats,
            List<Mixture> mixtures,
            int count,
            BigDecimal needed,
            BigDecimal prepared) {
        public Plan {
            formats = List.copyOf(formats);
            mixtures = List.copyOf(mixtures);
        }

        public ArticleChoice reference() {
            return formats.isEmpty() ? null : formats.getFirst().article();
        }

        public Map<Long, Integer> quantities() {
            var values = new TreeMap<Long, Integer>();
            for (var f : formats) values.put(f.article().id(), f.quantity());
            return values;
        }

        public Request request() {
            return new Request(
                    LocalDate.parse(date),
                    product.id(),
                    diameter,
                    template == null ? null : template.id(),
                    notes,
                    quantities());
        }
    }

    public record Saved(long id, int token, State state, Plan plan, String document) {
        public String number() {
            return "PR-" + String.format(Locale.ROOT, "%06d", id);
        }
    }

    public record Summary(
            long id,
            int token,
            State state,
            String date,
            String product,
            int formats,
            int count,
            String document) {
        public String number() {
            return "PR-" + String.format(Locale.ROOT, "%06d", id);
        }
    }

    public static IllegalArgumentException invalid(String text) {
        return new IllegalArgumentException(text);
    }
}
