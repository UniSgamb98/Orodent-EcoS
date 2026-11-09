package com.orodent.ecos.costi.repository;

import com.orodent.ecos.costi.model.PowderPrices.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** SQL on borrowed connections. Only COSTI is written; TON remains the identity authority. */
public final class PowderPriceRepository {
    private static final String SELECT =
            """
SELECT p.polvere_id,p.codice,p.nome,p.colore,p.attivo,
       c.prezzo_id,c.numero_versione,c.modalita,c.data_riferimento,c.prezzo_semplice,c.quantita_semplice
FROM TON.POLVERI p LEFT JOIN COSTI.POLVERI_PREZZI c ON c.polvere_id=p.polvere_id
  AND c.numero_versione=(SELECT MAX(v.numero_versione) FROM COSTI.POLVERI_PREZZI v WHERE v.polvere_id=p.polvere_id)
""";
    private final Connection connection;

    public PowderPriceRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    public Page page(String search, Filter filter, boolean inactive, int offset, int limit)
            throws SQLException {
        String pattern =
                "%"
                        + search.toUpperCase(Locale.ROOT)
                                .replace("\\", "\\\\")
                                .replace("%", "\\%")
                                .replace("_", "\\_")
                        + "%";
        String where =
                " WHERE (UPPER(p.codice) LIKE ? ESCAPE '\\' OR UPPER(p.nome) LIKE ? ESCAPE '\\')"
                        + (inactive ? "" : " AND p.attivo=TRUE")
                        + switch (filter) {
                            case TUTTE -> "";
                            case SEMPLICE -> " AND c.modalita='SEMPLICE'";
                            case AVANZATA -> " AND c.modalita='AVANZATA'";
                            case MANCANTI ->
                                    " AND (c.prezzo_id IS NULL OR (c.modalita='AVANZATA' AND NOT"
                                            + " EXISTS (SELECT 1 FROM COSTI.POLVERI_LOTTI z WHERE"
                                            + " z.prezzo_id=c.prezzo_id AND z.quantita_kg>0)))";
                        };
        int total;
        String from = SELECT.substring(SELECT.indexOf("FROM TON.POLVERI"));
        try (var q = connection.prepareStatement("SELECT COUNT(*) " + from + where)) {
            q.setString(1, pattern);
            q.setString(2, pattern);
            try (var r = q.executeQuery()) {
                r.next();
                total = r.getInt(1);
            }
        }
        var headers = new ArrayList<Header>();
        try (var q =
                connection.prepareStatement(
                        SELECT
                                + where
                                + " ORDER BY CASE p.colore WHEN 'BIANCA' THEN 0 WHEN 'GIALLA' THEN"
                                + " 1 WHEN 'ROSA' THEN 2 WHEN 'GRIGIA' THEN 3 ELSE 4"
                                + " END,p.codice,p.polvere_id OFFSET ? ROWS FETCH NEXT ? ROWS"
                                + " ONLY")) {
            q.setString(1, pattern);
            q.setString(2, pattern);
            q.setInt(3, offset);
            q.setInt(4, limit);
            try (var r = q.executeQuery()) {
                while (r.next()) headers.add(header(r));
            }
        }
        var lots = lots(headers.stream().filter(h -> h.id != 0).map(h -> h.id).toList());
        var rows =
                headers.stream()
                        .map(
                                h -> {
                                    Draft draft = h.draft(lots.getOrDefault(h.id, List.of()));
                                    return new Row(
                                            h.powder,
                                            h.mode,
                                            h.date,
                                            draft == null ? null : draft.quantity(),
                                            draft == null ? null : draft.unitPrice());
                                })
                        .toList();
        return new Page(rows, total, offset);
    }

    public Detail load(long powderId) throws SQLException {
        try (var q = connection.prepareStatement(SELECT + " WHERE p.polvere_id=?")) {
            q.setLong(1, powderId);
            try (var r = q.executeQuery()) {
                if (!r.next())
                    throw new IllegalArgumentException(
                            "La polvere non è più presente nel catalogo TON. Ricarica l'elenco.");
                Header h = header(r);
                return new Detail(
                        h.powder,
                        h.version,
                        h.draft(
                                h.id == 0
                                        ? List.of()
                                        : lots(List.of(h.id)).getOrDefault(h.id, List.of())));
            }
        }
    }

    public Detail save(long powderId, int expectedVersion, Draft draft) throws SQLException {
        Detail current = load(powderId);
        if (current.version() != expectedVersion) throw new Conflict();
        if (expectedVersion == Integer.MAX_VALUE)
            throw new IllegalArgumentException("Numero massimo di revisioni raggiunto.");
        long id;
        try (var q =
                connection.prepareStatement(
                        "INSERT INTO"
                            + " COSTI.POLVERI_PREZZI(polvere_id,numero_versione,data_riferimento,modalita,prezzo_semplice,quantita_semplice)"
                            + " VALUES (?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            q.setLong(1, powderId);
            q.setInt(2, expectedVersion + 1);
            q.setDate(3, java.sql.Date.valueOf(draft.referenceDate()));
            q.setString(4, draft.mode().name());
            q.setBigDecimal(5, draft.simplePrice());
            q.setBigDecimal(6, draft.simpleQuantity());
            try {
                q.executeUpdate();
            } catch (SQLException failure) {
                if ("23505".equals(failure.getSQLState())) throw new Conflict();
                throw failure;
            }
            try (var keys = q.getGeneratedKeys()) {
                if (!keys.next())
                    throw new SQLException("Identificativo della fotografia non disponibile.");
                id = keys.getLong(1);
            }
        }
        try (var q =
                connection.prepareStatement(
                        "INSERT INTO"
                            + " COSTI.POLVERI_LOTTI(prezzo_id,posizione,lotto,quantita_kg,prezzo_kg)"
                            + " VALUES (?,?,?,?,?)")) {
            int position = 0;
            for (Lot lot : draft.lots()) {
                q.setLong(1, id);
                q.setInt(2, ++position);
                q.setString(3, lot.code());
                q.setBigDecimal(4, lot.quantity());
                q.setBigDecimal(5, lot.unitPrice());
                q.addBatch();
            }
            if (!draft.lots().isEmpty()) q.executeBatch();
        }
        return new Detail(current.powder(), expectedVersion + 1, draft);
    }

    private Map<Long, List<Lot>> lots(List<Long> ids) throws SQLException {
        Map<Long, List<Lot>> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        try (var q =
                connection.prepareStatement(
                        "SELECT prezzo_id,lotto,quantita_kg,prezzo_kg FROM COSTI.POLVERI_LOTTI"
                                + " WHERE prezzo_id IN ("
                                + placeholders
                                + ") ORDER BY prezzo_id,posizione")) {
            for (int i = 0; i < ids.size(); i++) q.setLong(i + 1, ids.get(i));
            try (var r = q.executeQuery()) {
                while (r.next())
                    result.computeIfAbsent(r.getLong(1), ignored -> new ArrayList<>())
                            .add(new Lot(r.getString(2), r.getBigDecimal(3), r.getBigDecimal(4)));
            }
        }
        return result;
    }

    private static Header header(ResultSet r) throws SQLException {
        var date = r.getDate("data_riferimento");
        String mode = r.getString("modalita");
        return new Header(
                new Powder(
                        r.getLong("polvere_id"),
                        r.getString("codice"),
                        r.getString("nome"),
                        r.getString("colore"),
                        r.getBoolean("attivo")),
                r.getLong("prezzo_id"),
                r.getInt("numero_versione"),
                mode == null ? null : Mode.valueOf(mode),
                date == null ? null : date.toLocalDate(),
                r.getBigDecimal("prezzo_semplice"),
                r.getBigDecimal("quantita_semplice"));
    }

    private record Header(
            Powder powder,
            long id,
            int version,
            Mode mode,
            LocalDate date,
            BigDecimal price,
            BigDecimal quantity) {
        Draft draft(List<Lot> lots) {
            return mode == null ? null : new Draft(mode, date, price, quantity, lots);
        }
    }
}
