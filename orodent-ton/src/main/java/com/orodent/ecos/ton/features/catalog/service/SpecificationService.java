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
            Article article, int offset, String compositionQuery, String modelQuery) {
        return async(
                () ->
                        connections.withConnection(
                                c ->
                                        new SpecificationRepository(c)
                                                .page(
                                                        (Article)
                                                                new CatalogRepository(c)
                                                                        .load(
                                                                                CatalogKind
                                                                                        .ARTICLES,
                                                                                article.id()),
                                                        Math.max(0, offset),
                                                        CatalogService.PAGE_SIZE,
                                                        compositionQuery,
                                                        modelQuery)));
    }

    public CompletableFuture<Detail> save(
            Article expected, Specification version, SpecificationDraft draft, boolean approve) {
        if (version != null && (version.articleId() != expected.id() || version.frozen()))
            throw invalid("Solo le bozze dell'articolo selezionato sono modificabili.");
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    Article article = lock(c, expected);
                                    var repo = new SpecificationRepository(c);
                                    repo.validateTechnical(article, draft, approve);
                                    Product product =
                                            (Product)
                                                    new CatalogRepository(c)
                                                            .load(
                                                                    CatalogKind.PRODUCTS,
                                                                    article.product().id());
                                    Line line =
                                            (Line)
                                                    new CatalogRepository(c)
                                                            .load(
                                                                    CatalogKind.LINES,
                                                                    product.line().id());
                                    var history = new CatalogHistoryRepository(c);
                                    history.verifyCurrent(article);
                                    history.verifyCurrent(product);
                                    history.verifyCurrent(line);
                                    Specification current =
                                            version == null
                                                    ? repo.load(repo.insert(article, draft))
                                                    : repo.load(version.id());
                                    if (current.articleId() != article.id())
                                        throw invalid(
                                                "La versione non appartiene all’articolo"
                                                        + " selezionato.");
                                    if (version != null && current.revision() != version.revision())
                                        throw new Conflict();
                                    repo.update(current, draft, approve, line, product, article);
                                    if (approve && article.defaultSpecification() == null) {
                                        repo.preferred(article, current.id());
                                        article =
                                                (Article)
                                                        new CatalogRepository(c)
                                                                .load(
                                                                        CatalogKind.ARTICLES,
                                                                        article.id());
                                        history.record(article, "MODIFICA");
                                    }
                                    return CatalogService.detail(
                                            c,
                                            new CatalogRepository(c)
                                                    .load(CatalogKind.ARTICLES, article.id()));
                                }));
    }

    public CompletableFuture<Detail> copy(Article expected, Specification source) {
        if (source.articleId() != expected.id() || !source.frozen())
            throw invalid("Crea una nuova versione a partire da una scheda approvata o ritirata.");
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    Article article = lock(c, expected);
                                    var repo = new SpecificationRepository(c);
                                    Specification current = repo.load(source.id());
                                    if (current.articleId() != article.id())
                                        throw invalid(
                                                "La versione non appartiene all’articolo"
                                                        + " selezionato.");
                                    if (current.revision() != source.revision())
                                        throw new Conflict();
                                    var draft =
                                            new SpecificationDraft(
                                                    current.compositionId(),
                                                    current.modelId(),
                                                    current.calculation(),
                                                    current.notes());
                                    repo.insert(article, draft);
                                    return CatalogService.detail(
                                            c,
                                            new CatalogRepository(c)
                                                    .load(CatalogKind.ARTICLES, article.id()));
                                }));
    }

    public CompletableFuture<Detail> prefer(Article expected, Specification selected) {
        if (selected.articleId() != expected.id()
                || !"APPROVATA".equals(selected.state())
                || !selected.recorded())
            throw invalid("Seleziona una versione approvata con lo storico completo.");
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    Article article = lock(c, expected);
                                    var repo = new SpecificationRepository(c);
                                    Specification current = repo.load(selected.id());
                                    if (current.articleId() != article.id())
                                        throw invalid(
                                                "La versione non appartiene all’articolo"
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
                                            article,
                                            new SpecificationDraft(
                                                    current.compositionId(),
                                                    current.modelId(),
                                                    current.calculation(),
                                                    current.notes()),
                                            true);
                                    if (!Objects.equals(
                                            article.defaultSpecification(), current.id())) {
                                        new CatalogHistoryRepository(c).verifyCurrent(article);
                                        repo.preferred(article, current.id());
                                        article =
                                                (Article)
                                                        new CatalogRepository(c)
                                                                .load(
                                                                        CatalogKind.ARTICLES,
                                                                        article.id());
                                        new CatalogHistoryRepository(c).record(article, "MODIFICA");
                                    }
                                    return CatalogService.detail(
                                            c,
                                            new CatalogRepository(c)
                                                    .load(CatalogKind.ARTICLES, article.id()));
                                }));
    }

    public CompletableFuture<Detail> retire(Article expected, Specification selected) {
        if (selected.articleId() != expected.id() || !"APPROVATA".equals(selected.state()))
            throw invalid("Puoi ritirare solo una versione approvata.");
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    Article article = lock(c, expected, false);
                                    var repo = new SpecificationRepository(c);
                                    Specification current = repo.load(selected.id());
                                    if (current.articleId() != article.id())
                                        throw invalid(
                                                "La versione non appartiene all’articolo"
                                                        + " selezionato.");
                                    if (current.revision() != selected.revision())
                                        throw new Conflict();
                                    repo.retire(current);
                                    if (Objects.equals(
                                            article.defaultSpecification(), selected.id())) {
                                        new CatalogHistoryRepository(c).verifyCurrent(article);
                                        repo.preferred(article, null);
                                        article =
                                                (Article)
                                                        new CatalogRepository(c)
                                                                .load(
                                                                        CatalogKind.ARTICLES,
                                                                        article.id());
                                        new CatalogHistoryRepository(c).record(article, "MODIFICA");
                                    }
                                    return CatalogService.detail(
                                            c,
                                            new CatalogRepository(c)
                                                    .load(CatalogKind.ARTICLES, article.id()));
                                }));
    }

    private static Article lock(Connection c, Article expected) throws SQLException {
        return lock(c, expected, true);
    }

    private static Article lock(Connection c, Article expected, boolean requireAvailable)
            throws SQLException {
        var repo = new CatalogRepository(c);
        CatalogService.lockParents(repo, expected, draft(expected));
        repo.lock(CatalogKind.ARTICLES, expected.id());
        Article current = (Article) repo.load(CatalogKind.ARTICLES, expected.id());
        CatalogService.verifyExpected(current, expected);
        if (requireAvailable && !current.available())
            throw invalid(
                    "Completa e riattiva articolo, prodotto e linea prima di configurare una nuova"
                            + " produzione.");
        return current;
    }

    private <T> CompletableFuture<T> async(Supplier<T> action) {
        return CompletableFuture.supplyAsync(action, workers);
    }
}
