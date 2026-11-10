package com.orodent.ecos.costi.products.repository;

import com.orodent.ecos.costi.products.model.ProductCosts.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Batched catalogue reads. All prices and technical revisions are captured in one read transaction.
 */
public final class ProductCostRepository {
    private static final String USED_RECIPES =
            "(SELECT composizione_id FROM TON.CONFIGURAZIONI_ARTICOLO WHERE stato IN"
                    + " ('APPROVATA','RITIRATA'))";
    private static final String USED_MODELS =
            "(SELECT modello_id FROM TON.CONFIGURAZIONI_ARTICOLO WHERE stato IN"
                    + " ('APPROVATA','RITIRATA'))";
    private final Connection connection;

    public ProductCostRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    public Catalogue load() throws SQLException {
        return load(false);
    }

    public Catalogue load(boolean historical) throws SQLException {
        var products = new ArrayList<Product>();
        query(
                "SELECT"
                    + " p.prodotto_id,p.codice,p.linea_id,l.codice,l.nome,p.colore_commerciale,p.attivo"
                    + " FROM TON.PRODOTTI p LEFT JOIN TON.LINEE l ON l.linea_id=p.linea_id ORDER BY"
                    + " p.codice",
                r ->
                        products.add(
                                new Product(
                                        r.getLong(1),
                                        r.getString(2),
                                        (Long) r.getObject(3),
                                        r.getString(4),
                                        r.getString(5),
                                        r.getString(6),
                                        r.getBoolean(7))));
        var articles = new ArrayList<Article>();
        query(
                "SELECT"
                    + " articolo_id,prodotto_id,codice,diametro_mm,altezza_mm,attivo,configurazione_predefinita_id"
                    + " FROM TON.ARTICOLI ORDER BY prodotto_id,diametro_mm,altezza_mm,codice",
                r ->
                        articles.add(
                                new Article(
                                        r.getLong(1),
                                        r.getLong(2),
                                        r.getString(3),
                                        r.getBigDecimal(4),
                                        r.getBigDecimal(5),
                                        r.getBoolean(6),
                                        (Long) r.getObject(7))));
        var specifications = new HashMap<Long, List<Specification>>();
        query(
                """
SELECT c.configurazione_id,c.articolo_id,c.numero_versione,c.composizione_id,c.modello_id,
       c.versione_calcolo,c.articolo_revisione,COALESCE(h.diametro_mm,a.diametro_mm),COALESCE(h.altezza_mm,a.altezza_mm)
FROM TON.CONFIGURAZIONI_ARTICOLO c JOIN TON.ARTICOLI a ON a.articolo_id=c.articolo_id
LEFT JOIN TON.ARTICOLI_REVISIONI h ON h.articolo_id=c.articolo_id AND h.numero_revisione=c.articolo_revisione
WHERE c.stato IN ('APPROVATA','RITIRATA') ORDER BY c.articolo_id,c.numero_versione DESC
""",
                r ->
                        specifications
                                .computeIfAbsent(r.getLong(2), k -> new ArrayList<>())
                                .add(
                                        new Specification(
                                                r.getLong(1),
                                                r.getLong(2),
                                                r.getInt(3),
                                                r.getLong(4),
                                                r.getLong(5),
                                                r.getString(6),
                                                (Integer) r.getObject(7),
                                                r.getBigDecimal(8),
                                                r.getBigDecimal(9))));
        if (!historical) {
            var approved = new HashSet<Long>();
            query(
                    "SELECT configurazione_id FROM TON.CONFIGURAZIONI_ARTICOLO WHERE"
                            + " stato='APPROVATA'",
                    r -> approved.add(r.getLong(1)));
            specifications.replaceAll(
                    (id, values) ->
                            values.stream().filter(v -> approved.contains(v.id())).toList());
        }
        var ingredients = new HashMap<Long, Map<Integer, List<Ingredient>>>();
        query(
                "SELECT composizione_id,numero_strato FROM TON.COMPOSIZIONE_STRATI WHERE"
                        + " composizione_id IN "
                        + USED_RECIPES
                        + " ORDER BY composizione_id,numero_strato",
                r ->
                        ingredients
                                .computeIfAbsent(r.getLong(1), k -> new TreeMap<>())
                                .put(r.getInt(2), new ArrayList<>()));
        query(
                """
SELECT i.composizione_id,i.numero_strato,p.polvere_id,p.codice,r.numero_versione,i.percentuale_massa
FROM TON.COMPOSIZIONE_INGREDIENTI i JOIN TON.POLVERI_REVISIONI r ON r.polvere_revisione_id=i.polvere_revisione_id
JOIN TON.POLVERI p ON p.polvere_id=r.polvere_id WHERE i.composizione_id IN
"""
                        + USED_RECIPES
                        + " ORDER BY i.composizione_id,i.numero_strato,CASE p.colore WHEN 'BIANCA'"
                        + " THEN 0 WHEN 'GIALLA' THEN 1 WHEN 'ROSA' THEN 2 WHEN 'GRIGIA' THEN 3"
                        + " ELSE 4 END,p.codice",
                r ->
                        ingredients
                                .get(r.getLong(1))
                                .get(r.getInt(2))
                                .add(
                                        new Ingredient(
                                                r.getLong(3),
                                                r.getString(4),
                                                r.getInt(5),
                                                r.getBigDecimal(6))));
        var recipes = new HashMap<Long, Recipe>();
        query(
                "SELECT composizione_id,numero_versione,stato FROM TON.COMPOSIZIONI WHERE"
                        + " composizione_id IN "
                        + USED_RECIPES,
                r ->
                        recipes.put(
                                r.getLong(1),
                                new Recipe(
                                        r.getLong(1),
                                        r.getInt(2),
                                        r.getString(3),
                                        ingredients.getOrDefault(r.getLong(1), Map.of()))));
        var layers = new HashMap<Long, Map<Integer, BigDecimal>>();
        query(
                "SELECT modello_id,numero_strato,percentuale_altezza FROM TON.MODELLO_STRATI WHERE"
                        + " modello_id IN "
                        + USED_MODELS,
                r ->
                        layers.computeIfAbsent(r.getLong(1), k -> new TreeMap<>())
                                .put(r.getInt(2), r.getBigDecimal(3)));
        var ranges = new HashMap<Long, List<Range>>();
        query(
                "SELECT modello_id,altezza_da_mm,altezza_a_mm,superiore_mm,inferiore_mm FROM"
                        + " TON.MODELLO_SOVRAMATERIALI WHERE modello_id IN "
                        + USED_MODELS
                        + " ORDER BY modello_id,altezza_da_mm",
                r ->
                        ranges.computeIfAbsent(r.getLong(1), k -> new ArrayList<>())
                                .add(
                                        new Range(
                                                r.getBigDecimal(2),
                                                r.getBigDecimal(3),
                                                r.getBigDecimal(4),
                                                r.getBigDecimal(5))));
        var models = new HashMap<Long, Model>();
        query(
                "SELECT"
                    + " modello_id,codice,numero_versione,stato,grammi_per_mm,sovramateriale_superiore_mm,sovramateriale_inferiore_mm"
                    + " FROM TON.MODELLI_DISCO WHERE modello_id IN "
                        + USED_MODELS,
                r ->
                        models.put(
                                r.getLong(1),
                                new Model(
                                        r.getLong(1),
                                        r.getString(2),
                                        r.getInt(3),
                                        r.getString(4),
                                        r.getBigDecimal(5),
                                        r.getBigDecimal(6),
                                        r.getBigDecimal(7),
                                        layers.getOrDefault(r.getLong(1), Map.of()),
                                        ranges.getOrDefault(r.getLong(1), List.of()))));

        var lotTotals = new HashMap<Long, BigDecimal[]>();
        query(
                """
SELECT l.prezzo_id,l.quantita_kg,l.prezzo_kg FROM COSTI.POLVERI_LOTTI l JOIN COSTI.POLVERI_PREZZI p ON p.prezzo_id=l.prezzo_id
WHERE p.numero_versione=(SELECT MAX(v.numero_versione) FROM COSTI.POLVERI_PREZZI v WHERE v.polvere_id=p.polvere_id)
""",
                r -> {
                    BigDecimal[] total =
                            lotTotals.computeIfAbsent(
                                    r.getLong(1),
                                    k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
                    total[0] = total[0].add(r.getBigDecimal(2));
                    total[1] = total[1].add(r.getBigDecimal(2).multiply(r.getBigDecimal(3)));
                });
        var prices = new HashMap<Long, PowderPrice>();
        query(
                """
SELECT p.prezzo_id,p.polvere_id,p.numero_versione,p.data_riferimento,p.modalita,CASE WHEN p.modello_dati=2 THEN p.prezzo_riferimento ELSE p.prezzo_semplice END,p.modello_dati FROM COSTI.POLVERI_PREZZI p
WHERE p.numero_versione=(SELECT MAX(v.numero_versione) FROM COSTI.POLVERI_PREZZI v WHERE v.polvere_id=p.polvere_id)
""",
                r -> {
                    BigDecimal price = r.getBigDecimal(6);
                    if (r.getInt(7) == 1 && "AVANZATA".equals(r.getString(5))) {
                        var totals = lotTotals.get(r.getLong(1));
                        price =
                                totals == null || totals[0].signum() == 0
                                        ? null
                                        : totals[1].divide(totals[0], 8, RoundingMode.HALF_UP);
                    }
                    prices.put(
                            r.getLong(2),
                            new PowderPrice(
                                    r.getLong(2),
                                    r.getInt(3),
                                    r.getDate(4).toLocalDate(),
                                    r.getInt(7) == 2 ? "RIFERIMENTO" : r.getString(5),
                                    price));
                });
        return new Catalogue(
                Instant.now(),
                products,
                articles,
                specifications,
                recipes,
                models,
                prices,
                parameters());
    }

    private Map<Long, Parameters> parameters() throws SQLException {
        var overrides = new HashMap<Long, Map<Long, BigDecimal>>();
        query(
                """
SELECT a.parametro_id,a.articolo_id,a.trasformazione_eur FROM COSTI.ARTICOLI_TRASFORMAZIONE a
JOIN COSTI.PRODOTTI_PARAMETRI p ON p.parametro_id=a.parametro_id
WHERE p.numero_versione=(SELECT MAX(v.numero_versione) FROM COSTI.PRODOTTI_PARAMETRI v WHERE v.prodotto_id=p.prodotto_id)
""",
                r ->
                        overrides
                                .computeIfAbsent(r.getLong(1), k -> new HashMap<>())
                                .put(r.getLong(2), r.getBigDecimal(3)));
        var parameters = new HashMap<Long, Parameters>();
        query(
                """
SELECT p.parametro_id,p.prodotto_id,p.numero_versione,p.trasformazione_eur FROM COSTI.PRODOTTI_PARAMETRI p
WHERE p.numero_versione=(SELECT MAX(v.numero_versione) FROM COSTI.PRODOTTI_PARAMETRI v WHERE v.prodotto_id=p.prodotto_id)
""",
                r ->
                        parameters.put(
                                r.getLong(2),
                                new Parameters(
                                        r.getInt(3),
                                        r.getBigDecimal(4),
                                        overrides.getOrDefault(r.getLong(1), Map.of()))));
        return parameters;
    }

    public Parameters save(long productId, Parameters expected) throws SQLException {
        // Same parent-row lock convention as TON catalogues. It changes no values or history,
        // and serializes first revisions before touching the economic snapshot/index tables.
        try (var q =
                connection.prepareStatement(
                        "UPDATE TON.PRODOTTI SET versione_modifica=versione_modifica WHERE"
                                + " prodotto_id=?")) {
            q.setLong(1, productId);
            if (q.executeUpdate() != 1)
                throw new IllegalArgumentException(
                        "Il prodotto non esiste più. Ricarica il catalogo.");
        }
        int currentVersion;
        try (var q =
                connection.prepareStatement(
                        "SELECT COALESCE(MAX(numero_versione),0) FROM COSTI.PRODOTTI_PARAMETRI"
                                + " WHERE prodotto_id=?")) {
            q.setLong(1, productId);
            try (var r = q.executeQuery()) {
                r.next();
                currentVersion = r.getInt(1);
            }
        }
        if (currentVersion != expected.version()) throw new Conflict();
        if (expected.version() == Integer.MAX_VALUE)
            throw new IllegalArgumentException("Limite revisioni raggiunto.");
        for (long article : expected.overrides().keySet()) {
            try (var q =
                    connection.prepareStatement(
                            "SELECT prodotto_id FROM TON.ARTICOLI WHERE articolo_id=?")) {
                q.setLong(1, article);
                try (var r = q.executeQuery()) {
                    if (!r.next() || r.getLong(1) != productId)
                        throw new IllegalArgumentException(
                                "L'eccezione di trasformazione appartiene a un altro prodotto.");
                }
            }
        }
        long id;
        try (var q =
                connection.prepareStatement(
                        "INSERT INTO"
                            + " COSTI.PRODOTTI_PARAMETRI(prodotto_id,numero_versione,trasformazione_eur)"
                            + " VALUES (?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            q.setLong(1, productId);
            q.setInt(2, expected.version() + 1);
            q.setBigDecimal(3, expected.commonCost());
            try {
                q.executeUpdate();
            } catch (SQLException e) {
                if ("23505".equals(e.getSQLState())) throw new Conflict();
                throw e;
            }
            try (var r = q.getGeneratedKeys()) {
                if (!r.next()) throw new SQLException("Revisione non disponibile.");
                id = r.getLong(1);
            }
        }
        try (var q =
                connection.prepareStatement(
                        "INSERT INTO"
                            + " COSTI.ARTICOLI_TRASFORMAZIONE(parametro_id,articolo_id,trasformazione_eur)"
                            + " VALUES (?,?,?)")) {
            for (var entry : expected.overrides().entrySet()) {
                q.setLong(1, id);
                q.setLong(2, entry.getKey());
                q.setBigDecimal(3, entry.getValue());
                q.addBatch();
            }
            if (!expected.overrides().isEmpty()) q.executeBatch();
        }
        return new Parameters(expected.version() + 1, expected.commonCost(), expected.overrides());
    }

    private void query(String sql, RowConsumer row) throws SQLException {
        try (var q = connection.prepareStatement(sql);
                var r = q.executeQuery()) {
            while (r.next()) row.accept(r);
        }
    }

    @FunctionalInterface
    private interface RowConsumer {
        void accept(ResultSet row) throws SQLException;
    }
}
