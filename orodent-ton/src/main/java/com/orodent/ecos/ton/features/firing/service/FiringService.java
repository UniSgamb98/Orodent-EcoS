package com.orodent.ecos.ton.features.firing.service;

import static com.orodent.ecos.ton.features.firing.model.Firing.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.ton.features.firing.repository.FiringRepository;

import java.util.List;
import java.util.concurrent.*;

public final class FiringService {
    private final ConnectionProvider db;
    private final Executor workers;

    public FiringService(ConnectionProvider db, Executor workers) {
        this.db = db;
        this.workers = workers;
    }

    public CompletableFuture<Workspace> workspace() {
        return CompletableFuture.supplyAsync(
                () -> db.withConnection(c -> new FiringRepository(c).workspace()), workers);
    }

    public CompletableFuture<List<Disc>> loaded(long cycleId) {
        return CompletableFuture.supplyAsync(
                () -> db.withConnection(c -> new FiringRepository(c).loaded(cycleId)), workers);
    }

    public CompletableFuture<Load> create(Request request) {
        return CompletableFuture.supplyAsync(
                () -> db.withTransaction(c -> new FiringRepository(c).create(request)), workers);
    }
}
