package com.orodent.ecos.costi.consumption;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.costi.consumption.Consumption.*;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;

public final class ConsumptionService {
    private final ConnectionProvider db;
    private final Executor workers;

    public ConsumptionService(ConnectionProvider db, Executor workers) {
        this.db = db;
        this.workers = workers;
    }

    public CompletableFuture<Analysis> load(Request request) {
        return CompletableFuture.supplyAsync(
                () ->
                        db.withTransaction(
                                c -> {
                                    c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                                    c.setReadOnly(true);
                                    return new ConsumptionEngine()
                                            .analyse(
                                                    new ConsumptionRepository(c)
                                                            .load(
                                                                    request.from(),
                                                                    request.through()),
                                                    request);
                                }),
                workers);
    }

    public CompletableFuture<Planned> plan(Snapshot snapshot, List<PlanLine> lines) {
        var immutable = List.copyOf(lines);
        return CompletableFuture.supplyAsync(
                () -> new ConsumptionEngine().calculatePlan(snapshot, immutable), workers);
    }

    public CompletableFuture<Settings> save(long powder, Settings settings) {
        return CompletableFuture.supplyAsync(
                () -> db.withTransaction(c -> new ConsumptionRepository(c).save(powder, settings)),
                workers);
    }
}
