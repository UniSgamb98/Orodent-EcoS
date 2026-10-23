package com.orodent.ecos.statistiche.features.sales.products.service;

import com.orodent.ecos.statistiche.features.sales.products.model.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pure aggregation over a bounded snapshot; grouping and details never need another database read. */
public final class ProductAnalysisCalculator {
    private final ProductCodeParser parser = new ProductCodeParser();
    private final ProductTotalsCalculator totals = new ProductTotalsCalculator();

    public ProductOverview overview(ProductSalesSnapshot snapshot, ProductGrouping grouping) {
        Map<String, Optional<ProductAttributes>> attributes = attributes(snapshot);
        List<ProductSale> current = current(snapshot, snapshot.sales());
        Map<ProductGroupKey, List<ProductSale>> classified = current.stream()
                .filter(sale -> attributes.get(sale.code()).isPresent())
                .collect(Collectors.groupingBy(sale -> ProductGroupKey.of(attributes.get(sale.code()).orElseThrow(), grouping)));
        Map<ProductGroupKey, List<ProductSale>> histories = snapshot.sales().stream()
                .filter(sale -> attributes.get(sale.code()).isPresent())
                .collect(Collectors.groupingBy(sale -> ProductGroupKey.of(attributes.get(sale.code()).orElseThrow(), grouping)));
        List<ProductGroupSummary> groups = new ArrayList<>();
        for (var entry : classified.entrySet()) {
            groups.add(summary(snapshot, entry.getKey(), entry.getValue(), histories.get(entry.getKey())));
        }
        groups.sort(Comparator.comparing((ProductGroupSummary group) -> group.current().quantity()).reversed()
                .thenComparing(group -> group.key().label()));
        List<ProductSale> other = current.stream().filter(sale -> attributes.get(sale.code()).isEmpty()).toList();
        List<UnclassifiedProduct> unclassified = group(other, ProductSale::code).entrySet().stream()
                .map(entry -> new UnclassifiedProduct(entry.getKey(), entry.getValue().getFirst().description(), totals.total(entry.getValue())))
                .sorted(Comparator.comparing(UnclassifiedProduct::code)).toList();
        return new ProductOverview(snapshot, grouping, groups,
                totals.total(current.stream().filter(sale -> attributes.get(sale.code()).isPresent()).toList()),
                unclassified, totals.total(other));
    }

    public ProductDetailData detail(ProductSalesSnapshot snapshot, ProductGroupSummary summary) {
        Map<String, Optional<ProductAttributes>> attributes = attributes(snapshot);
        List<ProductSale> history = snapshot.sales().stream()
                .filter(sale -> attributes.get(sale.code()).map(summary.key()::matches).orElse(false)).toList();
        List<ProductSale> current = current(snapshot, history);
        List<ProductMonthlyValue> monthly = new ArrayList<>();
        for (int year = snapshot.year() - 1; year <= snapshot.year(); year++) {
            int lastMonth = year == snapshot.year() ? snapshot.reference().getMonthValue() : 12;
            for (int month = 1; month <= lastMonth; month++) {
                int selectedYear = year, selectedMonth = month;
                monthly.add(new ProductMonthlyValue(year, month, totals.total(history.stream()
                        .filter(sale -> sale.date().getYear() == selectedYear && sale.date().getMonthValue() == selectedMonth
                                && (selectedYear != snapshot.year() || !sale.date().isAfter(snapshot.reference()))).toList())));
            }
        }
        List<ProductCustomerValue> customers = group(current, ProductSale::customerCode).entrySet().stream()
                .map(entry -> new ProductCustomerValue(entry.getKey(), entry.getValue().getLast().customerName(), totals.total(entry.getValue())))
                .sorted(Comparator.comparing((ProductCustomerValue customer) -> customer.totals().quantity()).reversed()
                        .thenComparing(ProductCustomerValue::code)).toList();
        Map<ProductVariantDimension, List<ProductVariantValue>> variants = new EnumMap<>(ProductVariantDimension.class);
        for (ProductVariantDimension dimension : ProductVariantDimension.values()) {
            if (!dimension.availableFor(summary.key())) continue;
            variants.put(dimension, group(current, sale -> dimension.value(attributes.get(sale.code()).orElseThrow())).entrySet().stream()
                    .map(entry -> new ProductVariantValue(entry.getKey(), totals.total(entry.getValue()))).toList());
        }
        return new ProductDetailData(summary, snapshot, monthly, customers, variants,
                new ProductProjectionService().calculate(snapshot, summary, history));
    }

    private ProductGroupSummary summary(ProductSalesSnapshot snapshot, ProductGroupKey key,
                                        List<ProductSale> current, List<ProductSale> history) {
        ProductTotals actual = totals.total(current);
        LocalDate from = LocalDate.of(snapshot.year() - 1, 1, 1);
        LocalDate through = snapshot.reference().minusYears(1);
        ProductTotals previous = totals.total(history.stream().filter(sale -> !sale.date().isBefore(from) && !sale.date().isAfter(through)).toList());
        boolean comparable = !snapshot.dataFrom().isAfter(from);
        return new ProductGroupSummary(key, actual, previous,
                totals.change(actual.revenue(), previous.revenue(), comparable),
                totals.change(actual.quantity(), previous.quantity(), comparable));
    }

    private List<ProductSale> current(ProductSalesSnapshot snapshot, List<ProductSale> sales) {
        return sales.stream().filter(sale -> sale.date().getYear() == snapshot.year() && !sale.date().isAfter(snapshot.reference())).toList();
    }

    private Map<String, Optional<ProductAttributes>> attributes(ProductSalesSnapshot snapshot) {
        return snapshot.sales().stream().map(ProductSale::code).distinct().collect(Collectors.toMap(Function.identity(), parser::parse));
    }

    private <K> Map<K, List<ProductSale>> group(List<ProductSale> sales, Function<ProductSale, K> key) {
        return sales.stream().collect(Collectors.groupingBy(key));
    }
}
