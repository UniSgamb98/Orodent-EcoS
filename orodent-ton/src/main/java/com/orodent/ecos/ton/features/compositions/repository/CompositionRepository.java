package com.orodent.ecos.ton.features.compositions.repository;

import static com.orodent.ecos.ton.features.compositions.model.Compositions.*;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** SQL on the caller's connection. Recipe writes are claimed through their product aggregate. */
public final class CompositionRepository {
    private final Connection connection;

    public CompositionRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    private static String pattern(String value) {
        return "%"
                + text(value)
                        .toUpperCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_")
                + "%";
    }

    private static String marks(int n) {
        return String.join(",", Collections.nCopies(n, "?"));
    }

    private static void ids(PreparedStatement q, Collection<Long> ids) throws SQLException {
        int i = 1;
        for (long id : ids) q.setLong(i++, id);
    }

    public Page<Summary> page(String search, Filter filter, boolean archived, int offset, int limit)
            throws SQLException {
        String from =
                " FROM TON.COMPOSIZIONI c JOIN TON.PRODOTTI p ON p.prodotto_id=c.prodotto_id LEFT"
                        + " JOIN TON.LINEE l ON l.linea_id=p.linea_id";
        String where =
                " WHERE c.numero_versione=(SELECT MAX(x.numero_versione) FROM TON.COMPOSIZIONI x"
                    + " WHERE x.prodotto_id=c.prodotto_id) AND (UPPER(p.codice) LIKE ? ESCAPE '\\'"
                    + " OR UPPER(p.descrizione) LIKE ? ESCAPE '\\' OR UPPER(l.codice) LIKE ? ESCAPE"
                    + " '\\' OR UPPER(l.nome) LIKE ? ESCAPE '\\' OR UPPER(p.colore_commerciale)"
                    + " LIKE ? ESCAPE '\\')";
        if (!archived) where += " AND p.attivo=TRUE AND l.attivo=TRUE";
        if (filter != Filter.ALL)
            where +=
                    " AND EXISTS (SELECT 1 FROM TON.COMPOSIZIONI x WHERE"
                            + " x.prodotto_id=c.prodotto_id AND x.stato='"
                            + (filter == Filter.DRAFT ? "BOZZA" : "APPROVATA")
                            + "')";
        long total;
        try (var q = connection.prepareStatement("SELECT COUNT(*)" + from + where)) {
            for (int i = 1; i <= 5; i++) q.setString(i, pattern(search));
            try (var r = q.executeQuery()) {
                r.next();
                total = r.getLong(1);
            }
        }
        var rows = new ArrayList<Summary>();
        try (var q =
                connection.prepareStatement(
                        "SELECT"
                            + " c.composizione_id,p.prodotto_id,p.codice,p.descrizione,l.codice,p.colore_commerciale,(SELECT"
                            + " COUNT(*) FROM TON.COMPOSIZIONE_STRATI s WHERE"
                            + " s.composizione_id=c.composizione_id),(SELECT MAX(x.numero_versione)"
                            + " FROM TON.COMPOSIZIONI x WHERE x.prodotto_id=c.prodotto_id AND"
                            + " x.stato='APPROVATA'),(SELECT MAX(x.numero_versione) FROM"
                            + " TON.COMPOSIZIONI x WHERE x.prodotto_id=c.prodotto_id AND"
                            + " x.stato='BOZZA'),CASE WHEN p.attivo=TRUE AND l.attivo=TRUE THEN 1"
                            + " ELSE 0 END"
                                + from
                                + where
                                + " ORDER BY p.codice OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
            for (int i = 1; i <= 5; i++) q.setString(i, pattern(search));
            q.setInt(6, offset);
            q.setInt(7, limit);
            try (var r = q.executeQuery()) {
                while (r.next())
                    rows.add(
                            new Summary(
                                    r.getLong(1),
                                    r.getLong(2),
                                    r.getString(3),
                                    text(r.getString(4)),
                                    text(r.getString(5)),
                                    r.getString(6),
                                    r.getInt(7),
                                    (Integer) r.getObject(8),
                                    (Integer) r.getObject(9),
                                    r.getInt(10) == 1));
            }
        }
        return new Page<>(rows, total, offset, limit);
    }

    public long parent(long id) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "SELECT prodotto_id FROM TON.COMPOSIZIONI WHERE composizione_id=?")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw new Conflict();
                return r.getLong(1);
            }
        }
    }

    public ProductRef product(long id) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "SELECT"
                            + " p.codice,p.descrizione,p.linea_id,l.codice,l.nome,p.colore_commerciale,CASE"
                            + " WHEN p.attivo=TRUE AND l.attivo=TRUE AND p.colore_commerciale IS"
                            + " NOT NULL THEN 1 ELSE 0 END FROM TON.PRODOTTI p LEFT JOIN TON.LINEE"
                            + " l ON l.linea_id=p.linea_id WHERE p.prodotto_id=?")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw invalid("Il prodotto non è più disponibile.");
                Long line = (Long) r.getObject(3);
                return new ProductRef(
                        id,
                        r.getString(1),
                        text(r.getString(2)),
                        line,
                        text(r.getString(4)),
                        text(r.getString(5)),
                        r.getString(6),
                        r.getInt(7) == 1);
            }
        }
    }

    public Revision load(long id) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "SELECT * FROM TON.COMPOSIZIONI WHERE composizione_id=?")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw new Conflict();
                return new Revision(
                        id,
                        r.getInt("numero_versione"),
                        State.valueOf(r.getString("stato")),
                        r.getInt("versione_modifica"),
                        r.getTimestamp("aggiornato_il").toLocalDateTime(),
                        new Draft(r.getLong("prodotto_id"), r.getString("note"), layers(id)));
            }
        }
    }

    private List<Layer> layers(long id) throws SQLException {
        var ingredients = new HashMap<Integer, List<Ingredient>>();
        try (var q =
                connection.prepareStatement(
                        "SELECT numero_strato,polvere_revisione_id,percentuale_massa FROM"
                                + " TON.COMPOSIZIONE_INGREDIENTI WHERE composizione_id=? ORDER BY"
                                + " numero_strato,polvere_revisione_id")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                while (r.next())
                    ingredients
                            .computeIfAbsent(r.getInt(1), k -> new ArrayList<>())
                            .add(new Ingredient(r.getLong(2), r.getBigDecimal(3)));
            }
        }
        var result = new ArrayList<Layer>();
        try (var q =
                connection.prepareStatement(
                        "SELECT numero_strato,nome FROM TON.COMPOSIZIONE_STRATI WHERE"
                                + " composizione_id=? ORDER BY numero_strato")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                while (r.next())
                    result.add(
                            new Layer(
                                    r.getInt(1),
                                    r.getString(2),
                                    ingredients.getOrDefault(r.getInt(1), List.of())));
            }
        }
        return result;
    }

    public Map<Long, PowderRef> powders(Collection<Long> ids) throws SQLException {
        if (ids.isEmpty()) return Map.of();
        var result = new HashMap<Long, PowderRef>();
        try (var q =
                connection.prepareStatement(
                        "SELECT"
                            + " r.polvere_revisione_id,p.polvere_id,p.codice,p.nome,p.colore,r.classe_ittria_y,r.numero_versione,r.stato,p.attivo"
                            + " FROM TON.POLVERI_REVISIONI r JOIN TON.POLVERI p ON"
                            + " p.polvere_id=r.polvere_id WHERE r.polvere_revisione_id IN ("
                                + marks(ids.size())
                                + ") ORDER BY p.polvere_id,r.polvere_revisione_id")) {
            ids(q, ids);
            try (var r = q.executeQuery()) {
                while (r.next()) {
                    long id = r.getLong(1);
                    result.put(
                            id,
                            new PowderRef(
                                    id,
                                    r.getLong(2),
                                    r.getString(3),
                                    r.getString(4),
                                    r.getString(5),
                                    (Integer) r.getObject(6),
                                    r.getInt(7),
                                    State.valueOf(r.getString(8)),
                                    r.getBoolean(9),
                                    Map.of()));
                }
            }
        }
        var oxides = new HashMap<Long, Map<String, BigDecimal>>();
        try (var q =
                connection.prepareStatement(
                        "SELECT polvere_revisione_id,ossido_codice,percentuale_massa FROM"
                                + " TON.POLVERE_OSSIDI WHERE polvere_revisione_id IN ("
                                + marks(ids.size())
                                + ") ORDER BY polvere_revisione_id,ossido_codice")) {
            ids(q, ids);
            try (var r = q.executeQuery()) {
                while (r.next())
                    oxides.computeIfAbsent(r.getLong(1), k -> new TreeMap<>())
                            .put(r.getString(2), r.getBigDecimal(3).stripTrailingZeros());
            }
        }
        var enriched = new HashMap<Long, PowderRef>();
        for (PowderRef p : result.values())
            enriched.put(
                    p.id(),
                    new PowderRef(
                            p.id(),
                            p.powderId(),
                            p.code(),
                            p.name(),
                            p.color(),
                            p.yttria(),
                            p.number(),
                            p.state(),
                            p.active(),
                            oxides.getOrDefault(p.id(), Map.of())));
        return enriched;
    }

    public Page<PowderRef> choices(String search, int offset, int limit) throws SQLException {
        String from =
                " FROM TON.POLVERI_REVISIONI r JOIN TON.POLVERI p ON p.polvere_id=r.polvere_id";
        String where =
                " WHERE p.attivo=TRUE AND r.stato='APPROVATA' AND (UPPER(p.codice) LIKE ? ESCAPE"
                    + " '\\' OR UPPER(p.nome) LIKE ? ESCAPE '\\')";
        long total;
        try (var q = connection.prepareStatement("SELECT COUNT(*)" + from + where)) {
            q.setString(1, pattern(search));
            q.setString(2, pattern(search));
            try (var r = q.executeQuery()) {
                r.next();
                total = r.getLong(1);
            }
        }
        var ids = new ArrayList<Long>();
        try (var q =
                connection.prepareStatement(
                        "SELECT r.polvere_revisione_id"
                                + from
                                + where
                                + " ORDER BY CASE p.colore WHEN 'BIANCA' THEN 0 WHEN 'GIALLA' THEN"
                                + " 1 WHEN 'ROSA' THEN 2 WHEN 'GRIGIA' THEN 3 ELSE 4"
                                + " END,p.codice,r.numero_versione DESC OFFSET ? ROWS FETCH NEXT ?"
                                + " ROWS ONLY")) {
            q.setString(1, pattern(search));
            q.setString(2, pattern(search));
            q.setInt(3, offset);
            q.setInt(4, limit);
            try (var r = q.executeQuery()) {
                while (r.next()) ids.add(r.getLong(1));
            }
        }
        Map<Long, PowderRef> loaded = powders(ids);
        var rows =
                ids.stream()
                        .map(loaded::get)
                        .filter(Objects::nonNull)
                        .filter(PowderRef::usable)
                        .toList();
        return new Page<>(rows, total, offset, limit);
    }

    public void lockPowders(Collection<Long> revisions) throws SQLException {
        if (revisions.isEmpty()) return;
        var parents = new TreeSet<Long>();
        try (var q =
                connection.prepareStatement(
                        "SELECT polvere_id FROM TON.POLVERI_REVISIONI WHERE polvere_revisione_id IN"
                                + " ("
                                + marks(revisions.size())
                                + ")")) {
            ids(q, revisions);
            try (var r = q.executeQuery()) {
                while (r.next()) parents.add(r.getLong(1));
            }
        }
        for (long id : parents) lock("POLVERI", "polvere_id", id);
    }

    public void lock(long id) throws SQLException {
        lock("COMPOSIZIONI", "composizione_id", id);
    }

    private void lock(String table, String key, long id) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "UPDATE TON."
                                + table
                                + " SET versione_modifica=versione_modifica WHERE "
                                + key
                                + "=?")) {
            q.setLong(1, id);
            if (q.executeUpdate() != 1) throw new Conflict();
        }
    }

    public void readProduct(long id) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "SELECT prodotto_id FROM TON.PRODOTTI WHERE prodotto_id=?")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw new Conflict();
            }
        }
    }

    public void readLine(long id) throws SQLException {
        try (var q =
                connection.prepareStatement("SELECT linea_id FROM TON.LINEE WHERE linea_id=?")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                if (!r.next()) throw new Conflict();
            }
        }
    }

    public int maximum(long product) throws SQLException {
        return (int)
                count(
                        "SELECT COALESCE(MAX(numero_versione),0) FROM TON.COMPOSIZIONI WHERE"
                                + " prodotto_id=?",
                        product);
    }

    public boolean hasDraft(long product) throws SQLException {
        return count(
                        "SELECT COUNT(*) FROM TON.COMPOSIZIONI WHERE prodotto_id=? AND"
                                + " stato='BOZZA'",
                        product)
                > 0;
    }

    public boolean used(long id) throws SQLException {
        return count("SELECT COUNT(*) FROM TON.CONFIGURAZIONI_ARTICOLO WHERE composizione_id=?", id)
                > 0;
    }

    public Page<Version> versions(long product, int offset, int limit) throws SQLException {
        long total = count("SELECT COUNT(*) FROM TON.COMPOSIZIONI WHERE prodotto_id=?", product);
        var rows = new ArrayList<Version>();
        try (var q =
                connection.prepareStatement(
                        "SELECT composizione_id,numero_versione,stato,aggiornato_il FROM"
                            + " TON.COMPOSIZIONI WHERE prodotto_id=? ORDER BY numero_versione DESC"
                            + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
            q.setLong(1, product);
            q.setInt(2, offset);
            q.setInt(3, limit);
            try (var r = q.executeQuery()) {
                while (r.next())
                    rows.add(
                            new Version(
                                    r.getLong(1),
                                    r.getInt(2),
                                    State.valueOf(r.getString(3)),
                                    r.getTimestamp(4).toLocalDateTime()));
            }
        }
        return new Page<>(rows, total, offset, limit);
    }

    public Page<Usage> usages(long id, int offset, int limit) throws SQLException {
        long total =
                count(
                        "SELECT COUNT(*) FROM TON.CONFIGURAZIONI_ARTICOLO WHERE composizione_id=?",
                        id);
        var rows = new ArrayList<Usage>();
        try (var q =
                connection.prepareStatement(
                        "SELECT"
                            + " a.articolo_id,a.codice,a.diametro_mm,a.altezza_mm,c.numero_versione,c.stato,m.codice,m.numero_versione,CASE"
                            + " WHEN a.configurazione_predefinita_id=c.configurazione_id THEN 1"
                            + " ELSE 0 END FROM TON.CONFIGURAZIONI_ARTICOLO c JOIN TON.ARTICOLI a"
                            + " ON a.articolo_id=c.articolo_id JOIN TON.MODELLI_DISCO m ON"
                            + " m.modello_id=c.modello_id WHERE c.composizione_id=? ORDER BY"
                            + " a.codice,c.numero_versione DESC OFFSET ? ROWS FETCH NEXT ? ROWS"
                            + " ONLY")) {
            q.setLong(1, id);
            q.setInt(2, offset);
            q.setInt(3, limit);
            try (var r = q.executeQuery()) {
                while (r.next())
                    rows.add(
                            new Usage(
                                    r.getLong(1),
                                    r.getString(2),
                                    r.getBigDecimal(3),
                                    r.getBigDecimal(4),
                                    r.getInt(5),
                                    r.getString(6),
                                    r.getString(7),
                                    r.getInt(8),
                                    r.getInt(9) == 1));
            }
        }
        return new Page<>(rows, total, offset, limit);
    }

    public Page<ModelMatch> models(
            List<Integer> numbers, String search, boolean matching, int offset, int limit)
            throws SQLException {
        boolean consecutive = !numbers.isEmpty();
        for (int i = 0; i < numbers.size(); i++) consecutive &= numbers.get(i) == i + 1;
        String match =
                "(SELECT COUNT(*) FROM TON.MODELLO_STRATI s WHERE s.modello_id=m.modello_id)="
                        + numbers.size()
                        + " AND NOT EXISTS (SELECT 1 FROM TON.MODELLO_STRATI s WHERE"
                        + " s.modello_id=m.modello_id AND (s.numero_strato<1 OR s.numero_strato>"
                        + numbers.size()
                        + "))";
        String where =
                " WHERE m.stato='APPROVATA' AND m.codice LIKE ? ESCAPE '\\'"
                        + (matching ? (consecutive ? " AND " + match : " AND 1=0") : "");
        long total;
        try (var q =
                connection.prepareStatement("SELECT COUNT(*) FROM TON.MODELLI_DISCO m" + where)) {
            q.setString(1, pattern(search));
            try (var r = q.executeQuery()) {
                r.next();
                total = r.getLong(1);
            }
        }
        record Row(long id, String code, int version, BigDecimal diameter) {}
        var rows = new ArrayList<Row>();
        try (var q =
                connection.prepareStatement(
                        "SELECT m.modello_id,m.codice,m.numero_versione,m.diametro_pressatura_mm"
                                + " FROM TON.MODELLI_DISCO m"
                                + where
                                + " ORDER BY m.codice,m.numero_versione DESC OFFSET ? ROWS FETCH"
                                + " NEXT ? ROWS ONLY")) {
            q.setString(1, pattern(search));
            q.setInt(2, offset);
            q.setInt(3, limit);
            try (var r = q.executeQuery()) {
                while (r.next())
                    rows.add(
                            new Row(r.getLong(1), r.getString(2), r.getInt(3), r.getBigDecimal(4)));
            }
        }
        var layers = new HashMap<Long, List<Integer>>();
        if (!rows.isEmpty())
            try (var q =
                    connection.prepareStatement(
                            "SELECT modello_id,numero_strato FROM TON.MODELLO_STRATI WHERE"
                                    + " modello_id IN ("
                                    + marks(rows.size())
                                    + ") ORDER BY modello_id,numero_strato")) {
                ids(q, rows.stream().map(Row::id).toList());
                try (var r = q.executeQuery()) {
                    while (r.next())
                        layers.computeIfAbsent(r.getLong(1), k -> new ArrayList<>())
                                .add(r.getInt(2));
                }
            }
        var result = new ArrayList<ModelMatch>();
        for (Row row : rows) {
            var order = layers.getOrDefault(row.id(), List.of());
            result.add(
                    new ModelMatch(
                            row.id(),
                            row.code(),
                            row.version(),
                            row.diameter(),
                            order,
                            order.equals(numbers)));
        }
        return new Page<>(result, total, offset, limit);
    }

    public long insert(Draft d, int number, boolean approve) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "INSERT INTO"
                            + " TON.COMPOSIZIONI(prodotto_id,numero_versione,note,stato,approvato_il)"
                            + " VALUES (?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            q.setLong(1, d.productId());
            q.setInt(2, number);
            q.setString(3, d.notes());
            q.setString(4, approve ? "APPROVATA" : "BOZZA");
            q.setTimestamp(5, approve ? new Timestamp(System.currentTimeMillis()) : null);
            q.executeUpdate();
            try (var r = q.getGeneratedKeys()) {
                r.next();
                long id = r.getLong(1);
                insertChildren(id, d);
                return id;
            }
        }
    }

    public void update(Revision old, Draft d, boolean approve) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "UPDATE TON.COMPOSIZIONI SET"
                            + " note=?,stato=?,approvato_il=?,versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP"
                            + " WHERE composizione_id=? AND versione_modifica=? AND"
                            + " stato='BOZZA'")) {
            q.setString(1, d.notes());
            q.setString(2, approve ? "APPROVATA" : "BOZZA");
            q.setTimestamp(3, approve ? new Timestamp(System.currentTimeMillis()) : null);
            q.setLong(4, old.id());
            q.setInt(5, old.editVersion());
            if (q.executeUpdate() != 1) throw new Conflict();
        }
        deleteChildren(old.id());
        insertChildren(old.id(), d);
    }

    public void retire(Revision old) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "UPDATE TON.COMPOSIZIONI SET"
                            + " stato='RITIRATA',versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP"
                            + " WHERE composizione_id=? AND versione_modifica=? AND"
                            + " stato='APPROVATA'")) {
            q.setLong(1, old.id());
            q.setInt(2, old.editVersion());
            if (q.executeUpdate() != 1) throw new Conflict();
        }
    }

    public void delete(Revision old) throws SQLException {
        deleteChildren(old.id());
        try (var q =
                connection.prepareStatement(
                        "DELETE FROM TON.COMPOSIZIONI WHERE composizione_id=? AND"
                                + " versione_modifica=? AND stato='BOZZA'")) {
            q.setLong(1, old.id());
            q.setInt(2, old.editVersion());
            if (q.executeUpdate() != 1) throw new Conflict();
        }
    }

    private void deleteChildren(long id) throws SQLException {
        for (String table : List.of("COMPOSIZIONE_INGREDIENTI", "COMPOSIZIONE_STRATI"))
            try (var q =
                    connection.prepareStatement(
                            "DELETE FROM TON." + table + " WHERE composizione_id=?")) {
                q.setLong(1, id);
                q.executeUpdate();
            }
    }

    private void insertChildren(long id, Draft d) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "INSERT INTO TON.COMPOSIZIONE_STRATI(composizione_id,numero_strato,nome)"
                                + " VALUES (?,?,?)")) {
            for (Layer layer : d.layers()) {
                q.setLong(1, id);
                q.setInt(2, layer.number());
                q.setString(3, layer.name());
                q.addBatch();
            }
            q.executeBatch();
        }
        try (var q =
                connection.prepareStatement(
                        "INSERT INTO"
                            + " TON.COMPOSIZIONE_INGREDIENTI(composizione_id,numero_strato,polvere_revisione_id,percentuale_massa)"
                            + " VALUES (?,?,?,?)")) {
            for (Layer layer : d.layers())
                for (Ingredient i : layer.ingredients()) {
                    q.setLong(1, id);
                    q.setInt(2, layer.number());
                    q.setLong(3, i.powderRevisionId());
                    q.setBigDecimal(4, i.mass());
                    q.addBatch();
                }
            q.executeBatch();
        }
    }

    private long count(String sql, long value) throws SQLException {
        try (var q = connection.prepareStatement(sql)) {
            q.setLong(1, value);
            try (var r = q.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }
}
