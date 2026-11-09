package com.orodent.ecos.costi.service;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.costi.model.PowderPrices;
import com.orodent.ecos.costi.model.PowderPrices.*;
import com.orodent.ecos.costi.repository.PowderPriceRepository;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Database work always runs off the FX thread. Failed writes are never retried automatically. */
public final class PowderPriceService {
    private final ConnectionProvider connections;
    private final Executor workers;

    public PowderPriceService(ConnectionProvider connections, Executor workers) {
        this.connections = Objects.requireNonNull(connections);
        this.workers = Objects.requireNonNull(workers);
    }

    public CompletableFuture<Page> page(
            String search, Filter filter, boolean inactive, int offset) {
        if (offset < 0) throw new IllegalArgumentException("Pagina non valida.");
        String value = search == null ? "" : search.strip();
        if (value.length() > 150)
            throw new IllegalArgumentException("Ricerca: massimo 150 caratteri.");
        return CompletableFuture.supplyAsync(
                () ->
                        connections.withConnection(
                                c ->
                                        new PowderPriceRepository(c)
                                                .page(
                                                        value,
                                                        filter,
                                                        inactive,
                                                        offset,
                                                        PowderPrices.PAGE_SIZE)),
                workers);
    }

    public CompletableFuture<Detail> load(long powderId) {
        return CompletableFuture.supplyAsync(
                () -> connections.withConnection(c -> new PowderPriceRepository(c).load(powderId)),
                workers);
    }

    public CompletableFuture<Detail> save(long powderId, int version, Draft draft) {
        Objects.requireNonNull(draft);
        return CompletableFuture.supplyAsync(
                () ->
                        connections.withTransaction(
                                c -> new PowderPriceRepository(c).save(powderId, version, draft)),
                workers);
    }
}
