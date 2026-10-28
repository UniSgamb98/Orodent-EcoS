package com.orodent.ecos.ton.features.compositions.service;

import static com.orodent.ecos.ton.features.compositions.model.Compositions.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.Choice;
import com.orodent.ecos.ton.features.catalog.repository.CatalogRepository;
import com.orodent.ecos.ton.features.compositions.repository.CompositionRepository;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Borrowed workers and connections; no automatic replay of writes after a failure. */
public final class CompositionService {
    public static final int PAGE_SIZE = 50;
    private final ConnectionProvider connections;
    private final Executor workers;

    public CompositionService(ConnectionProvider connections, Executor workers) {
        this.connections = Objects.requireNonNull(connections);
        this.workers = Objects.requireNonNull(workers);
    }

    public CompletableFuture<Page<Summary>> page(
            String query, Filter filter, boolean archived, int offset) {
        return async(
                () ->
                        connections.withConnection(
                                c ->
                                        new CompositionRepository(c)
                                                .page(
                                                        query,
                                                        filter,
                                                        archived,
                                                        Math.max(0, offset),
                                                        PAGE_SIZE)));
    }

    public CompletableFuture<List<Choice>> products(String query, Long selected) {
        return async(
                () ->
                        connections.withConnection(
                                c ->
                                        new CatalogRepository(c)
                                                .choices(CatalogKind.PRODUCTS, query, selected)));
    }

    public CompletableFuture<List<PowderRef>> powders(String query) {
        return powderPage(query, 0).thenApply(Page::rows);
    }

    public CompletableFuture<Page<PowderRef>> powderPage(String query, int offset) {
        return async(
                () ->
                        connections.withConnection(
                                c ->
                                        new CompositionRepository(c)
                                                .choices(query, Math.max(0, offset), PAGE_SIZE)));
    }

    public CompletableFuture<Detail> load(long id) {
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repo = new CompositionRepository(c);
                                    long product = repo.parent(id);
                                    var catalog = new CatalogRepository(c);
                                    Long line = catalog.productLine(product);
                                    c.setTransactionIsolation(
                                            Connection.TRANSACTION_REPEATABLE_READ);
                                    c.setReadOnly(true);
                                    if (line != null) repo.readLine(line);
                                    repo.readProduct(product);
                                    if (!Objects.equals(line, catalog.productLine(product)))
                                        throw new Conflict();
                                    return detail(repo, id);
                                }));
    }

    public CompletableFuture<Page<Version>> versions(long product, int offset) {
        return async(
                () ->
                        connections.withConnection(
                                c ->
                                        new CompositionRepository(c)
                                                .versions(
                                                        product, Math.max(0, offset), PAGE_SIZE)));
    }

    public CompletableFuture<Page<Usage>> usages(long id, int offset) {
        return async(
                () ->
                        connections.withConnection(
                                c ->
                                        new CompositionRepository(c)
                                                .usages(id, Math.max(0, offset), PAGE_SIZE)));
    }

    public CompletableFuture<Page<ModelMatch>> models(
            List<Integer> numbers, String query, boolean matching, int offset) {
        List<Integer> requested = List.copyOf(numbers);
        return async(
                () ->
                        connections.withConnection(
                                c ->
                                        new CompositionRepository(c)
                                                .models(
                                                        requested,
                                                        query,
                                                        matching,
                                                        Math.max(0, offset),
                                                        PAGE_SIZE)));
    }

    public CompletableFuture<Detail> create(Draft draft, boolean approve) {
        if (approve) draft.requireApprovable();
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repo = new CompositionRepository(c);
                                    lockProduct(c, repo, draft.productId());
                                    requireAvailable(repo.product(draft.productId()));
                                    if (repo.maximum(draft.productId()) > 0)
                                        throw invalid(
                                                "Questo prodotto ha già una composizione. Apri la"
                                                        + " scheda e crea una nuova versione.");
                                    validatePowders(repo, draft, approve);
                                    return detail(repo, repo.insert(draft, 1, approve));
                                }));
    }

    public CompletableFuture<Detail> save(Revision expected, Draft draft, boolean approve) {
        if (approve) draft.requireApprovable();
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repo = new CompositionRepository(c);
                                    Revision current = claim(c, repo, expected);
                                    if (current.frozen())
                                        throw invalid(
                                                "La composizione approvata o ritirata è di sola"
                                                        + " lettura. Crea una nuova versione.");
                                    if (current.data().productId() != draft.productId())
                                        throw invalid(
                                                "Il prodotto della composizione rimane fisso nelle"
                                                        + " sue versioni.");
                                    if (approve) requireAvailable(repo.product(draft.productId()));
                                    validatePowders(repo, draft, approve);
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
                                    var repo = new CompositionRepository(c);
                                    Revision source = claim(c, repo, expected);
                                    if (!source.frozen())
                                        throw invalid(
                                                "Seleziona una versione approvata o ritirata da"
                                                        + " copiare.");
                                    requireAvailable(repo.product(source.data().productId()));
                                    if (repo.hasDraft(source.data().productId()))
                                        throw invalid(
                                                "Esiste già una bozza per questo prodotto. Ricarica"
                                                        + " la scheda per aprirla.");
                                    int next =
                                            Math.addExact(
                                                    repo.maximum(source.data().productId()), 1);
                                    return detail(repo, repo.insert(source.data(), next, false));
                                }));
    }

    public CompletableFuture<Detail> retire(Revision expected) {
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repo = new CompositionRepository(c);
                                    Revision current = claim(c, repo, expected);
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
                                    var repo = new CompositionRepository(c);
                                    Revision current = claim(c, repo, expected);
                                    if (current.state() != State.BOZZA || repo.used(current.id()))
                                        throw invalid(
                                                "Puoi eliminare solo una bozza mai utilizzata. Le"
                                                    + " versioni approvate restano nello storico.");
                                    repo.delete(current);
                                    return null;
                                }));
    }

    private static void requireAvailable(ProductRef product) {
        if (!product.available())
            throw invalid(
                    "Completa e riattiva prodotto e linea prima di approvare o creare una"
                            + " composizione.");
    }

    private static void lockProduct(Connection c, CompositionRepository repo, long product)
            throws SQLException {
        var catalog = new CatalogRepository(c);
        Long line = catalog.productLine(product);
        if (line != null) catalog.lock(CatalogKind.LINES, line);
        catalog.lock(CatalogKind.PRODUCTS, product);
        if (!Objects.equals(line, catalog.productLine(product))) throw new Conflict();
    }

    private static Revision claim(Connection c, CompositionRepository repo, Revision expected)
            throws SQLException {
        long product = repo.parent(expected.id());
        lockProduct(c, repo, product);
        repo.lock(expected.id());
        Revision current = repo.load(expected.id());
        if (current.editVersion() != expected.editVersion()
                || current.number() != expected.number()
                || current.data().productId() != expected.data().productId()) throw new Conflict();
        return current;
    }

    public static Set<Long> references(Draft draft) {
        var result = new TreeSet<Long>();
        for (Layer l : draft.layers())
            for (Ingredient i : l.ingredients()) result.add(i.powderRevisionId());
        return result;
    }

    private static void validatePowders(CompositionRepository repo, Draft draft, boolean approving)
            throws SQLException {
        Set<Long> ids = references(draft);
        repo.lockPowders(ids);
        Map<Long, PowderRef> refs = repo.powders(ids);
        for (Layer layer : draft.layers()) {
            var families = new HashSet<Long>();
            for (Ingredient i : layer.ingredients()) {
                PowderRef p = refs.get(i.powderRevisionId());
                if (p == null)
                    throw invalid("Una revisione di polvere non esiste più. Ricarica la scheda.");
                if (!families.add(p.powderId()))
                    throw invalid(
                            "Strato "
                                    + layer.number()
                                    + ": una polvere può comparire una sola volta, anche se le"
                                    + " revisioni sono diverse.");
                if (approving && !p.usable())
                    throw invalid(
                            "Strato "
                                    + layer.number()
                                    + ": "
                                    + p.label()
                                    + " non è una revisione approvata di una polvere attiva.");
            }
        }
    }

    private static Detail detail(CompositionRepository repo, long id) throws SQLException {
        Revision r = repo.load(id);
        return new Detail(
                r,
                repo.product(r.data().productId()),
                repo.powders(references(r.data())),
                repo.versions(r.data().productId(), 0, PAGE_SIZE),
                r.state() == State.BOZZA && !repo.used(id));
    }

    private <T> CompletableFuture<T> async(Supplier<T> action) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return action.get();
                    } catch (RuntimeException failure) {
                        for (Throwable t = failure; t != null; t = t.getCause())
                            if (t instanceof SQLException sql) {
                                if ("23505".equals(sql.getSQLState()))
                                    throw invalid(
                                            "La versione o la combinazione esiste già. Aggiorna la"
                                                    + " scheda: nessuna modifica è stata salvata.");
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
