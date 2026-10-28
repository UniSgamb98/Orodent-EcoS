package com.orodent.ecos.statistiche.features.sales.products.service;

import com.orodent.ecos.statistiche.features.sales.products.model.ProductDocumentKey;
import com.orodent.ecos.statistiche.features.sales.products.model.ProductSale;
import com.orodent.ecos.statistiche.features.sales.products.model.ProductSalesPace;
import com.orodent.ecos.statistiche.features.sales.products.model.ProductSalesSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Includes calendar days with no sales and measures document sizes after combining their lines. */
public final class ProductSalesPaceCalculator {
    private static final int RECENT_DAYS = 91;
    private static final BigDecimal SEVEN = BigDecimal.valueOf(7);

    public ProductSalesPace calculate(ProductSalesSnapshot snapshot, List<ProductSale> sales) {
        LocalDate from = later(LocalDate.of(snapshot.year(), 1, 1), snapshot.dataFrom());
        LocalDate through = earlier(LocalDate.of(snapshot.year(), 12, 31), snapshot.reference());
        LocalDate recentFrom = later(from, through.minusDays(RECENT_DAYS - 1));
        long days = days(from, through), recentDays = days(recentFrom, through);
        Map<ProductDocumentKey, BigDecimal> documents = new HashMap<>();
        BigDecimal sold = BigDecimal.ZERO, recent = BigDecimal.ZERO;
        for (ProductSale sale : sales) {
            if (!sale.sale() || sale.quantity().signum() <= 0
                    || sale.date().isBefore(from) || sale.date().isAfter(through)) continue;
            sold = sold.add(sale.quantity());
            documents.merge(ProductDocumentKey.of(sale), sale.quantity(), BigDecimal::add);
            if (!sale.date().isBefore(recentFrom)) recent = recent.add(sale.quantity());
        }
        List<BigDecimal> quantities = documents.values().stream().sorted().toList();
        BigDecimal mean = quantities.isEmpty() ? null : sold.divide(BigDecimal.valueOf(quantities.size()), 8, RoundingMode.HALF_UP);
        BigDecimal median = null;
        if (!quantities.isEmpty()) {
            int middle = quantities.size() / 2;
            median = quantities.size() % 2 == 1 ? quantities.get(middle)
                    : quantities.get(middle - 1).add(quantities.get(middle)).divide(BigDecimal.valueOf(2));
        }
        return new ProductSalesPace(from, through, days, sold, quantities.size(), weekly(sold, days), mean, median,
                recentFrom, recentDays, weekly(recent, recentDays));
    }

    private static BigDecimal weekly(BigDecimal quantity, long days) {
        return days <= 0 ? null : quantity.multiply(SEVEN).divide(BigDecimal.valueOf(days), 8, RoundingMode.HALF_UP);
    }

    private static long days(LocalDate from, LocalDate through) {
        return through.isBefore(from) ? 0 : ChronoUnit.DAYS.between(from, through) + 1;
    }

    private static LocalDate earlier(LocalDate a, LocalDate b) { return a.isBefore(b) ? a : b; }
    private static LocalDate later(LocalDate a, LocalDate b) { return a.isAfter(b) ? a : b; }
}
