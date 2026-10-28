package com.orodent.ecos.ton.features.powders.service;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.ton.features.powders.model.PowderModel.*;
import com.orodent.ecos.ton.features.powders.repository.PowderRepository;

import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * One aggregate transaction per command, on borrowed workers. Interrupted commands are never
 * replayed.
 */
public final class PowderService {
    public static final int PAGE_SIZE = 50;
    private final ConnectionProvider connections;
    private final Executor workers;

    public PowderService(ConnectionProvider connections, Executor workers) {
        this.connections = Objects.requireNonNull(connections);
        this.workers = Objects.requireNonNull(workers);
    }

    public CompletableFuture<Page> findPage(String filter, boolean inactive, int offset) {
        String search = filter == null ? "" : filter.strip();
        return CompletableFuture.supplyAsync(
                () ->
                        connections.withConnection(
                                c ->
                                        new PowderRepository(c)
                                                .findPage(
                                                        search,
                                                        inactive,
                                                        Math.max(0, offset),
                                                        PAGE_SIZE)),
                workers);
    }

    public CompletableFuture<Detail> load(long id) {
        return CompletableFuture.supplyAsync(
                () ->
                        connections.withTransaction(
                                c -> {
                                    c.setTransactionIsolation(
                                            java.sql.Connection.TRANSACTION_REPEATABLE_READ);
                                    c.setReadOnly(true);
                                    return new PowderRepository(c).load(id);
                                }),
                workers);
    }

    public CompletableFuture<Detail> create(
            IdentityDraft identity, TechnicalDraft technical, boolean approve) {
        Objects.requireNonNull(identity);
        identity.requireColor();
        Objects.requireNonNull(technical);
        if (approve) technical.requireApprovable();
        return command(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repository = new PowderRepository(c);
                                    long id = repository.insertPowder(identity);
                                    repository.insertRevision(id, 1, technical, approve);
                                    return repository.load(id);
                                }));
    }

    public CompletableFuture<Detail> save(
            Detail expected,
            Revision revision,
            IdentityDraft identity,
            TechnicalDraft technical,
            boolean approve) {
        requireOwned(expected, revision);
        identity.requireColor();
        if (revision.state() != State.BOZZA)
            throw new IllegalArgumentException(
                    "Le schede approvate o ritirate non sono modificabili. Crea una nuova"
                            + " versione.");
        if (approve) technical.requireApprovable();
        return command(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repository = new PowderRepository(c);
                                    repository.claim(expected.powder(), identity);
                                    repository.updateDraft(revision, technical, approve);
                                    return repository.load(expected.powder().id());
                                }));
    }

    public CompletableFuture<Detail> saveIdentity(Detail expected, IdentityDraft identity) {
        identity.requireColor();
        return command(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repository = new PowderRepository(c);
                                    repository.claim(expected.powder(), identity);
                                    return repository.load(expected.powder().id());
                                }));
    }

    public CompletableFuture<Detail> newVersion(Detail expected, Revision source) {
        requireOwned(expected, source);
        if (source.state() == State.BOZZA)
            throw new IllegalArgumentException(
                    "Salva e approva la bozza prima di creare una nuova versione.");
        return command(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repository = new PowderRepository(c);
                                    repository.claim(
                                            expected.powder(), expected.powder().identity());
                                    Detail current = repository.load(expected.powder().id());
                                    if (current.hasDraft())
                                        throw new IllegalArgumentException(
                                                "Esiste già una bozza per questa polvere. Aprila"
                                                        + " dal selettore delle versioni.");
                                    Revision actualSource = current.revision(source.id());
                                    int number =
                                            Math.addExact(
                                                    current.revisions().stream()
                                                            .mapToInt(Revision::number)
                                                            .max()
                                                            .orElse(0),
                                                    1);
                                    repository.insertRevision(
                                            current.powder().id(),
                                            number,
                                            actualSource.technical(),
                                            false);
                                    return repository.load(current.powder().id());
                                }));
    }

    public CompletableFuture<Detail> retire(Detail expected, Revision revision) {
        requireOwned(expected, revision);
        if (revision.state() != State.APPROVATA)
            throw new IllegalArgumentException("Si può ritirare solo una scheda approvata.");
        return command(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repository = new PowderRepository(c);
                                    repository.claim(
                                            expected.powder(), expected.powder().identity());
                                    repository.retire(revision);
                                    return repository.load(expected.powder().id());
                                }));
    }

    private CompletableFuture<Detail> command(Supplier<Detail> work) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return work.get();
                    } catch (RuntimeException failure) {
                        for (Throwable cause = failure; cause != null; cause = cause.getCause())
                            if (cause instanceof SQLException sql
                                    && "23505".equals(sql.getSQLState()))
                                throw new IllegalArgumentException(
                                        "Esiste già una polvere con questo codice. Nessuna modifica"
                                                + " è stata salvata.",
                                        failure);
                        throw failure;
                    }
                },
                workers);
    }

    private static void requireOwned(Detail detail, Revision revision) {
        Objects.requireNonNull(detail);
        Objects.requireNonNull(revision);
        if (revision.powderId() != detail.powder().id())
            throw new IllegalArgumentException(
                    "La versione non appartiene alla polvere selezionata.");
    }
}
