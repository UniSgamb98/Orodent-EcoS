package com.orodent.ecos.ton.features.products.model;

public final class ProductConflictException extends IllegalStateException {
    public ProductConflictException() {
        super("Il prodotto è stato modificato da un'altra postazione. Aggiorna l'elenco e riapri il prodotto prima di salvare.");
    }
}
