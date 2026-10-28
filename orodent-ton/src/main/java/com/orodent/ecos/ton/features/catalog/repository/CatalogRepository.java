package com.orodent.ecos.ton.features.catalog.repository;

import com.orodent.ecos.ton.features.catalog.model.CatalogKind;
import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import java.sql.*;
import java.util.*;

/** Typed catalogue SQL on a caller-owned connection. Transactions belong to the service. */
public final class CatalogRepository {
    private final Connection connection;

    public CatalogRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    public Page page(CatalogKind kind, Filter filter, int offset, int limit) throws SQLException {
        var parameters = new ArrayList<Object>();
        String where = where(kind, filter, parameters);
        long total = count("SELECT COUNT(*)" + from(kind) + where, parameters);
        var rows = new ArrayList<Entry>();
        try (var query =
                connection.prepareStatement(
                        select(kind)
                                + where
                                + " ORDER BY b.codice,b."
                                + kind.key()
                                + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
            bind(query, parameters);
            query.setInt(parameters.size() + 1, offset);
            query.setInt(parameters.size() + 2, limit);
            try (var result = query.executeQuery()) {
                while (result.next()) rows.add(map(kind, result));
            }
        }
        return new Page(rows, total, offset, limit);
    }

    public Entry load(CatalogKind kind, long id) throws SQLException {
        try (var query =
                connection.prepareStatement(select(kind) + " WHERE b." + kind.key() + "=?")) {
            query.setLong(1, id);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) throw new SQLException("Anagrafica non trovata.");
                return map(kind, rows);
            }
        }
    }

    public List<Choice> choices(CatalogKind kind, String search, Long selected)
            throws SQLException {
        if (kind == CatalogKind.ARTICLES)
            throw new IllegalArgumentException("Gli articoli non sono un selettore padre.");
        var values = new ArrayList<Object>();
        String where = where(kind, new Filter(search, false, null, null, null, null), values);
        if (kind == CatalogKind.PRODUCTS)
            where +=
                    " AND b.linea_id IS NOT NULL AND b.colore_commerciale IS NOT NULL AND"
                            + " l.attivo=TRUE";
        var result = new ArrayList<Choice>();
        try (var query =
                connection.prepareStatement(
                        select(kind) + where + " ORDER BY b.codice FETCH NEXT 50 ROWS ONLY")) {
            bind(query, values);
            try (var rows = query.executeQuery()) {
                while (rows.next()) result.add(choice(map(kind, rows)));
            }
        }
        if (selected != null && result.stream().noneMatch(c -> c.id() == selected))
            result.addFirst(choice(load(kind, selected)));
        return List.copyOf(result);
    }

    public static Choice choice(Entry entry) {
        return switch (entry) {
            case Line l ->
                    new Choice(l.id(), l.code(), l.name(), l.available(), l.id(), l.code(), null);
            case Product p ->
                    new Choice(
                            p.id(),
                            p.code(),
                            p.name(),
                            p.available(),
                            p.line() == null ? null : p.line().id(),
                            p.line() == null ? null : p.line().code(),
                            p.color());
            case Article ignored ->
                    throw new IllegalArgumentException("Un articolo non è un padre di catalogo.");
        };
    }

    public List<Choice> previousLines(long productId) throws SQLException {
        var result = new ArrayList<Choice>();
        try (var query =
                connection.prepareStatement(
                        "SELECT l.* FROM TON.LINEE l JOIN TON.LINEE_PRODOTTI lp ON"
                            + " lp.linea_id=l.linea_id WHERE lp.prodotto_id=? ORDER BY l.codice")) {
            query.setLong(1, productId);
            try (var rows = query.executeQuery()) {
                while (rows.next())
                    result.add(
                            new Choice(
                                    rows.getLong("linea_id"),
                                    rows.getString("codice"),
                                    rows.getString("nome"),
                                    rows.getBoolean("attivo"),
                                    rows.getLong("linea_id"),
                                    rows.getString("codice"),
                                    null));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Read only the parent key before acquiring locks; avoid joined counters on other aggregates.
     */
    public Long productLine(long id) throws SQLException {
        try (var query =
                connection.prepareStatement(
                        "SELECT linea_id FROM TON.PRODOTTI WHERE prodotto_id=?")) {
            query.setLong(1, id);
            try (var rows = query.executeQuery()) {
                if (!rows.next()) throw new Conflict();
                long line = rows.getLong(1);
                return rows.wasNull() ? null : line;
            }
        }
    }

    /** A no-op UPDATE locks the aggregate; it must not create a spurious audit revision. */
    public void lock(CatalogKind kind, long id) throws SQLException {
        try (var query =
                connection.prepareStatement(
                        "UPDATE "
                                + kind.table()
                                + " SET versione_modifica=versione_modifica WHERE "
                                + kind.key()
                                + "=?")) {
            query.setLong(1, id);
            if (query.executeUpdate() != 1) throw new Conflict();
        }
    }

    public long insert(Draft draft) throws SQLException {
        String fields =
                switch (draft) {
                    case LineDraft ignored -> "codice,nome,tecnologia,materia_prima,note,attivo";
                    case ProductDraft ignored ->
                            "codice,descrizione,linea_id,colore_commerciale,note,attivo";
                    case ArticleDraft ignored ->
                            "codice,descrizione,prodotto_id,diametro_mm,altezza_mm,note,attivo";
                };
        List<Object> values = values(draft);
        try (var query =
                connection.prepareStatement(
                        "INSERT INTO "
                                + draft.kind().table()
                                + "("
                                + fields
                                + ") VALUES ("
                                + String.join(",", Collections.nCopies(values.size(), "?"))
                                + ")",
                        Statement.RETURN_GENERATED_KEYS)) {
            bind(query, values);
            query.executeUpdate();
            try (var keys = query.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Identificativo non disponibile.");
                return keys.getLong(1);
            }
        }
    }

    public void update(Entry expected, Draft draft) throws SQLException {
        String assignments =
                switch (draft) {
                    case LineDraft ignored ->
                            "codice=?,nome=?,tecnologia=?,materia_prima=?,note=?,attivo=?";
                    case ProductDraft ignored ->
                            "codice=?,descrizione=?,linea_id=?,colore_commerciale=?,note=?,attivo=?";
                    case ArticleDraft ignored ->
                            "codice=?,descrizione=?,prodotto_id=?,diametro_mm=?,altezza_mm=?,note=?,attivo=?";
                };
        List<Object> values = values(draft);
        try (var query =
                connection.prepareStatement(
                        "UPDATE "
                                + draft.kind().table()
                                + " SET "
                                + assignments
                                + ",versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP"
                                + " WHERE "
                                + draft.kind().key()
                                + "=? AND versione_modifica=?")) {
            bind(query, values);
            query.setLong(values.size() + 1, expected.id());
            query.setInt(values.size() + 2, expected.revision());
            if (query.executeUpdate() != 1) throw new Conflict();
        }
    }

    public void active(Entry expected, boolean active) throws SQLException {
        var kind = expected.kind();
        try (var query =
                connection.prepareStatement(
                        "UPDATE "
                                + kind.table()
                                + " SET attivo=?,versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP"
                                + " WHERE "
                                + kind.key()
                                + "=? AND versione_modifica=?")) {
            query.setBoolean(1, active);
            query.setLong(2, expected.id());
            query.setInt(3, expected.revision());
            if (query.executeUpdate() != 1) throw new Conflict();
        }
    }

    public void delete(Entry expected) throws SQLException {
        CatalogKind kind = expected.kind();
        try (var query =
                connection.prepareStatement(
                        "DELETE FROM " + kind.historyTable() + " WHERE " + kind.key() + "=?")) {
            query.setLong(1, expected.id());
            query.executeUpdate();
        }
        try (var query =
                connection.prepareStatement(
                        "DELETE FROM "
                                + kind.table()
                                + " WHERE "
                                + kind.key()
                                + "=? AND versione_modifica=?")) {
            query.setLong(1, expected.id());
            query.setInt(2, expected.revision());
            if (query.executeUpdate() != 1) throw new Conflict();
        }
    }

    private static List<Object> values(Draft draft) {
        return switch (draft) {
            case LineDraft d ->
                    Arrays.asList(
                            d.code(),
                            d.name(),
                            d.technology(),
                            d.material(),
                            d.notes(),
                            d.active());
            case ProductDraft d ->
                    Arrays.asList(d.code(), d.name(), d.lineId(), d.color(), d.notes(), d.active());
            case ArticleDraft d ->
                    Arrays.asList(
                            d.code(),
                            d.name(),
                            d.productId(),
                            d.diameter(),
                            d.height(),
                            d.notes(),
                            d.active());
        };
    }

    private static String from(CatalogKind kind) {
        return switch (kind) {
            case LINES -> " FROM TON.LINEE b";
            case PRODUCTS -> " FROM TON.PRODOTTI b LEFT JOIN TON.LINEE l ON l.linea_id=b.linea_id";
            case ARTICLES ->
                    " FROM TON.ARTICOLI b JOIN TON.PRODOTTI p ON p.prodotto_id=b.prodotto_id LEFT"
                            + " JOIN TON.LINEE l ON l.linea_id=p.linea_id LEFT JOIN"
                            + " TON.CONFIGURAZIONI_ARTICOLO cfg ON"
                            + " cfg.configurazione_id=b.configurazione_predefinita_id";
        };
    }

    private static String select(CatalogKind kind) {
        String derived =
                switch (kind) {
                    case LINES ->
                            """
,(SELECT COUNT(*) FROM TON.PRODOTTI c WHERE c.linea_id=b.linea_id) AS child_count
,((SELECT COUNT(*) FROM TON.PRODOTTI c WHERE c.linea_id=b.linea_id)
 +(SELECT COUNT(*) FROM TON.LINEE_PRODOTTI c WHERE c.linea_id=b.linea_id)
 +(SELECT COUNT(*) FROM TON.PRODOTTI_REVISIONI c WHERE c.linea_id=b.linea_id)
 +(SELECT COUNT(*) FROM TON.ARTICOLI_REVISIONI c WHERE c.linea_id=b.linea_id)
 +(SELECT COUNT(*) FROM TON.PRODUZIONI c WHERE c.linea_id=b.linea_id)) AS uses_count
""";
                    case PRODUCTS ->
                            """
,l.codice AS line_code,l.nome AS line_name,l.attivo AS line_active
,(SELECT COUNT(*) FROM TON.ARTICOLI c WHERE c.prodotto_id=b.prodotto_id) AS child_count
,((SELECT COUNT(*) FROM TON.ARTICOLI c WHERE c.prodotto_id=b.prodotto_id)
 +(SELECT COUNT(*) FROM TON.ARTICOLI_REVISIONI c WHERE c.prodotto_id=b.prodotto_id)
 +(SELECT COUNT(*) FROM TON.COMPOSIZIONI c WHERE c.prodotto_id=b.prodotto_id)
 +(SELECT COUNT(*) FROM TON.LINEE_PRODOTTI c WHERE c.prodotto_id=b.prodotto_id)) AS uses_count
""";
                    case ARTICLES ->
                            """
,p.codice AS product_code,p.descrizione AS product_name,p.attivo AS product_active
,p.linea_id AS parent_line_id,p.colore_commerciale AS parent_color
,l.codice AS line_code,l.attivo AS line_active
,cfg.numero_versione AS spec_number,cfg.stato AS spec_state,cfg.articolo_revisione AS spec_recorded
,(SELECT COUNT(*) FROM TON.CONFIGURAZIONI_ARTICOLO c WHERE c.articolo_id=b.articolo_id) AS uses_count
""";
                };
        return "SELECT b.*" + derived + from(kind);
    }

    private static String where(CatalogKind kind, Filter filter, List<Object> values) {
        String name = kind == CatalogKind.LINES ? "b.nome" : "b.descrizione";
        String search =
                filter.search()
                        .toUpperCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
        String where =
                " WHERE (UPPER(b.codice) LIKE ? ESCAPE '\\' OR UPPER(COALESCE("
                        + name
                        + ",'')) LIKE ? ESCAPE '\\')";
        values.add("%" + search + "%");
        values.add("%" + search + "%");
        if (!filter.archived()) where += " AND b.attivo=TRUE";
        if (filter.lineId() != null && kind != CatalogKind.LINES) {
            where += " AND " + (kind == CatalogKind.PRODUCTS ? "b.linea_id" : "p.linea_id") + "=?";
            values.add(filter.lineId());
        }
        if (kind == CatalogKind.ARTICLES) {
            if (filter.productId() != null) {
                where += " AND b.prodotto_id=?";
                values.add(filter.productId());
            }
            if (filter.diameter() != null) {
                where += " AND b.diametro_mm=?";
                values.add(filter.diameter());
            }
            if (filter.height() != null) {
                where += " AND b.altezza_mm=?";
                values.add(filter.height());
            }
        }
        return where;
    }

    private static Entry map(CatalogKind kind, ResultSet rows) throws SQLException {
        Metadata meta =
                new Metadata(
                        rows.getLong(kind.key()),
                        rows.getString("codice"),
                        rows.getString(kind == CatalogKind.LINES ? "nome" : "descrizione"),
                        rows.getString("note"),
                        rows.getBoolean("attivo"),
                        rows.getInt("versione_modifica"),
                        rows.getTimestamp("creato_il").toLocalDateTime(),
                        rows.getTimestamp("aggiornato_il").toLocalDateTime());
        boolean used = rows.getLong("uses_count") > 0;
        return switch (kind) {
            case LINES ->
                    new Line(
                            meta,
                            rows.getString("tecnologia"),
                            rows.getString("materia_prima"),
                            rows.getInt("child_count"),
                            used);
            case PRODUCTS -> {
                Long id = (Long) rows.getObject("linea_id");
                Choice line =
                        id == null
                                ? null
                                : new Choice(
                                        id,
                                        rows.getString("line_code"),
                                        rows.getString("line_name"),
                                        rows.getBoolean("line_active"),
                                        id,
                                        rows.getString("line_code"),
                                        null);
                yield new Product(
                        meta,
                        line,
                        rows.getString("colore_commerciale"),
                        rows.getInt("child_count"),
                        used);
            }
            case ARTICLES -> {
                Long line = (Long) rows.getObject("parent_line_id");
                String color = rows.getString("parent_color");
                boolean ready =
                        line != null
                                && color != null
                                && rows.getBoolean("product_active")
                                && rows.getBoolean("line_active");
                Choice product =
                        new Choice(
                                rows.getLong("prodotto_id"),
                                rows.getString("product_code"),
                                rows.getString("product_name"),
                                ready,
                                line,
                                rows.getString("line_code"),
                                color);
                Integer spec = (Integer) rows.getObject("spec_number");
                String label =
                        spec == null
                                ? "Da configurare"
                                : rows.getObject("spec_recorded") == null
                                        ? "Specifica precedente · da verificare"
                                        : "v"
                                                + spec
                                                + " · "
                                                + rows.getString("spec_state")
                                                        .toLowerCase(Locale.ITALIAN);
                yield new Article(
                        meta,
                        product,
                        rows.getBigDecimal("diametro_mm").stripTrailingZeros(),
                        rows.getBigDecimal("altezza_mm").stripTrailingZeros(),
                        (Long) rows.getObject("configurazione_predefinita_id"),
                        label,
                        used);
            }
        };
    }

    public long count(String sql, List<Object> parameters) throws SQLException {
        try (var query = connection.prepareStatement(sql)) {
            bind(query, parameters);
            try (var rows = query.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    public static void bind(PreparedStatement query, List<?> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) query.setObject(i + 1, parameters.get(i));
    }
}
