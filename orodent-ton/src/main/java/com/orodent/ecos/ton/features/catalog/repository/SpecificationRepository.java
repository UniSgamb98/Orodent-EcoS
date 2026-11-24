package com.orodent.ecos.ton.features.catalog.repository;

import static com.orodent.ecos.ton.features.catalog.model.CatalogModels.invalid;

import com.orodent.ecos.ton.features.catalog.model.CatalogModels.*;

import java.sql.*;
import java.util.*;

/** Versioned production references. Approved content is never updated by this repository. */
public final class SpecificationRepository {
    private final Connection connection;

    public SpecificationRepository(Connection connection) {
        this.connection = connection;
    }

    private static final String SELECT =
            """
SELECT c.*,p.numero_versione AS composition_version,m.codice AS model_code,m.numero_versione AS model_version,
       a.configurazione_predefinita_id AS preferred_id
FROM TON.CONFIGURAZIONI_PRODOTTO c JOIN TON.COMPOSIZIONI p ON p.composizione_id=c.composizione_id
JOIN TON.MODELLI_DISCO m ON m.modello_id=c.modello_id JOIN TON.PRODOTTI a ON a.prodotto_id=c.prodotto_id
""";

    public Specification load(long id) throws SQLException {
        try (var query = connection.prepareStatement(SELECT + " WHERE c.configurazione_id=?")) {
            query.setLong(1, id);
            try (var r = query.executeQuery()) {
                if (!r.next()) throw new SQLException("Versione non trovata.");
                return map(r);
            }
        }
    }

    public SpecificationPage page(
            Product product, int offset, int limit, String compositionSearch, String modelSearch)
            throws SQLException {
        var rows = new ArrayList<Specification>();
        try (var query =
                connection.prepareStatement(
                        SELECT
                                + " WHERE c.prodotto_id=? ORDER BY c.numero_versione DESC OFFSET ?"
                                + " ROWS FETCH NEXT ? ROWS ONLY")) {
            query.setLong(1, product.id());
            query.setInt(2, offset);
            query.setInt(3, limit);
            try (var r = query.executeQuery()) {
                while (r.next()) rows.add(map(r));
            }
        }
        long total =
                count(
                        "SELECT COUNT(*) FROM TON.CONFIGURAZIONI_PRODOTTO WHERE prodotto_id=?",
                        product.id());
        var compositions = new ArrayList<TechnicalChoice>();
        try (var q =
                connection.prepareStatement(
                        "SELECT c.composizione_id,c.numero_versione FROM TON.COMPOSIZIONI c WHERE"
                            + " c.prodotto_id=? AND c.stato='APPROVATA' AND"
                            + " TRIM(CHAR(c.numero_versione)) LIKE ? ORDER BY c.numero_versione"
                            + " DESC FETCH NEXT 50 ROWS ONLY")) {
            q.setLong(1, product.id());
            q.setString(2, "%" + text(compositionSearch) + "%");
            try (var r = q.executeQuery()) {
                while (r.next())
                    compositions.add(
                            new TechnicalChoice(
                                    r.getLong(1), product.code() + " · v" + r.getInt(2)));
            }
        }
        var models = new ArrayList<TechnicalChoice>();
        try (var q =
                connection.prepareStatement(
                        "SELECT modello_id,codice,numero_versione FROM TON.MODELLI_DISCO WHERE"
                            + " stato='APPROVATA' AND UPPER(codice) LIKE ? ESCAPE '\\' ORDER BY"
                            + " codice,numero_versione DESC FETCH NEXT 50 ROWS ONLY")) {
            q.setString(
                    1,
                    "%"
                            + text(modelSearch)
                                    .toUpperCase(Locale.ROOT)
                                    .replace("\\", "\\\\")
                                    .replace("%", "\\%")
                                    .replace("_", "\\_")
                            + "%");
            try (var r = q.executeQuery()) {
                while (r.next())
                    models.add(
                            new TechnicalChoice(
                                    r.getLong(1), r.getString(2) + " · v" + r.getInt(3)));
            }
        }
        return new SpecificationPage(
                rows,
                total,
                offset,
                limit,
                compositions,
                models,
                com.orodent.ecos.database.CubatureRuleVersions.choices(connection));
    }

    public SpecificationDraft resolveCalculation(SpecificationDraft draft) throws SQLException {
        var version =
                com.orodent.ecos.database.CubatureRuleVersions.assignment(
                        connection, draft.calculation(), draft.calculationVersionId());
        return new SpecificationDraft(
                draft.compositionId(),
                draft.modelId(),
                draft.calculation(),
                draft.notes(),
                version.id());
    }

    public long insert(Product product, SpecificationDraft draft) throws SQLException {
        if (count(
                        "SELECT COUNT(*) FROM TON.CONFIGURAZIONI_PRODOTTO WHERE prodotto_id=? AND"
                                + " stato='BOZZA'",
                        product.id())
                > 0)
            throw invalid("Esiste già una bozza: aprila prima di creare un'altra versione.");
        int number =
                Math.addExact(
                        (int)
                                count(
                                        "SELECT COALESCE(MAX(numero_versione),0) FROM"
                                            + " TON.CONFIGURAZIONI_PRODOTTO WHERE prodotto_id=?",
                                        product.id()),
                        1);
        try (var q =
                connection.prepareStatement(
                        "INSERT INTO"
                            + " TON.CONFIGURAZIONI_PRODOTTO(prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo,note,calcolo_versione_id)"
                            + " VALUES (?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            q.setLong(1, product.id());
            q.setInt(2, number);
            q.setLong(3, draft.compositionId());
            q.setLong(4, draft.modelId());
            q.setString(5, draft.calculation());
            q.setString(6, draft.notes());
            q.setLong(
                    7,
                    draft.calculationVersionId() == null
                            ? resolveCalculation(draft).calculationVersionId()
                            : draft.calculationVersionId());
            q.executeUpdate();
            try (var keys = q.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("ID configurazione non disponibile.");
                return keys.getLong(1);
            }
        }
    }

    public void update(
            Specification expected,
            SpecificationDraft draft,
            boolean approve,
            Line line,
            Product product)
            throws SQLException {
        try (var q =
                connection.prepareStatement(
                        """
UPDATE TON.CONFIGURAZIONI_PRODOTTO SET composizione_id=?,modello_id=?,versione_calcolo=?,note=?,
stato=?,approvato_il=%s,linea_id=?,linea_revisione=?,prodotto_revisione=?,
versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP,calcolo_versione_id=?
WHERE configurazione_id=? AND prodotto_id=? AND versione_modifica=? AND stato='BOZZA'
"""
                                .formatted(approve ? "CURRENT_TIMESTAMP" : "NULL"))) {
            q.setLong(1, draft.compositionId());
            q.setLong(2, draft.modelId());
            q.setString(3, draft.calculation());
            q.setString(4, draft.notes());
            q.setString(5, approve ? "APPROVATA" : "BOZZA");
            q.setObject(6, approve ? line.id() : null, Types.BIGINT);
            q.setObject(7, approve ? line.revision() : null, Types.INTEGER);
            q.setObject(8, approve ? product.revision() : null, Types.INTEGER);
            q.setLong(9, draft.calculationVersionId());
            q.setLong(10, expected.id());
            q.setLong(11, product.id());
            q.setInt(12, expected.revision());
            if (q.executeUpdate() != 1) throw new Conflict();
        }
    }

    public void retire(Specification expected) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "UPDATE TON.CONFIGURAZIONI_PRODOTTO SET"
                            + " stato='RITIRATA',versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP"
                            + " WHERE configurazione_id=? AND versione_modifica=? AND"
                            + " stato='APPROVATA'")) {
            q.setLong(1, expected.id());
            q.setInt(2, expected.revision());
            if (q.executeUpdate() != 1) throw new Conflict();
        }
    }

    public void preferred(Product expected, Long id) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "UPDATE TON.PRODOTTI SET"
                            + " configurazione_predefinita_id=?,specifiche_da_verificare=FALSE,versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP"
                            + " WHERE prodotto_id=? AND versione_modifica=?")) {
            q.setObject(1, id, Types.BIGINT);
            q.setLong(2, expected.id());
            q.setInt(3, expected.revision());
            if (q.executeUpdate() != 1) throw new Conflict();
        }
    }

    public void validateTechnical(Product product, SpecificationDraft draft, boolean approving)
            throws SQLException {
        com.orodent.ecos.database.CubatureRuleCatalogue.requireActive(
                connection, draft.calculation());
        lock("COMPOSIZIONI", "composizione_id", draft.compositionId());
        lock("MODELLI_DISCO", "modello_id", draft.modelId());
        try (var q =
                connection.prepareStatement(
                        "SELECT prodotto_id,stato FROM TON.COMPOSIZIONI WHERE composizione_id=?")) {
            q.setLong(1, draft.compositionId());
            try (var r = q.executeQuery()) {
                if (!r.next() || r.getLong(1) != product.id())
                    throw invalid("La composizione non appartiene al prodotto selezionato.");
                if (!"APPROVATA".equals(r.getString(2)))
                    throw invalid("La composizione deve essere approvata.");
            }
        }
        try (var q =
                connection.prepareStatement(
                        "SELECT stato FROM TON.MODELLI_DISCO WHERE modello_id=?")) {
            q.setLong(1, draft.modelId());
            try (var r = q.executeQuery()) {
                if (!r.next() || !"APPROVATA".equals(r.getString(1)))
                    throw invalid("Il modello deve essere approvato.");
            }
        }
        if (!approving) return;
        var model =
                new com.orodent.ecos.ton.features.diskmodels.repository.DiskModelRepository(
                                connection)
                        .load(draft.modelId())
                        .data();
        model.requireApprovable();
        var powders = new ArrayList<Long>();
        try (var q =
                connection.prepareStatement(
                        "SELECT DISTINCT r.polvere_id FROM TON.COMPOSIZIONE_INGREDIENTI i JOIN"
                            + " TON.POLVERI_REVISIONI r ON"
                            + " r.polvere_revisione_id=i.polvere_revisione_id WHERE"
                            + " i.composizione_id=? ORDER BY r.polvere_id")) {
            q.setLong(1, draft.compositionId());
            try (var r = q.executeQuery()) {
                while (r.next()) powders.add(r.getLong(1));
            }
        }
        for (long id : powders)
            try (var q =
                    connection.prepareStatement(
                            "UPDATE TON.POLVERI SET versione_modifica=versione_modifica WHERE"
                                + " polvere_id=?")) {
                q.setLong(1, id);
                q.executeUpdate();
            }
        long layers =
                count(
                        "SELECT COUNT(*) FROM TON.COMPOSIZIONE_STRATI WHERE composizione_id=?",
                        draft.compositionId());
        if (layers == 0
                || layers
                        != count(
                                "SELECT COUNT(*) FROM TON.MODELLO_STRATI WHERE modello_id=?",
                                draft.modelId()))
            throw invalid(
                    "Composizione e modello devono avere lo stesso numero di strati, almeno uno.");
        try (var q =
                connection.prepareStatement(
                        "SELECT COUNT(*) FROM TON.COMPOSIZIONE_STRATI s WHERE s.composizione_id=?"
                            + " AND NOT EXISTS (SELECT 1 FROM TON.MODELLO_STRATI m WHERE"
                            + " m.modello_id=? AND m.numero_strato=s.numero_strato)")) {
            q.setLong(1, draft.compositionId());
            q.setLong(2, draft.modelId());
            try (var r = q.executeQuery()) {
                r.next();
                if (r.getLong(1) > 0)
                    throw invalid(
                            "I numeri degli strati di composizione e modello devono"
                                + " corrispondere.");
            }
        }
        if (count(
                        "SELECT COUNT(*) FROM TON.COMPOSIZIONE_STRATI s WHERE s.composizione_id=?"
                            + " AND COALESCE((SELECT SUM(i.percentuale_massa) FROM"
                            + " TON.COMPOSIZIONE_INGREDIENTI i WHERE"
                            + " i.composizione_id=s.composizione_id AND"
                            + " i.numero_strato=s.numero_strato),0)<>100",
                        draft.compositionId())
                > 0) throw invalid("Gli ingredienti di ogni strato devono totalizzare 100%.");
        if (count(
                        "SELECT COUNT(*) FROM TON.COMPOSIZIONE_INGREDIENTI i JOIN"
                            + " TON.POLVERI_REVISIONI r ON"
                            + " r.polvere_revisione_id=i.polvere_revisione_id JOIN TON.POLVERI p ON"
                            + " p.polvere_id=r.polvere_id WHERE i.composizione_id=? AND"
                            + " (r.stato<>'APPROVATA' OR p.attivo=FALSE)",
                        draft.compositionId())
                > 0)
            throw invalid(
                    "La composizione contiene una polvere archiviata o una versione non"
                        + " approvata.");
        try (var q =
                connection.prepareStatement(
                        "SELECT SUM(percentuale_altezza) FROM TON.MODELLO_STRATI WHERE"
                            + " modello_id=?")) {
            q.setLong(1, draft.modelId());
            try (var r = q.executeQuery()) {
                r.next();
                if (r.getBigDecimal(1) == null
                        || r.getBigDecimal(1).compareTo(java.math.BigDecimal.valueOf(100)) != 0)
                    throw invalid("Gli strati del modello devono totalizzare 100%.");
            }
        }
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
            if (q.executeUpdate() != 1) throw invalid("La specifica selezionata non esiste più.");
        }
    }

    private long count(String sql, long id) throws SQLException {
        return new CatalogRepository(connection).count(sql, List.of(id));
    }

    private static String text(String s) {
        return s == null ? "" : s.strip();
    }

    private static Specification map(ResultSet r) throws SQLException {
        Timestamp date = r.getTimestamp("approvato_il");
        long id = r.getLong("configurazione_id");
        return new Specification(
                id,
                r.getLong("prodotto_id"),
                r.getInt("numero_versione"),
                r.getInt("versione_modifica"),
                r.getString("stato"),
                r.getLong("composizione_id"),
                "Composizione v" + r.getInt("composition_version"),
                r.getLong("modello_id"),
                r.getString("model_code") + " · v" + r.getInt("model_version"),
                r.getString("versione_calcolo"),
                r.getString("note"),
                date == null ? null : date.toLocalDateTime(),
                (Integer) r.getObject("linea_revisione"),
                (Integer) r.getObject("prodotto_revisione"),
                Objects.equals((Long) r.getObject("preferred_id"), id),
                (Long) r.getObject("calcolo_versione_id"));
    }
}
