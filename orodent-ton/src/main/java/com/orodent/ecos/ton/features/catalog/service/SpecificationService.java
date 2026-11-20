package com.orodent.ecos.ton.features.catalog.service;

import static com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;
import com.orodent.ecos.ton.features.catalog.repository.*;

import java.sql.*;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Approval freezes catalogue snapshots and exact composition/model versions, without calculating
 * doses.
 */
public final class SpecificationService {
    private final ConnectionProvider connections;
    private final Executor workers;

    public SpecificationService(ConnectionProvider connections, Executor workers) {
        this.connections = Objects.requireNonNull(connections);
        this.workers = Objects.requireNonNull(workers);
    }

    public CompletableFuture<SpecificationPage> page(
            Product product, int offset, String compositionQuery, String modelQuery) {
        return async(
                () ->
                        connections.withConnection(
                                c ->
                                        new SpecificationRepository(c)
                                                .page(
                                                        (Product)
                                                                new CatalogRepository(c)
                                                                        .load(
                                                                                CatalogKind
                                                                                        .PRODUCTS,
                                                                                product.id()),
                                                        Math.max(0, offset),
                                                        CatalogService.PAGE_SIZE,
                                                        compositionQuery,
                                                        modelQuery)));
    }

    public CompletableFuture<Detail> save(
            Product expected, Specification version, SpecificationDraft draft, boolean approve) {
        if (version != null && (version.productId() != expected.id() || version.frozen()))
            throw invalid("Solo le bozze del prodotto selezionato sono modificabili.");
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    Product product = lock(c, expected);
                                    var repo = new SpecificationRepository(c);
                                    repo.validateTechnical(product, draft, approve);
                                    Line line =
                                            (Line)
                                                    new CatalogRepository(c)
                                                            .load(
                                                                    CatalogKind.LINES,
                                                                    product.line().id());
                                    var history = new CatalogHistoryRepository(c);
                                    history.verifyCurrent(product);
                                    history.verifyCurrent(line);
                                    Specification current =
                                            version == null
                                                    ? repo.load(repo.insert(product, draft))
                                                    : repo.load(version.id());
                                    if (current.productId() != product.id())
                                        throw invalid(
                                                "La versione non appartiene al prodotto"
                                                        + " selezionato.");
                                    if (version != null && current.revision() != version.revision())
                                        throw new Conflict();
                                    repo.update(current, draft, approve, line, product);
                                    if (approve
                                            && product.defaultSpecification() == null
                                            && !product.specificationConflict()) {
                                        repo.preferred(product, current.id());
                                        product =
                                                (Product)
                                                        new CatalogRepository(c)
                                                                .load(
                                                                        CatalogKind.PRODUCTS,
                                                                        product.id());
                                        history.record(product, "MODIFICA");
                                    }
                                    return CatalogService.detail(
                                            c,
                                            new CatalogRepository(c)
                                                    .load(CatalogKind.PRODUCTS, product.id()));
                                }));
    }

    public CompletableFuture<Detail> copy(Product expected, Specification source) {
        if (source.productId() != expected.id() || !source.frozen())
            throw invalid("Crea una nuova versione a partire da una scheda approvata o ritirata.");
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    Product product = lock(c, expected);
                                    var repo = new SpecificationRepository(c);
                                    Specification current = repo.load(source.id());
                                    if (current.productId() != product.id())
                                        throw invalid(
                                                "La versione non appartiene al prodotto"
                                                        + " selezionato.");
                                    if (current.revision() != source.revision())
                                        throw new Conflict();
                                    var draft =
                                            new SpecificationDraft(
                                                    current.compositionId(),
                                                    current.modelId(),
                                                    current.calculation(),
                                                    current.notes());
                                    repo.insert(product, draft);
                                    return CatalogService.detail(
                                            c,
                                            new CatalogRepository(c)
                                                    .load(CatalogKind.PRODUCTS, product.id()));
                                }));
    }

    public CompletableFuture<Detail> prefer(Product expected, Specification selected) {
        if (selected.productId() != expected.id()
                || !"APPROVATA".equals(selected.state())
                || !selected.recorded())
            throw invalid("Seleziona una versione approvata con lo storico completo.");
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    Product product = lock(c, expected);
                                    var repo = new SpecificationRepository(c);
                                    Specification current = repo.load(selected.id());
                                    if (current.productId() != product.id())
                                        throw invalid(
                                                "La versione non appartiene al prodotto"
                                                        + " selezionato.");
                                    if (current.revision() != selected.revision()
                                            || !"APPROVATA".equals(current.state()))
                                        throw new Conflict();
                                    if (!current.recorded())
                                        throw invalid(
                                                "La versione precedente non ha lo storico"
                                                        + " anagrafico completo. Crea e approva una"
                                                        + " nuova versione.");
                                    repo.validateTechnical(
                                            product,
                                            new SpecificationDraft(
                                                    current.compositionId(),
                                                    current.modelId(),
                                                    current.calculation(),
                                                    current.notes()),
                                            true);
                                    if (!Objects.equals(
                                            product.defaultSpecification(), current.id())) {
                                        new CatalogHistoryRepository(c).verifyCurrent(product);
                                        repo.preferred(product, current.id());
                                        product =
                                                (Product)
                                                        new CatalogRepository(c)
                                                                .load(
                                                                        CatalogKind.PRODUCTS,
                                                                        product.id());
                                        new CatalogHistoryRepository(c).record(product, "MODIFICA");
                                    }
                                    return CatalogService.detail(
                                            c,
                                            new CatalogRepository(c)
                                                    .load(CatalogKind.PRODUCTS, product.id()));
                                }));
    }

    public CompletableFuture<Detail> retire(Product expected, Specification selected) {
        if (selected.productId() != expected.id() || !"APPROVATA".equals(selected.state()))
            throw invalid("Puoi ritirare solo una versione approvata.");
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    Product product = lock(c, expected, false);
                                    var repo = new SpecificationRepository(c);
                                    Specification current = repo.load(selected.id());
                                    if (current.productId() != product.id())
                                        throw invalid(
                                                "La versione non appartiene al prodotto"
                                                        + " selezionato.");
                                    if (current.revision() != selected.revision())
                                        throw new Conflict();
                                    repo.retire(current);
                                    if (Objects.equals(
                                            product.defaultSpecification(), selected.id())) {
                                        new CatalogHistoryRepository(c).verifyCurrent(product);
                                        repo.preferred(product, null);
                                        product =
                                                (Product)
                                                        new CatalogRepository(c)
                                                                .load(
                                                                        CatalogKind.PRODUCTS,
                                                                        product.id());
                                        new CatalogHistoryRepository(c).record(product, "MODIFICA");
                                    }
                                    return CatalogService.detail(
                                            c,
                                            new CatalogRepository(c)
                                                    .load(CatalogKind.PRODUCTS, product.id()));
                                }));
    }

    private static Product lock(Connection c, Product expected) throws SQLException {
        return lock(c, expected, true);
    }

    private static Product lock(Connection c, Product expected, boolean requireAvailable)
            throws SQLException {
        var repo = new CatalogRepository(c);
        CatalogService.lockParents(repo, expected, draft(expected));
        repo.lock(CatalogKind.PRODUCTS, expected.id());
        Product current = (Product) repo.load(CatalogKind.PRODUCTS, expected.id());
        CatalogService.verifyExpected(current, expected);
        if (requireAvailable && !current.available())
            throw invalid(
                    "Completa e riattiva prodotto e linea prima di configurare una nuova"
                            + " produzione.");
        return current;
    }

    private <T> CompletableFuture<T> async(Supplier<T> action) {
        return CompletableFuture.supplyAsync(action, workers);
    }
}
