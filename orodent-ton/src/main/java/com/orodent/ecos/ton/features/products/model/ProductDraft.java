package com.orodent.ecos.ton.features.products.model;

import java.util.Locale;

public record ProductDraft(String code, String description, boolean active) {
    public ProductDraft {
        code = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        description = description == null ? "" : description.strip();
        if (code.isEmpty()) throw new IllegalArgumentException("Inserisci il codice del prodotto.");
        if (code.length() > 50) throw new IllegalArgumentException("Il codice può contenere al massimo 50 caratteri.");
        if (description.length() > 200) throw new IllegalArgumentException("La descrizione può contenere al massimo 200 caratteri.");
    }
}
