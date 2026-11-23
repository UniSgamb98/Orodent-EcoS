package com.orodent.ecos.ton.features.cubing.service;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.CubatureRuleCatalogue;
import com.orodent.ecos.database.CubatureRuleCatalogue.*;

import java.util.*;
import java.util.concurrent.*;

public final class CubatureRuleService {
    private final ConnectionProvider db;
    private final Executor workers;

    public CubatureRuleService(ConnectionProvider db, Executor workers) {
        this.db = Objects.requireNonNull(db);
        this.workers = Objects.requireNonNull(workers);
    }

    public CompletableFuture<List<Entry>> list(String query, boolean archived) {
        return CompletableFuture.supplyAsync(
                () -> db.withConnection(c -> CubatureRuleCatalogue.list(c, query, archived)),
                workers);
    }

    public CompletableFuture<Entry> load(long id) {
        return CompletableFuture.supplyAsync(
                () -> db.withConnection(c -> CubatureRuleCatalogue.load(c, id)), workers);
    }

    public CompletableFuture<Entry> save(Entry original, Draft draft) {
        return CompletableFuture.supplyAsync(
                () -> db.withTransaction(c -> CubatureRuleCatalogue.save(c, original, draft)),
                workers);
    }
}
