package com.orodent.ecos.ton.features.diskmodels.service;

import static com.orodent.ecos.ton.features.diskmodels.model.DiskModels.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.ton.features.diskmodels.repository.DiskModelRepository;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * One aggregate per transaction. Borrowed workers; failed/interrupted writes are never replayed.
 */
public final class DiskModelService {
    public static final int PAGE_SIZE = 50;
    private final ConnectionProvider connections;
    private final Executor workers;

    public DiskModelService(ConnectionProvider connections, Executor workers) {
        this.connections = Objects.requireNonNull(connections);
        this.workers = Objects.requireNonNull(workers);
    }

    public CompletableFuture<Page<Summary>> page(String search, Filter filter, int offset) {
        return async(
                () ->
                        connections.withConnection(
                                c ->
                                        new DiskModelRepository(c)
                                                .page(
                                                        text(search),
                                                        Objects.requireNonNull(filter),
                                                        Math.max(0, offset),
                                                        PAGE_SIZE)));
    }

    public CompletableFuture<Detail> load(long id) {
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repo = new DiskModelRepository(c);
                                    long first = repo.firstId(repo.code(id));
                                    c.setTransactionIsolation(
                                            Connection.TRANSACTION_REPEATABLE_READ);
                                    c.setReadOnly(true);
                                    repo.readLock(first);
                                    return detail(repo, id);
                                }));
    }

    public CompletableFuture<Page<Version>> versions(String code, int offset) {
        return async(
                () ->
                        connections.withConnection(
                                c ->
                                        new DiskModelRepository(c)
                                                .versions(code, Math.max(0, offset), PAGE_SIZE)));
    }

    public CompletableFuture<Page<Usage>> usages(long id, int offset) {
        return async(
                () ->
                        connections.withConnection(
                                c ->
                                        new DiskModelRepository(c)
                                                .usages(id, Math.max(0, offset), PAGE_SIZE)));
    }

    public CompletableFuture<Detail> create(Draft draft, boolean approve) {
        Objects.requireNonNull(draft);
        if (approve) draft.requireApprovable();
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repo = new DiskModelRepository(c);
                                    if (repo.lockCode(draft.code()))
                                        throw invalid(
                                                "Questo codice modello esiste già. Apri la scheda e"
                                                        + " crea una nuova versione.");
                                    return detail(repo, repo.insert(draft, 1, approve));
                                }));
    }

    public CompletableFuture<Detail> save(Revision expected, Draft draft, boolean approve) {
        if (approve) draft.requireApprovable();
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repo = new DiskModelRepository(c);
                                    Revision current = claim(repo, expected);
                                    if (current.frozen())
                                        throw invalid(
                                                "Una versione approvata o ritirata è di sola"
                                                        + " lettura. Crea una nuova versione.");
                                    if (!draft.code().equals(current.data().code()))
                                        throw invalid(
                                                "Il codice del modello rimane lo stesso nelle sue"
                                                        + " versioni.");
                                    if (approve || !current.data().equals(draft))
                                        repo.update(current, draft, approve);
                                    return detail(repo, current.id());
                                }));
    }

    public CompletableFuture<Detail> copy(Revision expected) {
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repo = new DiskModelRepository(c);
                                    Revision source = claim(repo, expected);
                                    if (!source.frozen())
                                        throw invalid(
                                                "Seleziona una versione approvata o ritirata da"
                                                        + " copiare.");
                                    if (repo.hasDraft(source.data().code()))
                                        throw invalid(
                                                "Esiste già una bozza di questo modello. Aprila dal"
                                                        + " selettore delle versioni.");
                                    int next = repo.maximum(source.data().code()) + 1;
                                    return detail(repo, repo.insert(source.data(), next, false));
                                }));
    }

    public CompletableFuture<Detail> retire(Revision expected) {
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repo = new DiskModelRepository(c);
                                    Revision current = claim(repo, expected);
                                    if (current.state() != State.APPROVATA)
                                        throw invalid("Puoi ritirare solo una versione approvata.");
                                    repo.retire(current);
                                    return detail(repo, current.id());
                                }));
    }

    public CompletableFuture<Void> delete(Revision expected) {
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repo = new DiskModelRepository(c);
                                    Revision current = claim(repo, expected);
                                    if (current.state() != State.BOZZA || repo.used(current.id()))
                                        throw invalid(
                                                "Puoi eliminare solo una bozza mai utilizzata. Le"
                                                        + " versioni approvate rimangono nello"
                                                        + " storico.");
                                    repo.delete(current);
                                    return null;
                                }));
    }

    private static Revision claim(DiskModelRepository repo, Revision expected) throws SQLException {
        Objects.requireNonNull(expected);
        // Resolve ownership from the DB before choosing the family lock, never from caller fields.
        String code = repo.code(expected.id());
        if (!repo.lockCode(code)) throw new Conflict();
        repo.lock(expected.id());
        Revision actual = repo.load(expected.id());
        if (actual.editVersion() != expected.editVersion()
                || !actual.data().code().equals(expected.data().code())
                || actual.number() != expected.number()) throw new Conflict();
        return actual;
    }

    private static Detail detail(DiskModelRepository repo, long id) throws SQLException {
        Revision selected = repo.load(id);
        return new Detail(
                selected,
                repo.versions(selected.data().code(), 0, PAGE_SIZE),
                selected.state() == State.BOZZA && !repo.used(id));
    }

    private <T> CompletableFuture<T> async(Supplier<T> operation) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return operation.get();
                    } catch (RuntimeException failure) {
                        for (Throwable t = failure; t != null; t = t.getCause())
                            if (t instanceof SQLException sql) {
                                if ("23505".equals(sql.getSQLState()))
                                    throw invalid(
                                            "Il codice o la versione esiste già: un altro operatore"
                                                    + " potrebbe averla appena creata. Aggiorna"
                                                    + " l'elenco.");
                                if ("23503".equals(sql.getSQLState()))
                                    throw invalid(
                                            "Esistono collegamenti da conservare. Nessuna modifica"
                                                    + " è stata salvata.");
                            }
                        throw failure;
                    }
                },
                workers);
    }
}
