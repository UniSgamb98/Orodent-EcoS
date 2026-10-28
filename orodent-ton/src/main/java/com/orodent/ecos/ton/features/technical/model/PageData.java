package com.orodent.ecos.ton.features.technical.model;

import java.util.List;

/** Read-only pagination contract shared by technical catalogues. */
public interface PageData<T> {
    List<T> rows();

    long total();

    int offset();

    int limit();

    default boolean hasNext() {
        return offset() + rows().size() < total();
    }

    default String label() {
        return total() == 0
                ? "Nessun risultato"
                : (offset() + 1) + "–" + (offset() + rows().size()) + " di " + total();
    }
}
