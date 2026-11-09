package com.orodent.ecos.costi.products.service;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.costi.products.model.ProductCosts.*;
import com.orodent.ecos.costi.products.repository.ProductCostRepository;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;

/** Shared workers are borrowed; JDBC and costing never execute on the JavaFX thread. */
public final class ProductCostService {
    private final ConnectionProvider connections;
    private final Executor workers;

    public ProductCostService(ConnectionProvider connections, Executor workers) {
        this.connections = Objects.requireNonNull(connections);
        this.workers = Objects.requireNonNull(workers);
    }

    public CompletableFuture<Catalogue> load() {
        return CompletableFuture.supplyAsync(
                () ->
                        connections.withTransaction(
                                c -> {
                                    c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                                    c.setReadOnly(true);
                                    return new ProductCostRepository(c).load();
                                }),
                workers);
    }

    public CompletableFuture<Parameters> save(long product, Parameters parameters) {
        return CompletableFuture.supplyAsync(
                () ->
                        connections.withTransaction(
                                c -> new ProductCostRepository(c).save(product, parameters)),
                workers);
    }

    public CompletableFuture<List<Result>> calculate(
            Catalogue catalogue,
            Collection<Long> products,
            Map<Long, Parameters> drafts,
            boolean alternatives,
            boolean archived) {
        var ids = List.copyOf(products);
        var parameters = Map.copyOf(drafts);
        return CompletableFuture.supplyAsync(
                () -> {
                    var calculator = new ProductCostCalculator();
                    var result = new ArrayList<Result>();
                    for (long id : ids)
                        result.addAll(
                                calculator.product(
                                        catalogue,
                                        id,
                                        parameters.getOrDefault(id, catalogue.parameters(id)),
                                        alternatives,
                                        archived));
                    return List.copyOf(result);
                },
                workers);
    }
}
