package com.orodent.ecos.ton.features.firingprograms.service;

import static com.orodent.ecos.ton.features.firingprograms.model.FiringPrograms.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.ton.features.firingprograms.repository.FiringProgramRepository;

import java.util.List;
import java.util.concurrent.*;

public final class FiringProgramService {
    private final ConnectionProvider db;
    private final Executor workers;

    public FiringProgramService(ConnectionProvider db, Executor workers) {
        this.db = db;
        this.workers = workers;
    }

    public CompletableFuture<List<Entry>> list(String query, boolean archived) {
        return CompletableFuture.supplyAsync(
                () -> db.withConnection(c -> new FiringProgramRepository(c).list(query, archived)),
                workers);
    }

    public CompletableFuture<Detail> load(long id, Long version) {
        return CompletableFuture.supplyAsync(
                () -> db.withConnection(c -> new FiringProgramRepository(c).load(id, version)),
                workers);
    }

    public CompletableFuture<Detail> save(
            Entry entry, Snapshot selected, Draft draft, boolean approve) {
        return CompletableFuture.supplyAsync(
                () ->
                        db.withTransaction(
                                c ->
                                        new FiringProgramRepository(c)
                                                .save(entry, selected, draft, approve)),
                workers);
    }

    public CompletableFuture<Detail> copy(Entry entry, Snapshot source) {
        return CompletableFuture.supplyAsync(
                () -> db.withTransaction(c -> new FiringProgramRepository(c).copy(entry, source)),
                workers);
    }
}
