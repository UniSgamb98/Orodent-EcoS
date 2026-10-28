package com.orodent.ecos.ton.features.catalog.service;

import static com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;
import com.orodent.ecos.ton.features.catalog.repository.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** One transaction per catalogue command. Workers and connections are borrowed from EcoS. */
public final class CatalogService {
    public static final int PAGE_SIZE = 50;
    private final ConnectionProvider connections;
    private final Executor workers;

    public CatalogService(ConnectionProvider connections, Executor workers) {
        this.connections = Objects.requireNonNull(connections);
        this.workers = Objects.requireNonNull(workers);
    }

    public CompletableFuture<Page> page(CatalogKind kind, Filter filter, int offset) {
        return read(
                () ->
                        connections.withConnection(
                                c ->
                                        new CatalogRepository(c)
                                                .page(
                                                        kind,
                                                        filter,
                                                        Math.max(0, offset),
                                                        PAGE_SIZE)));
    }

    public CompletableFuture<Detail> load(CatalogKind kind, long id) {
        return read(
                () ->
                        connections.withTransaction(
                                c -> detail(c, new CatalogRepository(c).load(kind, id))));
    }

    public CompletableFuture<HistoryPage> history(CatalogKind kind, long id, int offset) {
        return read(
                () ->
                        connections.withConnection(
                                c ->
                                        new CatalogHistoryRepository(c)
                                                .page(kind, id, Math.max(0, offset), PAGE_SIZE)));
    }

    public CompletableFuture<List<Choice>> choices(CatalogKind kind, String query, Long selected) {
        return read(
                () ->
                        connections.withConnection(
                                c -> new CatalogRepository(c).choices(kind, query, selected)));
    }

    public CompletableFuture<Detail> create(Draft draft) {
        requireComplete(draft);
        return command(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repo = new CatalogRepository(c);
                                    lockParents(repo, null, draft);
                                    validateParents(repo, null, draft);
                                    verifyCode(repo, draft.kind(), 0, draft.code());
                                    long id = repo.insert(draft);
                                    Entry result = repo.load(draft.kind(), id);
                                    new CatalogHistoryRepository(c).record(result, "CREAZIONE");
                                    return detail(c, result);
                                }));
    }

    public CompletableFuture<Detail> save(Entry expected, Draft draft) {
        if (expected.kind() != draft.kind()) throw invalid("Tipo di anagrafica non coerente.");
        requireComplete(draft);
        return command(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repo = new CatalogRepository(c);
                                    lockParents(repo, expected, draft);
                                    repo.lock(expected.kind(), expected.id());
                                    Entry current = repo.load(expected.kind(), expected.id());
                                    verifyExpected(current, expected);
                                    new CatalogHistoryRepository(c).verifyCurrent(current);
                                    validateParents(repo, current, draft);
                                    validateStructure(current, draft);
                                    verifyCode(repo, draft.kind(), expected.id(), draft.code());
                                    if (draft(current).equals(draft)) return detail(c, current);
                                    repo.update(current, draft);
                                    Entry result = repo.load(current.kind(), current.id());
                                    new CatalogHistoryRepository(c).record(result, "MODIFICA");
                                    return detail(c, result);
                                }));
    }

    public CompletableFuture<Detail> setActive(Entry expected, boolean active) {
        return command(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repo = new CatalogRepository(c);
                                    Draft draft = draft(expected);
                                    lockParents(repo, expected, draft);
                                    repo.lock(expected.kind(), expected.id());
                                    Entry current = repo.load(expected.kind(), expected.id());
                                    verifyExpected(current, expected);
                                    var history = new CatalogHistoryRepository(c);
                                    history.verifyCurrent(current);
                                    if (current.active() == active) return detail(c, current);
                                    repo.active(current, active);
                                    Entry result = repo.load(current.kind(), current.id());
                                    history.record(
                                            result, active ? "RIATTIVAZIONE" : "ARCHIVIAZIONE");
                                    return detail(c, result);
                                }));
    }

    public CompletableFuture<Void> delete(Entry expected) {
        return command(
                () ->
                        connections.withTransaction(
                                c -> {
                                    var repo = new CatalogRepository(c);
                                    lockParents(repo, expected, draft(expected));
                                    repo.lock(expected.kind(), expected.id());
                                    Entry current = repo.load(expected.kind(), expected.id());
                                    verifyExpected(current, expected);
                                    new CatalogHistoryRepository(c).verifyCurrent(current);
                                    if (!deletable(c, current))
                                        throw invalid(
                                                "L'anagrafica è utilizzata o ha uno storico da"
                                                    + " conservare. Puoi archiviarla.");
                                    repo.delete(current);
                                    return null;
                                }));
    }

    static Detail detail(Connection c, Entry entry) throws SQLException {
        var previous =
                entry instanceof Product p && !p.complete()
                        ? new CatalogRepository(c).previousLines(p.id())
                        : List.<Choice>of();
        return new Detail(entry, deletable(c, entry), previous);
    }

    static boolean deletable(Connection c, Entry entry) throws SQLException {
        return !entry.used()
                && entry.revision() == 1
                && new CatalogRepository(c)
                                .count(
                                        "SELECT COUNT(*) FROM "
                                                + entry.kind().historyTable()
                                                + " WHERE "
                                                + entry.kind().key()
                                                + "=?",
                                        List.of(entry.id()))
                        == 1;
    }

    static void verifyExpected(Entry current, Entry expected) {
        if (current.revision() != expected.revision()) throw new Conflict();
    }

    /** Global order: sorted lines, sorted products, then the article itself. */
    static void lockParents(CatalogRepository repo, Entry expected, Draft draft)
            throws SQLException {
        var lineIds = new TreeSet<Long>();
        var productIds = new TreeSet<Long>();
        var observedLines = new HashMap<Long, Long>();
        if (expected instanceof Product p && p.line() != null) lineIds.add(p.line().id());
        if (draft instanceof ProductDraft p && p.lineId() != null) lineIds.add(p.lineId());
        if (expected instanceof Article a) productIds.add(a.product().id());
        if (draft instanceof ArticleDraft a) productIds.add(a.productId());
        for (long id : productIds) {
            Product p = (Product) repo.load(CatalogKind.PRODUCTS, id);
            observedLines.put(id, p.line() == null ? null : p.line().id());
            if (p.line() != null) lineIds.add(p.line().id());
        }
        for (long id : lineIds) repo.lock(CatalogKind.LINES, id);
        for (long id : productIds) repo.lock(CatalogKind.PRODUCTS, id);
        for (long id : productIds) {
            Product p = (Product) repo.load(CatalogKind.PRODUCTS, id);
            if (!Objects.equals(observedLines.get(id), p.line() == null ? null : p.line().id()))
                throw new Conflict();
        }
    }

    private static void requireComplete(Draft draft) {
        Objects.requireNonNull(draft);
        if (draft instanceof ProductDraft p) p.requireComplete();
    }

    private static void validateParents(CatalogRepository repo, Entry current, Draft draft)
            throws SQLException {
        if (draft instanceof ProductDraft p) {
            Line line = (Line) repo.load(CatalogKind.LINES, p.lineId());
            boolean same =
                    current instanceof Product before
                            && before.line() != null
                            && before.line().id() == line.id();
            if (!line.available() && !same)
                throw invalid("La linea è archiviata. Riattivala prima di aggiungere un prodotto.");
        }
        if (draft instanceof ArticleDraft a) {
            Product product = (Product) repo.load(CatalogKind.PRODUCTS, a.productId());
            boolean same =
                    current instanceof Article before && before.product().id() == product.id();
            if (!product.available() && !same)
                throw invalid(
                        "Il prodotto o la sua linea sono archiviati, oppure il prodotto è da"
                            + " completare.");
        }
    }

    private static void validateStructure(Entry current, Draft draft) {
        if (!current.used()) return;
        if (current instanceof Line l
                && draft instanceof LineDraft d
                && (!l.technology().isBlank() && !l.technology().equals(d.technology())
                        || !l.material().isBlank() && !l.material().equals(d.material())))
            throw invalid(
                    "La linea è già utilizzata. Per cambiare tecnologia o materia prima crea una"
                        + " nuova linea.");
        if (current instanceof Product p && draft instanceof ProductDraft d) {
            if (p.line() != null && !Objects.equals(p.line().id(), d.lineId())
                    || p.color() != null && !Objects.equals(p.color(), d.color()))
                throw invalid(
                        "Il prodotto è già utilizzato. Per cambiare linea o colore crea un nuovo"
                            + " prodotto.");
        }
        if (current instanceof Article a
                && draft instanceof ArticleDraft d
                && (a.product().id() != d.productId()
                        || a.diameter().compareTo(d.diameter()) != 0
                        || a.height().compareTo(d.height()) != 0))
            throw invalid(
                    "L'articolo è già configurato. Per cambiare prodotto o formato crea un nuovo"
                        + " articolo.");
    }

    private static void verifyCode(CatalogRepository repo, CatalogKind kind, long id, String code)
            throws SQLException {
        if (repo.count(
                        "SELECT COUNT(*) FROM "
                                + kind.historyTable()
                                + " WHERE codice=? AND "
                                + kind.key()
                                + "<>?",
                        List.of(code, id))
                > 0)
            throw invalid(
                    "Questo codice è già stato utilizzato da un'altra anagrafica. Scegli un codice"
                        + " diverso per conservare i riferimenti storici.");
    }

    private <T> CompletableFuture<T> read(Supplier<T> query) {
        return CompletableFuture.supplyAsync(query, workers);
    }

    private <T> CompletableFuture<T> command(Supplier<T> command) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return command.get();
                    } catch (RuntimeException failure) {
                        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                            if (cause instanceof SQLException sql) {
                                if ("23505".equals(sql.getSQLState()))
                                    throw new IllegalArgumentException(
                                            "Esiste già un codice o una combinazione con questi"
                                                + " dati. Nessuna modifica è stata salvata.",
                                            failure);
                                if ("23503".equals(sql.getSQLState()))
                                    throw new IllegalArgumentException(
                                            "Esistono collegamenti da conservare. Nessuna modifica"
                                                + " è stata salvata; ricarica la scheda.",
                                            failure);
                            }
                        }
                        throw failure;
                    }
                },
                workers);
    }
}
