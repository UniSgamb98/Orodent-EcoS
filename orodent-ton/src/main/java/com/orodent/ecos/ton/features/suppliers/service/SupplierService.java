package com.orodent.ecos.ton.features.suppliers.service;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.SupplierCatalogue;
import com.orodent.ecos.database.SupplierCatalogue.*;

import java.util.*;
import java.util.concurrent.*;

public final class SupplierService {
    private final ConnectionProvider db;
    private final Executor workers;

    public SupplierService(ConnectionProvider db, Executor workers) {
        this.db = Objects.requireNonNull(db);
        this.workers = Objects.requireNonNull(workers);
    }

    public CompletableFuture<List<Entry>> list(String query, boolean archived) {
        return CompletableFuture.supplyAsync(
                () -> db.withConnection(c -> SupplierCatalogue.list(c, query, archived)), workers);
    }

    public CompletableFuture<Entry> load(long id) {
        return CompletableFuture.supplyAsync(
                () -> db.withConnection(c -> SupplierCatalogue.load(c, id)), workers);
    }

    public CompletableFuture<Entry> save(Entry original, Draft draft) {
        return CompletableFuture.supplyAsync(
                () -> db.withTransaction(c -> SupplierCatalogue.save(c, original, draft)), workers);
    }
}
