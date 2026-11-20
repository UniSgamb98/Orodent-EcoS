package com.orodent.ecos.ton.features.equipment.model;

import java.math.*;
import java.util.*;

/**
 * Physical equipment identities. Process targets and production history belong to their own
 * records.
 */
public final class Equipment {
    private Equipment() {}

    public enum Kind {
        PRESSES(
                "presses",
                "PRESSE",
                "pressa_id",
                "alesaggio_mm",
                "Presse",
                "Nuova pressa",
                "← Torna alle presse",
                "Alesaggio · mm",
                "Anagrafiche delle presse e alesaggio del pistone idraulico."),
        MOULDS(
                "moulds",
                "STAMPI",
                "stampo_id",
                "diametro_mm",
                "Stampi",
                "Nuovo stampo",
                "← Torna agli stampi",
                "Diametro stampo · mm",
                "Anagrafiche degli stampi e diametro di pressatura."),
        FURNACES(
                "furnaces",
                "FORNI",
                "forno_id",
                null,
                "Forni",
                "Nuovo forno",
                "← Torna ai forni",
                null,
                "Anagrafiche dei forni e disponibilità per le lavorazioni.");
        private final String id, table, key, measure, title, create, back, dimension, description;

        Kind(
                String id,
                String table,
                String key,
                String measure,
                String title,
                String create,
                String back,
                String dimension,
                String description) {
            this.id = id;
            this.table = table;
            this.key = key;
            this.measure = measure;
            this.title = title;
            this.create = create;
            this.back = back;
            this.dimension = dimension;
            this.description = description;
        }

        public String id() {
            return "ton-" + id;
        }

        public String table() {
            return table;
        }

        public String key() {
            return key;
        }

        public String measure() {
            return measure;
        }

        public String title() {
            return title;
        }

        public String create() {
            return create;
        }

        public String back() {
            return back;
        }

        public String dimension() {
            return dimension;
        }

        public String description() {
            return description;
        }
    }

    public record Entry(
            long id,
            int revision,
            String code,
            String name,
            BigDecimal dimension,
            String notes,
            boolean active) {
        public Entry {
            notes = Objects.toString(notes, "");
        }

        public Draft draft() {
            return new Draft(code, name, dimension, notes, active);
        }
    }

    public record Draft(
            String code, String name, BigDecimal dimension, String notes, boolean active) {
        public Draft {
            code = Objects.toString(code, "").strip().toUpperCase(Locale.ROOT);
            name = Objects.toString(name, "").strip();
            notes = Objects.toString(notes, "").strip();
            if (code.isBlank() || code.length() > 50)
                throw new IllegalArgumentException("Codice: inserisci da 1 a 50 caratteri.");
            if (name.isBlank() || name.length() > 100)
                throw new IllegalArgumentException("Nome: inserisci da 1 a 100 caratteri.");
            if (notes.length() > 1000)
                throw new IllegalArgumentException("Note: massimo 1000 caratteri.");
            if (dimension != null) {
                try {
                    dimension = dimension.setScale(3, RoundingMode.UNNECESSARY);
                    if (dimension.signum() <= 0 || dimension.precision() > 10)
                        throw new ArithmeticException();
                    dimension = dimension.stripTrailingZeros();
                } catch (ArithmeticException invalid) {
                    throw new IllegalArgumentException(
                            "La misura deve essere positiva, con massimo tre decimali e sette cifre"
                                    + " intere.");
                }
            }
        }

        public void validate(Kind kind) {
            if (kind.measure() != null && dimension == null)
                throw new IllegalArgumentException(
                        kind.dimension() + ": inserisci una misura positiva.");
            if (kind.measure() == null && dimension != null)
                throw new IllegalArgumentException("Questa misura non si applica ai forni.");
        }
    }

    public static final class Conflict extends IllegalStateException {
        public Conflict() {
            super(
                    "L’anagrafica è stata modificata da un’altra postazione. Aggiorna i dati prima"
                            + " di salvare.");
        }
    }
}
