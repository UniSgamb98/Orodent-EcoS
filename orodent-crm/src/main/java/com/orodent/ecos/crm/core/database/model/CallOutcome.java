package com.orodent.ecos.crm.core.database.model;

import java.util.Arrays;
import java.util.Optional;

public enum CallOutcome {
    NON_TROVATO("Non trovato", null),
    NON_INERENTE("Non inerente", "Non inerente"),
    PRESENTAZIONE("Presentazione", "Presentazione"),
    LISTINO("Listino", "Listino"),
    CAMPIONE("Campione", "Campione"),
    CLIENTE("Cliente", "Cliente");

    private final String label;
    private final String statoTrattativa;

    CallOutcome(String label, String statoTrattativa) {
        this.label = label;
        this.statoTrattativa = statoTrattativa;
    }

    public String label() {
        return label;
    }

    public Optional<String> statoTrattativa() {
        return Optional.ofNullable(statoTrattativa);
    }

    public static CallOutcome fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(outcome -> outcome.name().equals(code))
                .findFirst()
                .orElse(null);
    }

    @Override
    public String toString() {
        return label;
    }
}
