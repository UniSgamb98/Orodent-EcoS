package com.orodent.ecos.ton.features.cubing.service;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.database.CubatureRuleCatalogue;
import com.orodent.ecos.database.CubatureRuleCatalogue.*;
import com.orodent.ecos.database.CubatureRuleVersions;
import com.orodent.ecos.database.CubatureRuleVersions.*;

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

    public CompletableFuture<Detail> load(long id, Long version) {
        return CompletableFuture.supplyAsync(
                () -> db.withConnection(c -> CubatureRuleVersions.detail(c, id, version)), workers);
    }

    public CompletableFuture<Detail> save(
            Entry original, Version selected, Draft draft, String formulas, boolean approve) {
        return CompletableFuture.supplyAsync(
                () ->
                        db.withTransaction(
                                c ->
                                        CubatureRuleVersions.save(
                                                c, original, selected, draft, formulas, approve)),
                workers);
    }

    public CompletableFuture<Detail> copy(Entry original, Version source) {
        return CompletableFuture.supplyAsync(
                () -> db.withTransaction(c -> CubatureRuleVersions.copy(c, original, source)),
                workers);
    }
}
