package com.orodent.ecos.ton.features.documents.service;

import static com.orodent.ecos.ton.features.documents.model.DocumentTemplates.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.ton.features.documents.repository.DocumentTemplateRepository;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Borrows EcoS workers/connections. Every mutation is one transaction and is attempted once. */
public final class DocumentTemplateService {
    public static final int PAGE_SIZE = 50;
    private final ConnectionProvider db;
    private final Executor workers;

    public DocumentTemplateService(ConnectionProvider db, Executor workers) {
        this.db = Objects.requireNonNull(db);
        this.workers = Objects.requireNonNull(workers);
    }

    public CompletableFuture<Page> page(String search, int offset) {
        return async(
                () ->
                        db.withConnection(
                                c ->
                                        new DocumentTemplateRepository(c)
                                                .page(
                                                        search.strip(),
                                                        Math.max(0, offset),
                                                        PAGE_SIZE)));
    }

    public CompletableFuture<Detail> load(long id) {
        return async(() -> db.withConnection(c -> new DocumentTemplateRepository(c).load(id)));
    }

    public CompletableFuture<List<Template>> versions(String code) {
        return async(
                () -> db.withConnection(c -> new DocumentTemplateRepository(c).versions(code)));
    }

    public CompletableFuture<Detail> create(Draft draft, boolean approve) {
        if (approve) draft.requireApprovable();
        return async(
                () ->
                        db.withTransaction(
                                c -> {
                                    var repo = new DocumentTemplateRepository(c);
                                    Detail inserted = repo.insert(draft, 1);
                                    return approve
                                            ? repo.save(inserted.template(), draft, true)
                                            : inserted;
                                }));
    }

    public CompletableFuture<Detail> save(Template expected, Draft draft, boolean approve) {
        if (expected.state() != State.BOZZA)
            throw new IllegalArgumentException(
                    "Una versione approvata è di sola lettura. Crea una nuova versione.");
        if (!expected.code().equals(draft.code()))
            throw new IllegalArgumentException("Il codice di un modello salvato non può cambiare.");
        if (approve) draft.requireApprovable();
        return async(
                () ->
                        db.withTransaction(
                                c ->
                                        new DocumentTemplateRepository(c)
                                                .save(expected, draft, approve)));
    }

    public CompletableFuture<Detail> newVersion(Template source) {
        if (source.state() == State.BOZZA)
            throw new IllegalArgumentException(
                    "Approva la bozza prima di creare una nuova versione.");
        return async(
                () ->
                        db.withTransaction(
                                c -> {
                                    var repo = new DocumentTemplateRepository(c);
                                    repo.lockFamily(source.code());
                                    Detail current = repo.load(source.id());
                                    if (current.template().token() != source.token()
                                            || !current.template().code().equals(source.code())
                                            || current.template().state() == State.BOZZA)
                                        throw new IllegalStateException(
                                                "La versione di partenza è cambiata. Ricarica il"
                                                    + " modello.");
                                    List<Template> versions = repo.versions(source.code());
                                    if (versions.stream().anyMatch(v -> v.state() == State.BOZZA))
                                        throw new IllegalArgumentException(
                                                "Esiste già una bozza: aprila dal selettore delle"
                                                    + " versioni.");
                                    return repo.insert(
                                            new Draft(source.code(), current.content()),
                                            Math.addExact(versions.getFirst().number(), 1));
                                }));
    }

    public CompletableFuture<Void> delete(Template expected) {
        if (expected.state() != State.BOZZA)
            throw new IllegalArgumentException("Puoi eliminare soltanto una bozza.");
        return async(
                () ->
                        db.withTransaction(
                                c -> {
                                    new DocumentTemplateRepository(c).deleteDraft(expected);
                                    return null;
                                }));
    }

    private <T> CompletableFuture<T> async(Supplier<T> work) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return work.get();
                    } catch (RuntimeException failure) {
                        for (Throwable cause = failure; cause != null; cause = cause.getCause())
                            if (cause instanceof SQLException sql
                                    && "23505".equals(sql.getSQLState()))
                                throw new IllegalArgumentException(
                                        "Esiste già un modello con questo codice o questa versione."
                                            + " Ricarica l’elenco.",
                                        failure);
                        throw failure;
                    }
                },
                workers);
    }
}
