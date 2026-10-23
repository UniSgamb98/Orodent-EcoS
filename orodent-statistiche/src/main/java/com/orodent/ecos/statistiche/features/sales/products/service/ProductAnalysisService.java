package com.orodent.ecos.statistiche.features.sales.products.service;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.statistiche.features.sales.products.model.*;
import com.orodent.ecos.statistiche.features.sales.products.repository.*;
import java.sql.Connection;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

public final class ProductAnalysisService {
    private final ConnectionProvider connections;
    private final Executor executor;
    private final Function<Connection, ProductAnalysisRepository> repositories;
    private final Clock clock;
    private final ProductAnalysisCalculator calculator = new ProductAnalysisCalculator();

    public ProductAnalysisService(ConnectionProvider connections, Executor executor) {
        this(connections, executor, ProductAnalysisRepositoryImpl::new, Clock.systemDefaultZone());
    }

    ProductAnalysisService(ConnectionProvider connections, Executor executor,
                           Function<Connection, ProductAnalysisRepository> repositories, Clock clock) {
        this.connections = java.util.Objects.requireNonNull(connections);
        this.executor = java.util.Objects.requireNonNull(executor);
        this.repositories = java.util.Objects.requireNonNull(repositories);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public CompletableFuture<ProductOverview> load(Integer requestedYear, ProductGrouping grouping) {
        return CompletableFuture.supplyAsync(() -> connections.withConnection(connection -> {
            ProductAnalysisRepository repository = repositories.apply(connection);
            List<Integer> years = repository.findYears();
            int year = requestedYear != null && years.contains(requestedYear) ? requestedYear
                    : years.isEmpty() ? LocalDate.now(clock).getYear() : years.getFirst();
            var coverage = repository.loadCoverage();
            LocalDate reference = LocalDate.of(year, 12, 31);
            if (coverage != null && coverage.through().isBefore(reference)) reference = coverage.through();
            if (LocalDate.now(clock).isBefore(reference)) reference = LocalDate.now(clock);
            ProductSalesSnapshot snapshot = new ProductSalesSnapshot(years, year, reference,
                    coverage == null ? reference : coverage.from(),
                    years.isEmpty() ? List.of() : repository.loadSales(LocalDate.of(years.stream().min(Integer::compareTo).orElse(year), 1, 1), LocalDate.of(year, 12, 31)));
            return calculator.overview(snapshot, grouping);
        }), executor);
    }

    public CompletableFuture<ProductOverview> regroup(ProductSalesSnapshot snapshot, ProductGrouping grouping) {
        return CompletableFuture.supplyAsync(() -> calculator.overview(snapshot, grouping), executor);
    }

    public CompletableFuture<ProductDetailData> detail(ProductSalesSnapshot snapshot, ProductGroupSummary group) {
        return CompletableFuture.supplyAsync(() -> calculator.detail(snapshot, group), executor);
    }
}
