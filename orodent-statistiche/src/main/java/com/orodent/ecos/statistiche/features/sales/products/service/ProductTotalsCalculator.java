package com.orodent.ecos.statistiche.features.sales.products.service;

import com.orodent.ecos.statistiche.features.sales.products.model.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ProductTotalsCalculator {
    public ProductTotals total(List<ProductSale> sales) {
        BigDecimal revenue = BigDecimal.ZERO, quantity = BigDecimal.ZERO;
        BigDecimal returnedRevenue = BigDecimal.ZERO, returnedQuantity = BigDecimal.ZERO;
        Set<String> customers = new HashSet<>();
        Set<DocumentKey> documents = new HashSet<>();
        for (ProductSale sale : sales) {
            revenue = revenue.add(sale.revenue()); quantity = quantity.add(sale.quantity());
            if (sale.sale()) {
                customers.add(sale.customerCode());
                documents.add(new DocumentKey(sale.customerCode(), sale.document()));
            } else {
                returnedRevenue = returnedRevenue.add(sale.revenue().abs());
                returnedQuantity = returnedQuantity.add(sale.quantity().abs());
            }
        }
        return new ProductTotals(revenue, quantity, customers.size(), documents.size(), returnedRevenue, returnedQuantity);
    }

    public BigDecimal change(BigDecimal current, BigDecimal previous, boolean comparable) {
        if (!comparable || previous.signum() <= 0) return null;
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 1, RoundingMode.HALF_UP);
    }

    public static BigDecimal share(BigDecimal value, BigDecimal total) {
        return total.signum() <= 0 ? null : value.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    public static BigDecimal unitValue(ProductTotals totals) {
        return totals.quantity().signum() <= 0 ? null : totals.revenue().divide(totals.quantity(), 2, RoundingMode.HALF_UP);
    }

    private record DocumentKey(String customer, String document) { }
}
