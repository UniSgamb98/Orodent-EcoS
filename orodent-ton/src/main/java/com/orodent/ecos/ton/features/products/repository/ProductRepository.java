package com.orodent.ecos.ton.features.products.repository;

import com.orodent.ecos.ton.features.products.model.ProductDraft;
import com.orodent.ecos.ton.features.products.model.ProductPage;
import com.orodent.ecos.ton.features.products.model.TonProduct;
import com.orodent.ecos.ton.features.products.model.ProductConflictException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

/** All statements use the caller's connection and transaction. */
public final class ProductRepository {
    private static final String COLUMNS = "prodotto_id, codice, descrizione, attivo, versione_modifica, creato_il, aggiornato_il";
    private final Connection connection;

    public ProductRepository(Connection connection) { this.connection = Objects.requireNonNull(connection); }

    public ProductPage findPage(String search, boolean includeInactive, int offset, int limit) throws SQLException {
        String pattern = "%" + search.toUpperCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String where = " WHERE (UPPER(codice) LIKE ? ESCAPE '\\' OR UPPER(COALESCE(descrizione, '')) LIKE ? ESCAPE '\\')"
                + (includeInactive ? "" : " AND attivo = TRUE");
        long total;
        try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM TON.PRODOTTI" + where)) {
            statement.setString(1, pattern); statement.setString(2, pattern);
            try (var rows = statement.executeQuery()) { rows.next(); total = rows.getLong(1); }
        }
        var products = new ArrayList<TonProduct>();
        try (var statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM TON.PRODOTTI" + where
                + " ORDER BY codice, prodotto_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
            statement.setString(1, pattern); statement.setString(2, pattern);
            statement.setInt(3, offset); statement.setInt(4, limit);
            try (var rows = statement.executeQuery()) { while (rows.next()) products.add(map(rows)); }
        }
        return new ProductPage(products, total, offset, limit);
    }

    public TonProduct insert(ProductDraft draft) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO TON.PRODOTTI(codice, descrizione, attivo) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            bindDraft(statement, draft);
            statement.executeUpdate();
            try (var keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Identificativo del prodotto non disponibile.");
                return findById(keys.getLong(1));
            }
        }
    }

    public TonProduct update(TonProduct original, ProductDraft draft) throws SQLException {
        try (var statement = connection.prepareStatement("""
                UPDATE TON.PRODOTTI SET codice=?, descrizione=?, attivo=?,
                    versione_modifica=versione_modifica+1, aggiornato_il=CURRENT_TIMESTAMP
                WHERE prodotto_id=? AND versione_modifica=?
                """)) {
            bindDraft(statement, draft);
            statement.setLong(4, original.id()); statement.setInt(5, original.revision());
            if (statement.executeUpdate() != 1) throw new ProductConflictException();
        }
        return findById(original.id());
    }

    private TonProduct findById(long id) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM TON.PRODOTTI WHERE prodotto_id=?")) {
            statement.setLong(1, id);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new SQLException("Prodotto non trovato dopo il salvataggio.");
                return map(rows);
            }
        }
    }

    private static void bindDraft(PreparedStatement statement, ProductDraft draft) throws SQLException {
        statement.setString(1, draft.code()); statement.setString(2, draft.description()); statement.setBoolean(3, draft.active());
    }

    private static TonProduct map(ResultSet row) throws SQLException {
        return new TonProduct(row.getLong("prodotto_id"), row.getString("codice"), row.getString("descrizione"),
                row.getBoolean("attivo"), row.getInt("versione_modifica"),
                row.getTimestamp("creato_il").toLocalDateTime(), row.getTimestamp("aggiornato_il").toLocalDateTime());
    }
}
