package com.orodent.ecos.crm.feature.clienti.clienti.dto;

public record ClientePreview(
        String name,
        String type,
        String contact,
        String address,
        String operator,
        String status,
        String lastContact) {}
