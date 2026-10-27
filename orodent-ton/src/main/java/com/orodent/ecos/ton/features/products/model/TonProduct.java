package com.orodent.ecos.ton.features.products.model;

import java.time.LocalDateTime;

/** Technical family/material, independent of the sales article codes. */
public record TonProduct(long id, String code, String description, boolean active, int revision,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
    public TonProduct {
        description = description == null ? "" : description;
    }
}
