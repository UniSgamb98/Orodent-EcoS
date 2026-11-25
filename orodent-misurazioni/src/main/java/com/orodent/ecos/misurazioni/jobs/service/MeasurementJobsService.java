package com.orodent.ecos.misurazioni.jobs.service;

import static com.orodent.ecos.misurazioni.jobs.model.MeasurementJobs.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.misurazioni.jobs.repository.MeasurementJobsRepository;
import com.orodent.ecos.misurazioni.model.MeasurementBatch.Measurement;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public final class MeasurementJobsService {
    private final ConnectionProvider db;
    private final Executor workers;
    private final Clock clock;

    public MeasurementJobsService(ConnectionProvider db, Executor workers) {
        this(db, workers, Clock.systemDefaultZone());
    }

    public MeasurementJobsService(ConnectionProvider db, Executor workers, Clock clock) {
        this.db = db;
        this.workers = workers;
        this.clock = clock;
    }

    public CompletableFuture<List<Job>> list() {
        return CompletableFuture.supplyAsync(
                () ->
                        db.withConnection(
                                c -> new MeasurementJobsRepository(c).list(LocalDate.now(clock))),
                workers);
    }

    public CompletableFuture<Detail> load(Key key) {
        return CompletableFuture.supplyAsync(
                () ->
                        db.withConnection(
                                c ->
                                        new MeasurementJobsRepository(c)
                                                .load(key, LocalDate.now(clock))),
                workers);
    }

    public CompletableFuture<Detail> save(
            Detail baseline, List<Measurement> values, Operator operator) {
        var snapshot = List.copyOf(values);
        return CompletableFuture.supplyAsync(
                () ->
                        db.withTransaction(
                                c ->
                                        new MeasurementJobsRepository(c)
                                                .save(
                                                        baseline,
                                                        snapshot,
                                                        operator,
                                                        LocalDate.now(clock))),
                workers);
    }
}
