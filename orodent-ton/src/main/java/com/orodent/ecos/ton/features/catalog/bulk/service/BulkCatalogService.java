package com.orodent.ecos.ton.features.catalog.bulk.service;

import static com.orodent.ecos.ton.features.catalog.bulk.model.BulkCatalogModels.*;
import static com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import com.orodent.ecos.core.ConnectionProvider;
import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.repository.*;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Bounded, asynchronous bulk commands. A group and all its audit records commit together. */
public final class BulkCatalogService {
    private final ConnectionProvider connections;
    private final Executor workers;

    public BulkCatalogService(ConnectionProvider connections, Executor workers) {
        this.connections = Objects.requireNonNull(connections);
        this.workers = Objects.requireNonNull(workers);
    }

    public CompletableFuture<ParentPage> parents(
            CatalogKind kind, String search, Long lineId, int offset) {
        return async(
                () ->
                        connections.withConnection(
                                c -> {
                                    var args = new ArrayList<Object>();
                                    String query =
                                            "%"
                                                    + text(search)
                                                            .toUpperCase(Locale.ROOT)
                                                            .replace("\\", "\\\\")
                                                            .replace("%", "\\%")
                                                            .replace("_", "\\_")
                                                    + "%";
                                    args.add(query);
                                    args.add(query);
                                    String where =
                                            " WHERE (UPPER(b.codice) LIKE ? ESCAPE '\\' OR"
                                                    + " UPPER(COALESCE("
                                                    + (kind == CatalogKind.LINES
                                                            ? "b.nome"
                                                            : "b.descrizione")
                                                    + ",'')) LIKE ? ESCAPE '\\') AND b.attivo=TRUE";
                                    if (kind == CatalogKind.PRODUCTS) {
                                        where =
                                                " WHERE (UPPER(b.codice) LIKE ? ESCAPE '\\' OR"
                                                    + " UPPER(COALESCE(b.descrizione,'')) LIKE ?"
                                                    + " ESCAPE '\\' OR"
                                                    + " UPPER(COALESCE(b.colore_commerciale,''))"
                                                    + " LIKE ? ESCAPE '\\') AND b.attivo=TRUE AND"
                                                    + " l.attivo=TRUE AND b.colore_commerciale IS"
                                                    + " NOT NULL";
                                        args.add(query);
                                        if (lineId != null) {
                                            where += " AND b.linea_id=?";
                                            args.add(lineId);
                                        }
                                    }
                                    var found = new ArrayList<Parent>();
                                    try (var q =
                                            c.prepareStatement(
                                                    parentSelect(kind)
                                                            + where
                                                            + " ORDER BY b.codice OFFSET ? ROWS"
                                                            + " FETCH NEXT ? ROWS ONLY")) {
                                        CatalogRepository.bind(q, args);
                                        q.setInt(args.size() + 1, Math.max(0, offset));
                                        q.setInt(args.size() + 2, PAGE_SIZE + 1);
                                        try (var r = q.executeQuery()) {
                                            while (r.next()) found.add(parent(kind, r));
                                        }
                                    }
                                    return new ParentPage(
                                            found.stream().limit(PAGE_SIZE).toList(),
                                            found.size() > PAGE_SIZE,
                                            Math.max(0, offset));
                                }));
    }

    public CompletableFuture<List<Parent>> parentsByIds(CatalogKind kind, Collection<Long> ids) {
        var requested = List.copyOf(ids);
        return async(
                () ->
                        connections.withConnection(
                                c -> {
                                    var result = new ArrayList<Parent>();
                                    for (long id : new TreeSet<>(requested))
                                        result.add(loadParent(c, kind, id));
                                    return List.copyOf(result);
                                }));
    }

    public CompletableFuture<Preview> preview(Request request) {
        return async(() -> connections.withTransaction(c -> evaluate(c, request).preview()));
    }

    public CompletableFuture<Created> create(Preview expected) {
        if (!expected.savable())
            throw invalid("Verifica e correggi l'anteprima prima di creare il gruppo.");
        return async(
                () ->
                        connections.withTransaction(
                                c -> {
                                    c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                                    lockParents(c, expected.request());
                                    Evaluation current = evaluate(c, expected.request());
                                    if (!current.preview().savable()
                                            || !current.preview().rows().equals(expected.rows()))
                                        throw invalid(
                                                "I dati dell'anteprima sono cambiati. Verifica di"
                                                        + " nuovo le righe prima di salvare. Nessun"
                                                        + " elemento è stato creato.");
                                    var repo = new CatalogRepository(c);
                                    var history = new CatalogHistoryRepository(c);
                                    var created = new ArrayList<Entry>();
                                    for (var row : current.preview().rows())
                                        if (row.state() == State.NEW) {
                                            Draft draft = current.drafts().get(row.key());
                                            long id = repo.insert(draft);
                                            Entry entry = repo.load(draft.kind(), id);
                                            history.record(entry, "CREAZIONE");
                                            created.add(entry);
                                        }
                                    return new Created(created, current.preview().existing());
                                }));
    }

    private record Evaluation(Preview preview, Map<Integer, Draft> drafts) {}

    private record Existing(
            long id,
            String code,
            long parent,
            String color,
            BigDecimal diameter,
            BigDecimal height,
            boolean active) {}

    private Evaluation evaluate(Connection c, Request request) throws SQLException {
        var results = new LinkedHashMap<Integer, RowResult>();
        var drafts = new LinkedHashMap<Integer, Draft>();
        var parents = new HashMap<Long, Parent>();
        for (Seed seed : request.rows()) {
            if (!seed.selected()) {
                results.put(seed.key(), new RowResult(seed.key(), State.EXCLUDED, "Escluso", null));
                continue;
            }
            try {
                Parent snapshot =
                        Objects.requireNonNull(
                                seed.parent(), "Seleziona il collegamento della riga.");
                CatalogKind parentKind = request.kind().parent();
                if (snapshot.kind() != parentKind)
                    throw invalid("Collegamento non coerente con il tipo di anagrafica.");
                Parent parent = parents.get(snapshot.id());
                if (parent == null) {
                    parent = loadParent(c, parentKind, snapshot.id());
                    parents.put(parent.id(), parent);
                }
                if (parent.revision() != snapshot.revision()
                        || parent.lineRevision() != snapshot.lineRevision()
                        || parent.lineId() != snapshot.lineId())
                    throw invalid(
                            "Il collegamento è stato modificato. Aggiorna i dati per ricalcolare"
                                    + " l'anteprima.");
                if (!parent.available())
                    throw invalid("Il collegamento o la linea sono archiviati o incompleti.");
                Draft draft =
                        request.kind() == CatalogKind.PRODUCTS
                                ? new ProductDraft(
                                        seed.code(),
                                        productDescription(parent.name(), seed.color()),
                                        parent.id(),
                                        seed.color(),
                                        request.notes(),
                                        true)
                                : new ArticleDraft(
                                        seed.code(),
                                        parent.name(),
                                        parent.id(),
                                        decimal(seed.diameter(), "Diametro"),
                                        decimal(seed.height(), "Altezza"),
                                        request.notes(),
                                        true);
                if (draft instanceof ProductDraft p) {
                    p.requireComplete();
                    if (p.code().isBlank()) throw invalid("Il codice prodotto è obbligatorio.");
                }
                drafts.put(seed.key(), draft);
            } catch (IllegalArgumentException | NullPointerException invalid) {
                results.put(
                        seed.key(),
                        new RowResult(seed.key(), State.INVALID, invalid.getMessage(), null));
            }
        }
        if (request.kind() == CatalogKind.ARTICLES
                && parents.values().stream().map(Parent::lineId).distinct().count() > 1) {
            for (Seed seed : request.rows())
                if (seed.selected()) {
                    results.put(
                            seed.key(),
                            new RowResult(
                                    seed.key(),
                                    State.INVALID,
                                    "Scegli prodotti di una sola linea per ogni gruppo di"
                                            + " articoli.",
                                    null));
                }
            drafts.clear();
        }
        var codeCounts = new HashMap<String, Integer>();
        var naturalCounts = new HashMap<String, Integer>();
        drafts.values()
                .forEach(
                        d -> {
                            codeCounts.merge(d.code(), 1, Integer::sum);
                            naturalCounts.merge(natural(d), 1, Integer::sum);
                        });
        List<Existing> existing = findExisting(c, request.kind(), new ArrayList<>(drafts.values()));
        var byCode = new HashMap<String, List<Existing>>();
        var byNatural = new HashMap<String, Existing>();
        for (var entry : existing) {
            byCode.computeIfAbsent(entry.code().toUpperCase(Locale.ROOT), k -> new ArrayList<>())
                    .add(entry);
            byNatural.put(natural(entry, request.kind()), entry);
        }
        Set<String> historical =
                historyCodes(
                        c,
                        request.kind(),
                        drafts.values().stream().map(Draft::code).distinct().toList());
        for (var candidate : drafts.entrySet()) {
            int key = candidate.getKey();
            Draft d = candidate.getValue();
            String error = null;
            Existing match = null;
            if (codeCounts.get(d.code()) > 1) error = "Codice duplicato nel gruppo";
            else if (naturalCounts.get(natural(d)) > 1)
                error =
                        request.kind() == CatalogKind.PRODUCTS
                                ? "Colore duplicato nel gruppo"
                                : "Formato duplicato nel gruppo";
            else {
                List<Existing> sameCode = byCode.getOrDefault(d.code(), List.of());
                if (sameCode.size() > 1) error = "Codice non univoco nel catalogo";
                else if (!sameCode.isEmpty()) {
                    Existing e = sameCode.getFirst();
                    if (natural(e, request.kind()).equals(natural(d))) match = e;
                    else error = "Codice già usato per un altro collegamento o formato";
                } else if (byNatural.containsKey(natural(d))) {
                    error =
                            (request.kind() == CatalogKind.PRODUCTS
                                            ? "Colore già presente: "
                                            : "Formato già presente: ")
                                    + byNatural.get(natural(d)).code();
                } else if (historical.contains(d.code()))
                    error = "Codice già utilizzato nello storico";
            }
            results.put(
                    key,
                    error != null
                            ? new RowResult(key, State.INVALID, error, null)
                            : match != null
                                    ? new RowResult(
                                            key,
                                            State.EXISTING,
                                            match.active()
                                                    ? "Già presente"
                                                    : "Già presente · archiviato",
                                            match.id())
                                    : new RowResult(key, State.NEW, "Nuovo", null));
        }
        return new Evaluation(
                new Preview(
                        request, request.rows().stream().map(s -> results.get(s.key())).toList()),
                drafts);
    }

    private static BigDecimal decimal(String raw, String label) {
        String value = text(raw);
        if (!value.matches("[0-9]+([.,][0-9]+)?"))
            throw invalid(label + ": usa un numero con virgola o punto decimale.");
        return new BigDecimal(value.replace(',', '.'));
    }

    private static String natural(Draft d) {
        if (d instanceof ProductDraft p) return p.lineId() + "|" + p.color();
        ArticleDraft a = (ArticleDraft) d;
        return a.productId() + "|" + number(a.diameter()) + "|" + number(a.height());
    }

    private static String natural(Existing e, CatalogKind kind) {
        return kind == CatalogKind.PRODUCTS
                ? e.parent() + "|" + commercialColor(e.color())
                : e.parent() + "|" + number(e.diameter()) + "|" + number(e.height());
    }

    private static String number(BigDecimal n) {
        return n.stripTrailingZeros().toPlainString();
    }

    private static List<Existing> findExisting(Connection c, CatalogKind kind, List<Draft> drafts)
            throws SQLException {
        var found = new LinkedHashMap<Long, Existing>();
        List<String> codes = drafts.stream().map(Draft::code).distinct().toList();
        for (int start = 0; start < codes.size(); start += 100) {
            List<String> batch = codes.subList(start, Math.min(start + 100, codes.size()));
            existingQuery(
                    c,
                    kind,
                    "UPPER(codice) IN ("
                            + String.join(",", Collections.nCopies(batch.size(), "?"))
                            + ")",
                    batch,
                    found);
        }
        for (int start = 0; start < drafts.size(); start += 50) {
            var args = new ArrayList<Object>();
            var conditions = new ArrayList<String>();
            for (Draft d : drafts.subList(start, Math.min(start + 50, drafts.size()))) {
                if (d instanceof ProductDraft p) {
                    conditions.add("(linea_id=? AND UPPER(colore_commerciale)=?)");
                    args.add(p.lineId());
                    args.add(p.color());
                } else {
                    ArticleDraft a = (ArticleDraft) d;
                    conditions.add("(prodotto_id=? AND diametro_mm=? AND altezza_mm=?)");
                    args.add(a.productId());
                    args.add(a.diameter());
                    args.add(a.height());
                }
            }
            existingQuery(c, kind, String.join(" OR ", conditions), args, found);
        }
        return List.copyOf(found.values());
    }

    private static void existingQuery(
            Connection c, CatalogKind kind, String where, List<?> args, Map<Long, Existing> found)
            throws SQLException {
        boolean product = kind == CatalogKind.PRODUCTS;
        String fields =
                product
                        ? "prodotto_id,codice,linea_id,colore_commerciale,attivo"
                        : "articolo_id,codice,prodotto_id,diametro_mm,altezza_mm,attivo";
        try (var q =
                c.prepareStatement(
                        "SELECT " + fields + " FROM " + kind.table() + " WHERE " + where)) {
            CatalogRepository.bind(q, args);
            try (var r = q.executeQuery()) {
                while (r.next()) {
                    Existing e =
                            new Existing(
                                    r.getLong(1),
                                    r.getString(2),
                                    r.getLong(3),
                                    product ? r.getString(4) : null,
                                    product ? null : r.getBigDecimal(4),
                                    product ? null : r.getBigDecimal(5),
                                    r.getBoolean(product ? 5 : 6));
                    found.put(e.id(), e);
                }
            }
        }
    }

    private static Set<String> historyCodes(Connection c, CatalogKind kind, List<String> codes)
            throws SQLException {
        var result = new HashSet<String>();
        for (int start = 0; start < codes.size(); start += 100) {
            List<String> batch = codes.subList(start, Math.min(start + 100, codes.size()));
            try (var q =
                    c.prepareStatement(
                            "SELECT DISTINCT UPPER(codice) FROM "
                                    + kind.historyTable()
                                    + " WHERE UPPER(codice) IN ("
                                    + String.join(",", Collections.nCopies(batch.size(), "?"))
                                    + ")")) {
                CatalogRepository.bind(q, batch);
                try (var r = q.executeQuery()) {
                    while (r.next()) result.add(r.getString(1));
                }
            }
        }
        return result;
    }

    private static void lockParents(Connection c, Request request) throws SQLException {
        var repo = new CatalogRepository(c);
        var lines = new TreeSet<Long>();
        var products = new TreeSet<Long>();
        var mapping = new HashMap<Long, Long>();
        for (Seed seed : request.rows())
            if (seed.selected() && seed.parent() != null) {
                if (request.kind() == CatalogKind.PRODUCTS) lines.add(seed.parent().id());
                else products.add(seed.parent().id());
            }
        // Planning must not acquire a product lock before the sorted line locks.
        // Dirty parent keys are only hints: the mapping is verified again under locks.
        for (long id : products) {
            Long line;
            try (var q =
                    c.prepareStatement(
                            "SELECT linea_id FROM TON.PRODOTTI WHERE prodotto_id=? WITH UR")) {
                q.setLong(1, id);
                try (var r = q.executeQuery()) {
                    if (!r.next()) throw new Conflict();
                    long value = r.getLong(1);
                    line = r.wasNull() ? null : value;
                }
            }
            mapping.put(id, line);
            if (line != null) lines.add(line);
        }
        for (long id : lines) repo.lock(CatalogKind.LINES, id);
        for (long id : products) repo.lock(CatalogKind.PRODUCTS, id);
        for (long id : products)
            if (!Objects.equals(mapping.get(id), repo.productLine(id))) throw new Conflict();
    }

    private static String parentSelect(CatalogKind kind) {
        if (kind == CatalogKind.LINES)
            return "SELECT b.linea_id AS parent_id,b.codice AS parent_code,b.nome AS"
                       + " parent_name,b.linea_id,b.codice AS line_code,CAST(NULL AS VARCHAR(30))"
                       + " AS color,b.versione_modifica AS parent_revision,b.versione_modifica AS"
                       + " line_revision,b.attivo AS available FROM TON.LINEE b";
        if (kind != CatalogKind.PRODUCTS) throw invalid("Seleziona linee o prodotti.");
        return "SELECT b.prodotto_id AS parent_id,b.codice AS parent_code,b.descrizione AS"
                   + " parent_name,b.linea_id,l.codice AS line_code,b.colore_commerciale AS"
                   + " color,b.versione_modifica AS parent_revision,l.versione_modifica AS"
                   + " line_revision,CASE WHEN b.attivo=TRUE AND l.attivo=TRUE AND"
                   + " b.colore_commerciale IS NOT NULL THEN TRUE ELSE FALSE END AS available FROM"
                   + " TON.PRODOTTI b LEFT JOIN TON.LINEE l ON l.linea_id=b.linea_id";
    }

    private static Parent loadParent(Connection c, CatalogKind kind, long id) throws SQLException {
        try (var q = c.prepareStatement(parentSelect(kind) + " WHERE b." + kind.key() + "=?")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next())
                    throw invalid("Il collegamento non esiste più. Aggiorna la selezione.");
                return parent(kind, r);
            }
        }
    }

    private static Parent parent(CatalogKind kind, ResultSet r) throws SQLException {
        return new Parent(
                r.getLong("parent_id"),
                kind,
                r.getString("parent_code"),
                r.getString("parent_name"),
                r.getLong("linea_id"),
                r.getString("line_code"),
                r.getString("color"),
                r.getInt("parent_revision"),
                r.getInt("line_revision"),
                r.getBoolean("available"));
    }

    private <T> CompletableFuture<T> async(Supplier<T> operation) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        return operation.get();
                    } catch (RuntimeException error) {
                        for (Throwable e = error; e != null; e = e.getCause())
                            if (e instanceof SQLException sql
                                    && List.of("23505", "23503", "40001")
                                            .contains(sql.getSQLState()))
                                throw new IllegalArgumentException(
                                        "Il catalogo è cambiato o contiene già questi dati. Nessun"
                                                + " elemento del gruppo è stato creato. Verifica di"
                                                + " nuovo l'anteprima.",
                                        error);
                        throw error;
                    }
                },
                workers);
    }
}
