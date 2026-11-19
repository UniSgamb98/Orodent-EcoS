package com.orodent.ecos.ton.features.production.service;

import static com.orodent.ecos.ton.features.production.model.Production.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.ton.features.production.repository.ProductionRepository;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public final class ProductionService {
    private final ConnectionProvider db;
    private final Executor workers;

    public ProductionService(ConnectionProvider db, Executor workers) {
        this.db = db;
        this.workers = workers;
    }

    public CompletableFuture<Catalogues> catalogues() {
        return async(() -> db.withConnection(c -> new ProductionRepository(c).catalogues()));
    }

    public CompletableFuture<List<BigDecimal>> diameters(long product) {
        return async(() -> db.withConnection(c -> new ProductionRepository(c).diameters(product)));
    }

    public CompletableFuture<Workspace> workspace(
            long product, BigDecimal diameter, Map<Long, Long> pinned) {
        return async(
                () ->
                        db.withTransaction(
                                c ->
                                        new ProductionRepository(c)
                                                .workspace(product, diameter, pinned)));
    }

    public CompletableFuture<List<Summary>> list(String search, State state) {
        return async(() -> db.withConnection(c -> new ProductionRepository(c).list(search, state)));
    }

    public CompletableFuture<Saved> load(long id) {
        return async(() -> db.withConnection(c -> new ProductionRepository(c).load(id)));
    }

    public CompletableFuture<Saved> save(
            Saved expected, Workspace workspace, Request request, boolean prepare) {
        if (workspace.product().id() != request.productId()
                || workspace.diameter().compareTo(request.diameter()) != 0)
            throw invalid("La selezione è cambiata. Aggiorna i dati.");
        if (expected != null && expected.state() != State.BOZZA)
            throw invalid(
                    "Una produzione preparata è di sola lettura. Usa Duplica per un nuovo lavoro.");
        return async(
                () ->
                        db.withTransaction(
                                c -> {
                                    var repo = new ProductionRepository(c);
                                    repo.lockSelection(workspace, request);
                                    var pinned = new HashMap<Long, Long>();
                                    workspace
                                            .articles()
                                            .forEach(
                                                    a -> {
                                                        if (a.specificationId() > 0)
                                                            pinned.put(a.id(), a.specificationId());
                                                    });
                                    var current =
                                            repo.workspace(
                                                    request.productId(),
                                                    request.diameter(),
                                                    pinned);
                                    var plan =
                                            ProductionCalculator.calculate(
                                                    current,
                                                    request,
                                                    repo.template(request.templateId()));
                                    if (prepare && plan.count() == 0)
                                        throw invalid(
                                                "Inserisci almeno una quantità maggiore di zero.");
                                    return repo.save(expected, plan, prepare);
                                }));
    }

    public CompletableFuture<String> storeDocument(long id, String html) {
        return async(() -> db.withTransaction(c -> new ProductionRepository(c).document(id, html)));
    }

    private <T> CompletableFuture<T> async(Supplier<T> action) {
        return CompletableFuture.supplyAsync(action, workers);
    }
}
