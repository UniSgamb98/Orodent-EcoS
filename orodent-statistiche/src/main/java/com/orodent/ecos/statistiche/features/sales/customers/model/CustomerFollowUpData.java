package com.orodent.ecos.statistiche.features.sales.customers.model;

import java.time.LocalDate;
import java.util.List;

public record CustomerFollowUpData(int year, LocalDate reference, List<CustomerFollowUpItem> items) {
    public CustomerFollowUpData { items = List.copyOf(items); }
}
