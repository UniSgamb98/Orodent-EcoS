package com.orodent.ecos.ton.features.products.service;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.ton.features.products.model.ProductDraft;
import com.orodent.ecos.ton.features.products.model.ProductPage;
import com.orodent.ecos.ton.features.products.model.TonProduct;
import com.orodent.ecos.ton.features.products.repository.ProductRepository;

import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class ProductService {
    public static final int PAGE_SIZE = 50;
    private final ConnectionProvider connections;
    private final Executor workers;

    public ProductService(ConnectionProvider connections, Executor workers) {
        this.connections = Objects.requireNonNull(connections);
        this.workers = Objects.requireNonNull(workers);
    }

    public CompletableFuture<ProductPage> findPage(String search, boolean includeInactive, int offset) {
        String filter = search == null ? "" : search.strip();
        int safeOffset = Math.max(0, offset);
        return CompletableFuture.supplyAsync(() -> connections.withConnection(connection ->
                new ProductRepository(connection).findPage(filter, includeInactive, safeOffset, PAGE_SIZE)), workers);
    }

    public CompletableFuture<TonProduct> create(ProductDraft draft) {
        Objects.requireNonNull(draft);
        return save(() -> connections.withTransaction(connection -> new ProductRepository(connection).insert(draft)));
    }

    public CompletableFuture<TonProduct> update(TonProduct original, ProductDraft draft) {
        Objects.requireNonNull(original); Objects.requireNonNull(draft);
        if (original.id() <= 0 || original.revision() <= 0) throw new IllegalArgumentException("Prodotto non valido.");
        return save(() -> connections.withTransaction(connection -> new ProductRepository(connection).update(original, draft)));
    }

    private CompletableFuture<TonProduct> save(Supplier<TonProduct> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try { return operation.get(); }
            catch (RuntimeException failure) {
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                    if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState()))
                        throw new IllegalArgumentException("Esiste già un prodotto con questo codice.", failure);
                }
                throw failure;
            }
        }, workers);
    }
}
