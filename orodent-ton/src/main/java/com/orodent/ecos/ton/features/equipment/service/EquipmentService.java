package com.orodent.ecos.ton.features.equipment.service;

import static com.orodent.ecos.ton.features.equipment.model.Equipment.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.ton.features.equipment.repository.EquipmentRepository;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class EquipmentService {
    private final ConnectionProvider db;
    private final Executor workers;

    public EquipmentService(ConnectionProvider db, Executor workers) {
        this.db = Objects.requireNonNull(db);
        this.workers = Objects.requireNonNull(workers);
    }

    public CompletableFuture<List<Entry>> list(Kind kind, String query, boolean archived) {
        return async(
                () ->
                        db.withConnection(
                                c -> new EquipmentRepository(c).list(kind, query, archived)));
    }

    public CompletableFuture<Entry> load(Kind kind, long id) {
        return async(() -> db.withConnection(c -> new EquipmentRepository(c).load(kind, id)));
    }

    public CompletableFuture<Entry> save(Kind kind, Entry expected, Draft draft) {
        draft.validate(kind);
        return async(
                () ->
                        db.withTransaction(
                                c -> {
                                    var repo = new EquipmentRepository(c);
                                    if (expected == null) return repo.create(kind, draft);
                                    var current = repo.load(kind, expected.id());
                                    if (current.revision() != expected.revision())
                                        throw new Conflict();
                                    if (current.draft().equals(draft)) return current;
                                    return repo.save(kind, expected, draft);
                                }));
    }

    private <T> CompletableFuture<T> async(Supplier<T> action) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return action.get();
                    } catch (RuntimeException error) {
                        for (Throwable cause = error; cause != null; cause = cause.getCause())
                            if (cause instanceof SQLException sql
                                    && "23505".equals(sql.getSQLState()))
                                throw new IllegalArgumentException(
                                        "Questo codice è già utilizzato. Scegli un codice"
                                                + " diverso.");
                        throw error;
                    }
                },
                workers);
    }
}
