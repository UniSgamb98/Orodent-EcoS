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
SELECT p.polvere_id,p.codice,p.nome,p.colore,p.attivo,p.fornitore,
       c.prezzo_id,c.numero_versione,c.modalita,c.data_riferimento,CASE WHEN c.modello_dati=2 THEN c.prezzo_riferimento ELSE c.prezzo_semplice END AS prezzo_semplice,
       CASE WHEN c.modello_dati=2 THEN c.quantita_totale ELSE c.quantita_semplice END AS quantita_semplice,c.modello_dati
FROM TON.POLVERI p LEFT JOIN COSTI.POLVERI_PREZZI c ON c.polvere_id=p.polvere_id
  AND c.numero_versione=(SELECT MAX(v.numero_versione) FROM COSTI.POLVERI_PREZZI v WHERE v.polvere_id=p.polvere_id)
""";
    private final Connection connection;

    public PowderPriceRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    public Page page(String search, Filter filter, boolean inactive, int offset, int limit)
            throws SQLException {
        return page(
                search,
                filter,
                inactive,
                offset,
                limit,
                Set.of("BIANCA", "GIALLA", "ROSA", "GRIGIA"));
    }

    public Page page(
            String search,
            Filter filter,
            boolean inactive,
            int offset,
            int limit,
            Set<String> selectedColors)
            throws SQLException {
        var colors =
                List.of("BIANCA", "GIALLA", "ROSA", "GRIGIA").stream()
                        .filter(selectedColors::contains)
                        .toList();
        String colorWhere =
                colors.isEmpty()
                        ? " AND 1=0"
                        : " AND (p.colore IS NULL OR p.colore IN ("
                                + String.join(",", Collections.nCopies(colors.size(), "?"))
                                + "))";
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
                        + colorWhere
                        + switch (filter) {
                            case TUTTE -> "";
                            case SEMPLICE -> "";
                            case AVANZATA -> "";
                            case MANCANTI ->
                                    " AND (c.prezzo_id IS NULL OR c.prezzo_riferimento IS NULL OR"
                                            + " c.quantita_totale IS NULL)";
                        };
        int total;
        String from = SELECT.substring(SELECT.indexOf("FROM TON.POLVERI"));
        try (var q = connection.prepareStatement("SELECT COUNT(*) " + from + where)) {
            q.setString(1, pattern);
            q.setString(2, pattern);
            for (int i = 0; i < colors.size(); i++) q.setString(3 + i, colors.get(i));
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
            for (int i = 0; i < colors.size(); i++) q.setString(3 + i, colors.get(i));
            q.setInt(3 + colors.size(), offset);
            q.setInt(4 + colors.size(), limit);
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
                                            draft == null ? null : draft.unitPrice(),
                                            draft == null
                                                    ? BigDecimal.ZERO
                                                    : draft.totals().quantity(),
                                            draft == null ? null : draft.totals().unitPrice());
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
        try (var lock =
                connection.prepareStatement(
                        "UPDATE TON.POLVERI SET versione_modifica=versione_modifica WHERE"
                                + " polvere_id=?")) {
            lock.setLong(1, powderId);
            if (lock.executeUpdate() != 1)
                throw new IllegalArgumentException("Polvere non disponibile.");
        }
        Detail current = load(powderId);
        if (current.version() != expectedVersion) throw new Conflict();
        if (expectedVersion == Integer.MAX_VALUE)
            throw new IllegalArgumentException("Numero massimo di revisioni raggiunto.");
        long id;
        try (var q =
                connection.prepareStatement(
                        "INSERT INTO"
                            + " COSTI.POLVERI_PREZZI(polvere_id,numero_versione,data_riferimento,modalita,prezzo_riferimento,quantita_totale,modello_dati)"
                            + " VALUES (?,?,?,?,?,?,2)",
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
                        r.getBoolean("attivo"), Objects.toString(r.getString("fornitore"), "")),
                r.getLong("prezzo_id"),
                r.getInt("numero_versione"),
                mode == null ? null : Mode.valueOf(mode),
                date == null ? null : date.toLocalDate(),
                r.getBigDecimal("prezzo_semplice"),
                r.getBigDecimal("quantita_semplice"),
                r.getInt("modello_dati"));
    }

    private record Header(
            Powder powder,
            long id,
            int version,
            Mode mode,
            LocalDate date,
            BigDecimal price,
            BigDecimal quantity,
            int model) {
        Draft draft(List<Lot> lots) {
            if (mode == null) return null;
            if (model == 1 && mode == Mode.AVANZATA) {
                var totals = com.orodent.ecos.costi.model.PowderPrices.aggregate(lots);
                return new Draft(
                        mode,
                        date,
                        totals.unitPrice() == null ? price : totals.unitPrice(),
                        totals.quantity(),
                        lots);
            }
            return new Draft(mode, date, price, quantity, lots);
        }
    }
}
