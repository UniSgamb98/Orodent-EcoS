package com.orodent.ecos.ton.features.catalog.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** Immutable catalogue data. No JavaFX properties or live JDBC objects cross worker boundaries. */
public final class CatalogModels {
    private CatalogModels() {}

    public record Metadata(
            long id,
            String code,
            String name,
            String notes,
            boolean active,
            int revision,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        public Metadata {
            name = text(name);
            notes = text(notes);
        }
    }

    public sealed interface Entry permits Line, Product, Article {
        Metadata meta();

        CatalogKind kind();

        boolean used();

        boolean complete();

        boolean available();

        default long id() {
            return meta().id();
        }

        default int revision() {
            return meta().revision();
        }

        default String code() {
            return meta().code();
        }

        default String name() {
            return meta().name();
        }

        default boolean active() {
            return meta().active();
        }

        default String status() {
            return !active()
                    ? "Archiviato"
                    : !complete() ? "Da completare" : !available() ? "Padre archiviato" : "Attivo";
        }
    }

    /** usable includes all ancestors, and is distinct from the record's own active flag. */
    public record Choice(
            long id,
            String code,
            String name,
            boolean usable,
            Long lineId,
            String lineCode,
            String color) {
        @Override
        public String toString() {
            return code + (text(name).isEmpty() ? "" : " · " + name);
        }
    }

    public record Line(
            Metadata meta, String technology, String material, int products, boolean used)
            implements Entry {
        public Line {
            technology = text(technology);
            material = text(material);
        }

        @Override
        public CatalogKind kind() {
            return CatalogKind.LINES;
        }

        @Override
        public boolean complete() {
            return true;
        }

        @Override
        public boolean available() {
            return active();
        }
    }

    public record Product(Metadata meta, Choice line, String color, int articles, boolean used)
            implements Entry {
        @Override
        public CatalogKind kind() {
            return CatalogKind.PRODUCTS;
        }

        @Override
        public boolean complete() {
            return line != null && color != null;
        }

        @Override
        public boolean available() {
            return active() && complete() && line.usable();
        }

        public String colorLabel() {
            return colorLabelOf(color);
        }
    }

    public record Article(
            Metadata meta,
            Choice product,
            BigDecimal diameter,
            BigDecimal height,
            Long defaultSpecification,
            String specificationLabel,
            boolean used)
            implements Entry {
        @Override
        public CatalogKind kind() {
            return CatalogKind.ARTICLES;
        }

        @Override
        public boolean complete() {
            return product.lineId() != null && product.color() != null;
        }

        @Override
        public boolean available() {
            return active() && complete() && product.usable();
        }
    }

    public sealed interface Draft permits LineDraft, ProductDraft, ArticleDraft {
        String code();

        String name();

        String notes();

        boolean active();

        CatalogKind kind();
    }

    public record LineDraft(
            String code,
            String name,
            String technology,
            String material,
            String notes,
            boolean active)
            implements Draft {
        public LineDraft {
            code = CatalogModels.code(code, 50);
            name = required(name, 100, "nome della linea");
            technology = limited(technology, 200, "Tecnologia");
            material = limited(material, 30, "Materia prima").toUpperCase(Locale.ROOT);
            notes = limited(notes, 1000, "Note");
        }

        @Override
        public CatalogKind kind() {
            return CatalogKind.LINES;
        }
    }

    public record ProductDraft(
            String code, String name, Long lineId, String color, String notes, boolean active)
            implements Draft {
        public ProductDraft {
            code = CatalogModels.code(code, 50);
            name = limited(name, 200, "Descrizione");
            notes = limited(notes, 1000, "Note");
            color = commercialColor(color);
            if (lineId != null && lineId <= 0) throw invalid("Seleziona una linea valida.");
        }

        @Override
        public CatalogKind kind() {
            return CatalogKind.PRODUCTS;
        }

        public void requireComplete() {
            if (lineId == null || color == null)
                throw invalid("Seleziona la linea e il colore commerciale del prodotto.");
        }
    }

    public record ArticleDraft(
            String code,
            String name,
            long productId,
            BigDecimal diameter,
            BigDecimal height,
            String notes,
            boolean active)
            implements Draft {
        public ArticleDraft {
            code = CatalogModels.code(code, 80);
            name = limited(name, 200, "Descrizione");
            notes = limited(notes, 1000, "Note");
            if (productId <= 0) throw invalid("Seleziona il prodotto dell'articolo.");
            diameter = measure(diameter, "Diametro");
            height = measure(height, "Altezza");
        }

        @Override
        public CatalogKind kind() {
            return CatalogKind.ARTICLES;
        }
    }

    public record Filter(
            String search,
            boolean archived,
            Long lineId,
            Long productId,
            BigDecimal diameter,
            BigDecimal height) {
        public Filter {
            search = text(search);
            if (diameter != null) diameter = measure(diameter, "Diametro");
            if (height != null) height = measure(height, "Altezza");
        }

        public static Filter empty() {
            return new Filter("", false, null, null, null, null);
        }
    }

    public record Page(List<Entry> rows, long total, int offset, int limit) {
        public Page {
            rows = List.copyOf(rows);
        }

        public boolean hasNext() {
            return offset + rows.size() < total;
        }
    }

    public record Audit(Entry snapshot, int number, LocalDateTime recordedAt, String source) {
        public String sourceLabel() {
            return switch (source) {
                case "MIGRAZIONE" -> "Stato iniziale disponibile";
                case "CREAZIONE" -> "Creazione";
                case "ARCHIVIAZIONE" -> "Archiviazione";
                case "RIATTIVAZIONE" -> "Riattivazione";
                default -> "Modifica";
            };
        }
    }

    public record HistoryPage(List<Audit> rows, long total, int offset, int limit) {
        public HistoryPage {
            rows = List.copyOf(rows);
        }

        public boolean hasNext() {
            return offset + rows.size() < total;
        }
    }

    public record Detail(Entry entry, boolean deletable, List<Choice> previousLines) {
        public Detail {
            previousLines = List.copyOf(previousLines);
        }
    }

    public record Navigation(CatalogKind kind, Long parentFilter, Long openId, boolean create) {
        public static Navigation list(CatalogKind kind) {
            return new Navigation(kind, null, null, false);
        }

        public static Navigation open(CatalogKind kind, long id) {
            return new Navigation(kind, null, id, false);
        }
    }

    public record Specification(
            long id,
            long articleId,
            int number,
            int revision,
            String state,
            long compositionId,
            String compositionLabel,
            long modelId,
            String modelLabel,
            String calculation,
            String notes,
            LocalDateTime approvedAt,
            Integer lineRevision,
            Integer productRevision,
            Integer articleRevision,
            boolean preferred) {
        public boolean frozen() {
            return !"BOZZA".equals(state);
        }

        public boolean recorded() {
            return lineRevision != null && productRevision != null && articleRevision != null;
        }

        @Override
        public String toString() {
            return "v"
                    + number
                    + " · "
                    + switch (state) {
                        case "APPROVATA" -> "Approvata";
                        case "RITIRATA" -> "Ritirata";
                        default -> "Bozza";
                    }
                    + (preferred ? " · predefinita" : "");
        }
    }

    public record SpecificationDraft(
            long compositionId, long modelId, String calculation, String notes) {
        public SpecificationDraft {
            if (compositionId <= 0 || modelId <= 0)
                throw invalid("Seleziona composizione e modello.");
            calculation = required(calculation, 50, "versione di calcolo");
            notes = limited(notes, 1000, "Note");
        }
    }

    public record TechnicalChoice(long id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    public record SpecificationPage(
            List<Specification> rows,
            long total,
            int offset,
            int limit,
            List<TechnicalChoice> compositions,
            List<TechnicalChoice> models) {
        public SpecificationPage {
            rows = List.copyOf(rows);
            compositions = List.copyOf(compositions);
            models = List.copyOf(models);
        }

        public boolean hasNext() {
            return offset + rows.size() < total;
        }
    }

    public static Draft draft(Entry e) {
        return switch (e) {
            case Line l ->
                    new LineDraft(
                            l.code(),
                            l.name(),
                            l.technology(),
                            l.material(),
                            l.meta().notes(),
                            l.active());
            case Product p ->
                    new ProductDraft(
                            p.code(),
                            p.name(),
                            p.line() == null ? null : p.line().id(),
                            p.color(),
                            p.meta().notes(),
                            p.active());
            case Article a ->
                    new ArticleDraft(
                            a.code(),
                            a.name(),
                            a.product().id(),
                            a.diameter(),
                            a.height(),
                            a.meta().notes(),
                            a.active());
        };
    }

    public static String colorLabelOf(String color) {
        return color == null
                ? "Da specificare"
                : "SENZA_COLORE".equals(color) ? "Senza colore" : color;
    }

    public static String text(String value) {
        return value == null ? "" : value.strip();
    }

    public static String commercialColor(String value) {
        String c = text(value).toUpperCase(Locale.ROOT);
        if (c.isEmpty()) return null;
        if (c.equals("SENZA COLORE")) c = "SENZA_COLORE";
        return limited(c, 30, "Colore commerciale");
    }

    private static String code(String value, int max) {
        return required(value, max, "codice").toUpperCase(Locale.ROOT);
    }

    private static String required(String value, int max, String label) {
        String v = limited(value, max, label);
        if (v.isBlank()) throw invalid("Inserisci il " + label + ".");
        return v;
    }

    private static String limited(String value, int max, String label) {
        String v = text(value);
        if (v.length() > max) throw invalid(label + ": massimo " + max + " caratteri.");
        return v;
    }

    private static BigDecimal measure(BigDecimal v, String label) {
        if (v == null || v.signum() <= 0)
            throw invalid(label + ": inserisci un valore maggiore di zero.");
        try {
            if (v.setScale(2, RoundingMode.UNNECESSARY).precision() > 6)
                throw invalid(label + ": valore troppo grande.");
        } catch (ArithmeticException e) {
            throw invalid(label + ": massimo due cifre decimali, senza arrotondamento.");
        }
        return v.stripTrailingZeros();
    }

    public static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }

    public static final class Conflict extends RuntimeException {
        public Conflict() {
            super(
                    "L'anagrafica è stata modificata da un altro operatore. Ricarica la scheda"
                        + " prima di riprovare; le tue modifiche restano nell'editor.");
        }
    }
}
