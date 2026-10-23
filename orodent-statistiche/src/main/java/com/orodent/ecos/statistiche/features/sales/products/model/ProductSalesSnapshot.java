package com.orodent.ecos.statistiche.features.sales.products.model;

import java.time.LocalDate;
import java.util.List;
public record ProductSalesSnapshot(List<Integer> years, int year, LocalDate reference, LocalDate dataFrom,
                                   List<ProductSale> sales) {
    public ProductSalesSnapshot { years = List.copyOf(years); sales = List.copyOf(sales); }
}
