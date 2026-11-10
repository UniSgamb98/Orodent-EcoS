package com.orodent.ecos.database;

import java.math.*;
import java.sql.*;
import java.util.*;

/**
 * Converts only the current legacy state by appending a revision. Historical values are retained.
 */
final class WarehouseMigration {
    static void apply(Connection c) throws SQLException {
        record Legacy(
                long price,
                long powder,
                int version,
                String mode,
                java.sql.Date date,
                BigDecimal reference,
                BigDecimal quantity) {}
        var old = new ArrayList<Legacy>();
        try (var q = c.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT"
                                    + " p.prezzo_id,p.polvere_id,p.numero_versione,p.modalita,p.data_riferimento,p.prezzo_semplice,p.quantita_semplice"
                                    + " FROM COSTI.POLVERI_PREZZI p WHERE p.modello_dati=1 AND"
                                    + " p.numero_versione=(SELECT MAX(v.numero_versione) FROM"
                                    + " COSTI.POLVERI_PREZZI v WHERE v.polvere_id=p.polvere_id)"
                                    + " ORDER BY p.polvere_id")) {
            while (r.next())
                old.add(
                        new Legacy(
                                r.getLong(1),
                                r.getLong(2),
                                r.getInt(3),
                                r.getString(4),
                                r.getDate(5),
                                r.getBigDecimal(6),
                                r.getBigDecimal(7)));
        }
        for (Legacy p : old) {
            BigDecimal qty = p.quantity(), price = p.reference();
            if (p.mode().equals("AVANZATA")) {
                BigDecimal total = BigDecimal.ZERO, value = BigDecimal.ZERO;
                try (var q =
                        c.prepareStatement(
                                "SELECT quantita_kg,prezzo_kg FROM COSTI.POLVERI_LOTTI WHERE"
                                        + " prezzo_id=?")) {
                    q.setLong(1, p.price());
                    try (var r = q.executeQuery()) {
                        while (r.next()) {
                            total = total.add(r.getBigDecimal(1));
                            value = value.add(r.getBigDecimal(1).multiply(r.getBigDecimal(2)));
                        }
                    }
                }
                qty = total;
                if (total.signum() > 0) price = value.divide(total, 8, RoundingMode.HALF_UP);
            }
            if (p.version() == Integer.MAX_VALUE)
                throw new SQLException("Limite revisioni magazzino raggiunto.");
            long id;
            try (var q =
                    c.prepareStatement(
                            "INSERT INTO"
                                + " COSTI.POLVERI_PREZZI(polvere_id,numero_versione,modalita,data_riferimento,modello_dati,prezzo_riferimento,quantita_totale)"
                                + " VALUES (?,?,?,?,2,?,?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                q.setLong(1, p.powder());
                q.setInt(2, p.version() + 1);
                q.setString(3, p.mode());
                q.setDate(4, p.date());
                q.setBigDecimal(5, price);
                q.setBigDecimal(6, qty);
                q.executeUpdate();
                try (var r = q.getGeneratedKeys()) {
                    r.next();
                    id = r.getLong(1);
                }
            }
            try (var q =
                    c.prepareStatement(
                            "INSERT INTO"
                                + " COSTI.POLVERI_LOTTI(prezzo_id,posizione,lotto,quantita_kg,prezzo_kg)"
                                + " SELECT ?,posizione,lotto,quantita_kg,prezzo_kg FROM"
                                + " COSTI.POLVERI_LOTTI WHERE prezzo_id=?")) {
                q.setLong(1, id);
                q.setLong(2, p.price());
                q.executeUpdate();
            }
        }
    }
}
