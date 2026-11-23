package com.orodent.ecos.ton.features.availability.repository;

import static com.orodent.ecos.ton.features.availability.model.Availability.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Shared current snapshots and exact catalogue-code associations; caller owns the transaction. */
public final class AvailabilityRepository {
    private final Connection c;

    public AvailabilityRepository(Connection c) {
        this.c = c;
    }

    public List<Article> articles() throws SQLException {
        var result = new ArrayList<Article>();
        try (var q = c.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT"
                                    + " a.articolo_id,a.codice,a.prodotto_id,p.codice,p.colore_commerciale,l.linea_id,l.codice,l.nome,a.diametro_mm,a.altezza_mm,a.attivo,p.attivo,l.attivo"
                                    + " FROM TON.ARTICOLI a JOIN TON.PRODOTTI p ON"
                                    + " p.prodotto_id=a.prodotto_id LEFT JOIN TON.LINEE l ON"
                                    + " l.linea_id=p.linea_id")) {
            while (r.next())
                result.add(
                        new Article(
                                r.getLong(1),
                                r.getString(2),
                                r.getLong(3),
                                r.getString(4),
                                r.getString(5),
                                (Long) r.getObject(6),
                                r.getString(7),
                                r.getString(8),
                                r.getBigDecimal(9),
                                r.getBigDecimal(10),
                                r.getBoolean(11) && r.getBoolean(12) && r.getBoolean(13)));
        }
        result.sort(
                Comparator.comparing((Article a) -> a.lineId() == null)
                        .thenComparing(
                                a -> Objects.toString(a.lineLabel(), ""),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(
                                a -> Objects.toString(a.lineCode(), ""),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Article::productCode, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(
                                Article::diameter, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(
                                Article::height, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Article::code, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public Map<String, Article> aliases(List<Article> articles) throws SQLException {
        var byId = new HashMap<Long, Article>();
        var result = new HashMap<String, Article>();
        for (var a : articles) {
            byId.put(a.id(), a);
            putAlias(result, a.code(), a);
        }
        try (var q = c.createStatement();
                var r = q.executeQuery("SELECT articolo_id,codice FROM TON.ARTICOLI_REVISIONI")) {
            while (r.next()) {
                Article a = byId.get(r.getLong(1));
                if (a != null) putAlias(result, r.getString(2), a);
            }
        }
        return result;
    }

    private static void putAlias(Map<String, Article> result, String raw, Article article) {
        String key = code(raw);
        var old = result.putIfAbsent(key, article);
        if (old != null && old.id() != article.id())
            throw new IllegalArgumentException(
                    "Il codice "
                            + key
                            + " è associato a più articoli. Verifica le revisioni del catalogo.");
    }

    public Source source(Kind kind) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "SELECT"
                            + " f.versione_modifica,i.import_id,i.nome_file,i.data_riferimento,i.importato_il,i.codici_assenti_zero,i.magazzini_distinti"
                            + " FROM TON.DISPONIBILITA_FONTI f LEFT JOIN TON.DISPONIBILITA_IMPORT i"
                            + " ON i.import_id=f.import_id WHERE f.tipo=?")) {
            q.setString(1, kind.name());
            try (var r = q.executeQuery()) {
                if (!r.next()) throw new SQLException("Fonte disponibilità mancante.");
                Long id = (Long) r.getObject(2);
                var values = new LinkedHashMap<QuantityKey, BigDecimal>();
                if (id != null)
                    try (var v =
                            c.prepareStatement(
                                    "SELECT codice,quantita,magazzino FROM TON.DISPONIBILITA_VALORI"
                                        + " WHERE import_id=? ORDER BY codice")) {
                        v.setLong(1, id);
                        try (var data = v.executeQuery()) {
                            while (data.next())
                                values.put(
                                        new QuantityKey(data.getString(1), data.getString(3)),
                                        data.getBigDecimal(2));
                        }
                    }
                return new Source(
                        kind,
                        r.getInt(1),
                        id,
                        r.getString(3),
                        r.getDate(4) == null ? null : r.getDate(4).toLocalDate(),
                        r.getTimestamp(5) == null ? null : r.getTimestamp(5).toLocalDateTime(),
                        r.getBoolean(6),
                        values,
                        r.getBoolean(7));
            }
        }
    }

    public Snapshot snapshot(Period period, LocalDate today) throws SQLException {
        var articles = articles();
        var aliases = aliases(articles);
        var stock = source(Kind.DISPONIBILITA);
        var orders = source(Kind.ORDINI);
        var quantities = aggregate(codeTotals(stock.values()), aliases);
        var pending = aggregate(codeTotals(orders.values()), aliases);
        var warehouses = warehouses(stock.present() && !stock.separateWarehouses());
        var warehouseAmounts = new LinkedHashMap<String, Map<Long, BigDecimal>>();
        for (var warehouse : warehouses) {
            if (!stock.present()
                    || (stock.separateWarehouses() == warehouse.code().equals(UNALLOCATED)))
                continue;
            var subset = new LinkedHashMap<String, BigDecimal>();
            stock.values()
                    .forEach(
                            (k, n) -> {
                                if (k.warehouse().equals(warehouse.code()))
                                    subset.merge(k.code(), n, BigDecimal::add);
                            });
            warehouseAmounts.put(warehouse.code(), aggregate(subset, aliases));
        }
        LocalDate last = null;
        try (var q =
                c.prepareStatement(
                        "SELECT MAX(data_vendita) FROM VENDITE.VENDITE_DETTAGLIO WHERE"
                            + " data_vendita<=?")) {
            q.setDate(1, java.sql.Date.valueOf(today));
            try (var r = q.executeQuery()) {
                r.next();
                if (r.getDate(1) != null) last = r.getDate(1).toLocalDate();
            }
        }
        LocalDate through = last == null ? today : last,
                from = through.minusDays(period.days() - 1L);
        var sales = new HashMap<String, BigDecimal>();
        try (var q =
                c.prepareStatement(
                        "SELECT UPPER(TRIM(codice_prodotto)),SUM(quantita) FROM"
                            + " VENDITE.VENDITE_DETTAGLIO WHERE data_vendita BETWEEN ? AND ? AND"
                            + " tipo_operazione='VENDITA' AND quantita>0 GROUP BY"
                            + " UPPER(TRIM(codice_prodotto))")) {
            q.setDate(1, java.sql.Date.valueOf(from));
            q.setDate(2, java.sql.Date.valueOf(through));
            try (var r = q.executeQuery()) {
                while (r.next()) sales.put(r.getString(1), r.getBigDecimal(2));
            }
        }
        var sold = aggregate(sales, aliases);
        var rows = new ArrayList<Row>();
        for (var a : articles) {
            var perWarehouse = new LinkedHashMap<String, BigDecimal>();
            warehouseAmounts.forEach(
                    (warehouse, amounts) -> {
                        var n = amount(stock, amounts, a.id());
                        if (n != null) perWarehouse.put(warehouse, n);
                    });
            rows.add(
                    new Row(
                            a,
                            amount(stock, quantities, a.id()),
                            amount(orders, pending, a.id()),
                            last == null ? null : sold.getOrDefault(a.id(), BigDecimal.ZERO),
                            perWarehouse));
        }
        return new Snapshot(
                rows,
                Map.of(Kind.DISPONIBILITA, stock, Kind.ORDINI, orders),
                from,
                through,
                last,
                unknown(stock, aliases),
                unknown(orders, aliases),
                warehouses);
    }

    private static BigDecimal amount(Source source, Map<Long, BigDecimal> values, long id) {
        if (!source.present()) return null;
        return values.getOrDefault(id, source.missingZero() ? BigDecimal.ZERO : null);
    }

    private static int unknown(Source source, Map<String, Article> aliases) {
        return (int)
                source.values().keySet().stream()
                        .map(QuantityKey::code)
                        .distinct()
                        .filter(k -> !aliases.containsKey(k))
                        .count();
    }

    private static Map<String, BigDecimal> codeTotals(Map<QuantityKey, BigDecimal> values) {
        var result = new LinkedHashMap<String, BigDecimal>();
        values.forEach((key, n) -> result.merge(key.code(), n, BigDecimal::add));
        return result;
    }

    private List<Warehouse> warehouses(boolean includeUnallocated) throws SQLException {
        var values = new ArrayList<Warehouse>();
        try (var q = c.createStatement();
                var r = q.executeQuery("SELECT codice,nome FROM TON.MAGAZZINI ORDER BY codice")) {
            while (r.next())
                if (includeUnallocated || !r.getString(1).equals(UNALLOCATED))
                    values.add(new Warehouse(r.getString(1), r.getString(2)));
        }
        return List.copyOf(values);
    }

    private static Map<Long, BigDecimal> aggregate(
            Map<String, BigDecimal> values, Map<String, Article> aliases) {
        var result = new HashMap<Long, BigDecimal>();
        values.forEach(
                (k, n) -> {
                    var a = aliases.get(k);
                    if (a != null) result.merge(a.id(), n, BigDecimal::add);
                });
        return result;
    }

    public void apply(Preview preview) throws SQLException {
        if (!preview.errors().isEmpty())
            throw new IllegalArgumentException("Correggi gli errori prima di importare.");
        try (var q =
                c.prepareStatement(
                        "UPDATE TON.DISPONIBILITA_FONTI SET versione_modifica=versione_modifica+1"
                            + " WHERE tipo=? AND versione_modifica=?")) {
            q.setString(1, preview.kind().name());
            q.setInt(2, preview.expectedRevision());
            if (q.executeUpdate() != 1) throw new Conflict();
        }
        if (preview.kind() == Kind.DISPONIBILITA) {
            var known = new HashSet<String>();
            warehouses(true).forEach(w -> known.add(w.code()));
            try (var q =
                    c.prepareStatement("INSERT INTO TON.MAGAZZINI(codice,nome) VALUES (?,?)")) {
                for (var key : preview.values().keySet())
                    if (known.add(key.warehouse())) {
                        q.setString(1, key.warehouse());
                        q.setString(2, key.warehouse());
                        q.executeUpdate();
                    }
            }
        }
        long id;
        try (var q =
                c.prepareStatement(
                        "INSERT INTO"
                            + " TON.DISPONIBILITA_IMPORT(tipo,nome_file,data_riferimento,codici_assenti_zero,righe,magazzini_distinti)"
                            + " VALUES (?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            q.setString(1, preview.kind().name());
            q.setString(2, preview.file());
            q.setDate(3, java.sql.Date.valueOf(preview.date()));
            q.setBoolean(4, preview.missingZero());
            q.setInt(5, preview.inputRows());
            q.setBoolean(6, preview.kind() == Kind.DISPONIBILITA);
            q.executeUpdate();
            try (var r = q.getGeneratedKeys()) {
                r.next();
                id = r.getLong(1);
            }
        }
        try (var q =
                c.prepareStatement(
                        "INSERT INTO TON.DISPONIBILITA_VALORI(import_id,codice,quantita,magazzino)"
                            + " VALUES (?,?,?,?)")) {
            for (var e : preview.values().entrySet()) {
                q.setLong(1, id);
                q.setString(2, e.getKey().code());
                q.setBigDecimal(3, e.getValue());
                q.setString(4, e.getKey().warehouse());
                q.addBatch();
            }
            q.executeBatch();
        }
        try (var q =
                c.prepareStatement("UPDATE TON.DISPONIBILITA_FONTI SET import_id=? WHERE tipo=?")) {
            q.setLong(1, id);
            q.setString(2, preview.kind().name());
            q.executeUpdate();
        }
    }
}
