package com.orodent.ecos.crm.feature.clienti.clienti.dto;

import java.util.UUID;

public record OperatoreFilter(UUID id, String label) {
    public static OperatoreFilter empty() {
        return new OperatoreFilter(null, "Tutti");
    }

    @Override
    public String toString() {
        return label;
    }
}
