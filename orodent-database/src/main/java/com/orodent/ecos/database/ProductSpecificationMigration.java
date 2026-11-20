package com.orodent.ecos.database;

import java.sql.*;
import java.util.*;

/** Retains every legacy reference, merging only identical product-level content and snapshots. */
final class ProductSpecificationMigration {
    static void apply(Connection c) throws SQLException {
        var ids = new LinkedHashMap<Key, Long>();
        var numbers = new HashMap<Long, Integer>();
        try (var q = c.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT * FROM TON.CONFIGURAZIONI_ARTICOLO ORDER BY"
                                        + " prodotto_id,creato_il,configurazione_id")) {
            while (r.next()) {
                var key =
                        new Key(
                                r.getLong("prodotto_id"),
                                r.getLong("composizione_id"),
                                r.getLong("modello_id"),
                                r.getString("versione_calcolo"),
                                r.getString("note"),
                                r.getString("stato"),
                                (Long) r.getObject("linea_id"),
                                (Integer) r.getObject("linea_revisione"),
                                (Integer) r.getObject("prodotto_revisione"));
                Long id = ids.get(key);
                if (id == null) {
                    try (var insert =
                            c.prepareStatement(
                                    "INSERT INTO"
                                        + " TON.CONFIGURAZIONI_PRODOTTO(prodotto_id,numero_versione,composizione_id,modello_id,versione_calcolo,note,stato,linea_id,linea_revisione,prodotto_revisione,creato_il,aggiornato_il,approvato_il)"
                                        + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                    Statement.RETURN_GENERATED_KEYS)) {
                        insert.setLong(1, key.product());
                        insert.setInt(2, numbers.merge(key.product(), 1, Integer::sum));
                        insert.setLong(3, key.composition());
                        insert.setLong(4, key.model());
                        insert.setString(5, key.calculation());
                        insert.setString(6, key.notes());
                        insert.setString(7, key.state());
                        insert.setObject(8, key.line(), Types.BIGINT);
                        insert.setObject(9, key.lineRevision(), Types.INTEGER);
                        insert.setObject(10, key.productRevision(), Types.INTEGER);
                        insert.setTimestamp(11, r.getTimestamp("creato_il"));
                        insert.setTimestamp(12, r.getTimestamp("aggiornato_il"));
                        insert.setTimestamp(13, r.getTimestamp("approvato_il"));
                        insert.executeUpdate();
                        try (var generated = insert.getGeneratedKeys()) {
                            generated.next();
                            id = generated.getLong(1);
                        }
                    }
                    ids.put(key, id);
                }
                try (var map =
                        c.prepareStatement("INSERT INTO TON.CONFIGURAZIONI_MIGRATE VALUES (?,?)")) {
                    map.setLong(1, r.getLong("configurazione_id"));
                    map.setLong(2, id);
                    map.executeUpdate();
                }
            }
        }
        for (long product : numbers.keySet()) {
            var defaults = new HashSet<Long>();
            try (var q =
                    c.prepareStatement(
                            "SELECT DISTINCT m.prodotto_configurazione_id FROM TON.ARTICOLI a JOIN"
                                + " TON.CONFIGURAZIONI_MIGRATE m ON"
                                + " m.articolo_configurazione_id=a.configurazione_predefinita_id"
                                + " WHERE a.prodotto_id=?")) {
                q.setLong(1, product);
                try (var r = q.executeQuery()) {
                    while (r.next()) defaults.add(r.getLong(1));
                }
            }
            Long preferred = defaults.size() == 1 ? defaults.iterator().next() : null;
            if (preferred != null)
                try (var q =
                        c.prepareStatement(
                                "SELECT stato,linea_revisione,prodotto_revisione FROM"
                                    + " TON.CONFIGURAZIONI_PRODOTTO WHERE configurazione_id=?")) {
                    q.setLong(1, preferred);
                    try (var r = q.executeQuery()) {
                        r.next();
                        if (!"APPROVATA".equals(r.getString(1))
                                || r.getObject(2) == null
                                || r.getObject(3) == null) preferred = null;
                    }
                }
            // Ambiguous or incomplete imports require an explicit product-level decision.
            try (var q =
                    c.prepareStatement(
                            "UPDATE TON.PRODOTTI SET"
                                + " configurazione_predefinita_id=?,specifiche_da_verificare=?,versione_modifica=versione_modifica+1,aggiornato_il=CURRENT_TIMESTAMP"
                                + " WHERE prodotto_id=?")) {
                q.setObject(1, preferred, Types.BIGINT);
                q.setBoolean(2, preferred == null);
                q.setLong(3, product);
                q.executeUpdate();
            }
            try (var q =
                    c.prepareStatement(
                            "INSERT INTO"
                                + " TON.PRODOTTI_REVISIONI(prodotto_id,numero_revisione,codice,descrizione,linea_id,linea_codice,colore_commerciale,note,attivo,configurazione_predefinita_id,specifiche_da_verificare,origine)"
                                + " SELECT"
                                + " p.prodotto_id,p.versione_modifica,p.codice,p.descrizione,p.linea_id,l.codice,p.colore_commerciale,p.note,p.attivo,p.configurazione_predefinita_id,p.specifiche_da_verificare,'MIGRAZIONE'"
                                + " FROM TON.PRODOTTI p LEFT JOIN TON.LINEE l ON"
                                + " l.linea_id=p.linea_id WHERE p.prodotto_id=?")) {
                q.setLong(1, product);
                q.executeUpdate();
            }
        }
    }

    private record Key(
            long product,
            long composition,
            long model,
            String calculation,
            String notes,
            String state,
            Long line,
            Integer lineRevision,
            Integer productRevision) {}
}
