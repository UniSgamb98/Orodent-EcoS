package com.orodent.ecos.database;

import com.google.gson.*;

import java.sql.*;
import java.util.*;

/** Expands a frozen plan into individual identities, once, in the caller's transaction. */
public final class ProducedDiscs {
    private ProducedDiscs() {}

    public static void confirm(Connection c, long planId) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "UPDATE TON.PIANI_PRODUZIONE SET versione_modifica=versione_modifica WHERE"
                            + " piano_id=? AND stato='PREPARATA'")) {
            q.setLong(1, planId);
            if (q.executeUpdate() != 1)
                throw new IllegalArgumentException("La produzione non è confermata.");
        }
        JsonObject plan;
        java.sql.Date date;
        long productId;
        int expected;
        try (var q =
                c.prepareStatement(
                        "SELECT"
                            + " contenuto_piano,data_preparazione,prodotto_id,numero_dischi,dischi_confermati_il"
                            + " FROM TON.PIANI_PRODUZIONE WHERE piano_id=?")) {
            q.setLong(1, planId);
            try (var r = q.executeQuery()) {
                r.next();
                if (r.getTimestamp(5) != null) return;
                try {
                    plan = JsonParser.parseString(r.getString(1)).getAsJsonObject();
                } catch (RuntimeException invalid) {
                    throw new IllegalArgumentException(
                            "Il piano storico non contiene un formato supportato.", invalid);
                }
                date = r.getDate(2);
                productId = r.getLong(3);
                expected = r.getInt(4);
            }
        }
        int schema = plan.get("schemaVersion").getAsInt();
        if (schema != 1 && schema != 2)
            throw new IllegalArgumentException("Versione del piano non supportata.");
        var product = plan.getAsJsonObject("product");
        var formats = plan.getAsJsonArray("formats");
        if (product.get("id").getAsLong() != productId)
            throw new IllegalArgumentException("Prodotto del piano non coerente.");
        long total = 0;
        for (var value : formats) {
            int quantity = value.getAsJsonObject().get("quantity").getAsInt();
            if (quantity <= 0) throw new IllegalArgumentException("Quantità del piano non valida.");
            total += quantity;
        }
        if (total != expected || total == 0)
            throw new IllegalArgumentException("Quantità del piano non coerenti.");
        long execution = 0;
        int row = 0, batch = 0;
        try (var insert =
                c.prepareStatement(
                        "INSERT INTO"
                            + " TON.DISCHI_PRODOTTI(esecuzione_id,piano_id,numero_riga,progressivo,articolo_id,prodotto_id,articolo_codice,prodotto_codice,linea_codice,colore,diametro_mm,altezza_mm,specifica_prodotto_id,specifica_articolo_id)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for (var value : formats) {
                row++;
                var format = value.getAsJsonObject();
                var article = format.getAsJsonObject("article");
                int quantity = format.get("quantity").getAsInt();
                var existing = new HashSet<Integer>();
                try (var q =
                        c.prepareStatement(
                                "SELECT progressivo FROM TON.DISCHI_PRODOTTI WHERE piano_id=? AND"
                                    + " numero_riga=?")) {
                    q.setLong(1, planId);
                    q.setInt(2, row);
                    try (var r = q.executeQuery()) {
                        while (r.next()) {
                            int ordinal = r.getInt(1);
                            if (ordinal > quantity)
                                throw new IllegalArgumentException(
                                        "Dischi già registrati oltre la quantità del piano.");
                            existing.add(ordinal);
                        }
                    }
                }
                for (long position = 1; position <= quantity; position++) {
                    int ordinal = (int) position;
                    if (existing.contains(ordinal)) continue;
                    if (execution == 0) execution = execution(c, planId, date);
                    insert.setLong(1, execution);
                    insert.setLong(2, planId);
                    insert.setInt(3, row);
                    insert.setInt(4, ordinal);
                    insert.setLong(5, article.get("id").getAsLong());
                    insert.setLong(6, productId);
                    insert.setString(7, article.get("code").getAsString());
                    insert.setString(8, product.get("code").getAsString());
                    insert.setString(9, product.get("lineCode").getAsString());
                    insert.setString(
                            10,
                            product.has("color") && !product.get("color").isJsonNull()
                                    ? product.get("color").getAsString()
                                    : "");
                    insert.setBigDecimal(11, article.get("diameter").getAsBigDecimal());
                    insert.setBigDecimal(12, article.get("height").getAsBigDecimal());
                    long spec = article.get("specificationId").getAsLong();
                    insert.setObject(13, schema == 2 ? spec : null, Types.BIGINT);
                    insert.setObject(14, schema == 1 ? spec : null, Types.BIGINT);
                    insert.addBatch();
                    if (++batch == 1000) {
                        insert.executeBatch();
                        batch = 0;
                    }
                }
            }
            if (batch > 0) insert.executeBatch();
        }
        try (var q =
                c.prepareStatement(
                        "UPDATE TON.PIANI_PRODUZIONE SET dischi_confermati_il=CURRENT_TIMESTAMP"
                            + " WHERE piano_id=?")) {
            q.setLong(1, planId);
            q.executeUpdate();
        }
    }

    private static long execution(Connection c, long planId, java.sql.Date date)
            throws SQLException {
        try (var q =
                c.prepareStatement(
                        "INSERT INTO TON.ESECUZIONI_PIANO(piano_id,data_produzione,note) VALUES"
                            + " (?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            q.setLong(1, planId);
            q.setDate(2, date);
            q.setString(3, "Registrazione automatica alla conferma della produzione.");
            q.executeUpdate();
            try (var keys = q.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    static void upgradePrepared(Connection c) throws SQLException {
        var ids = new ArrayList<Long>();
        try (var q = c.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT piano_id,contenuto_piano FROM TON.PIANI_PRODUZIONE WHERE"
                                    + " stato='PREPARATA'")) {
            while (r.next()) {
                // Old imported placeholders have no executable plan. Preserve them verbatim.
                try {
                    var plan = JsonParser.parseString(r.getString(2)).getAsJsonObject();
                    if (plan.has("schemaVersion") && plan.has("formats") && plan.has("product"))
                        ids.add(r.getLong(1));
                } catch (JsonParseException | IllegalStateException ignored) {
                }
            }
        }
        for (long id : ids) confirm(c, id);
    }
}
