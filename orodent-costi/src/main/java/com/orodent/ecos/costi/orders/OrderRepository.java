package com.orodent.ecos.costi.orders;

import com.orodent.ecos.costi.orders.PowderOrders.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class OrderRepository {
    private final Connection connection;

    public OrderRepository(Connection connection) {
        this.connection = connection;
    }

    public List<Stored> list() throws SQLException {
        var rows = new ArrayList<Stored>();
        try (var q = connection.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT"
                                    + " o.ordine_id,o.fornitore,o.numero_ordine,o.posizione,v.numero_versione,v.polvere_id,v.codice_sorgente,v.descrizione,v.data_ordine,v.consegna_prevista,v.consegna_manuale,v.quantita_ordinata,v.quantita_consegnata,v.quantita_residua,v.prezzo_kg,v.escluso"
                                    + " FROM COSTI.ORDINI_POLVERI o JOIN"
                                    + " COSTI.ORDINI_POLVERI_REVISIONI v ON v.ordine_id=o.ordine_id"
                                    + " AND v.numero_versione=(SELECT MAX(x.numero_versione) FROM"
                                    + " COSTI.ORDINI_POLVERI_REVISIONI x WHERE"
                                    + " x.ordine_id=o.ordine_id) ORDER BY"
                                    + " o.fornitore,o.numero_ordine,o.posizione")) {
            while (r.next())
                rows.add(
                        new Stored(
                                r.getLong(1),
                                r.getInt(5),
                                r.getLong(6),
                                new Row(
                                        new Key(r.getString(2), r.getString(3), r.getInt(4)),
                                        r.getDate(9).toLocalDate(),
                                        date(r, 10),
                                        r.getString(7),
                                        r.getString(8),
                                        r.getBigDecimal(12),
                                        r.getBigDecimal(13),
                                        r.getBigDecimal(14),
                                        r.getBigDecimal(15)),
                                date(r, 11),
                                r.getBoolean(16)));
        }
        return List.copyOf(rows);
    }

    public List<Powder> powders() throws SQLException {
        var rows = new ArrayList<Powder>();
        try (var q = connection.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT polvere_id,codice,nome,colore FROM TON.POLVERI ORDER BY"
                                    + " codice")) {
            while (r.next())
                rows.add(new Powder(r.getLong(1), r.getString(2), r.getString(3), r.getString(4)));
        }
        return List.copyOf(rows);
    }

    public Map<Code, Long> aliases() throws SQLException {
        var result = new HashMap<Code, Long>();
        try (var q = connection.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT fornitore,codice_sorgente,polvere_id FROM"
                                        + " COSTI.POLVERI_CODICI_FORNITORE")) {
            while (r.next()) result.put(new Code(r.getString(1), r.getString(2)), r.getLong(3));
        }
        return Map.copyOf(result);
    }

    public Report apply(Preview preview, Map<Code, Long> chosen) throws SQLException {
        if (!preview.valid())
            throw new IllegalArgumentException(
                    "Correggi gli errori dell'anteprima prima di importare.");
        var allowed = new HashSet<Long>();
        powders().forEach(p -> allowed.add(p.id()));
        Map<Code, Long> aliases = aliases();
        var source = new TreeMap<Key, Row>();
        preview.csv().validRows().forEach(r -> source.put(r.value().key(), r.value()));
        int created = 0, updated = 0, unchanged = 0;
        for (Row row : source.values()) {
            Long powder = chosen.getOrDefault(row.source(), preview.mappings().get(row.source()));
            if (powder == null || !allowed.contains(powder))
                throw new IllegalArgumentException(
                        "Collega la polvere " + row.code() + " al catalogo TON.");
            Long alias = aliases.get(row.source());
            if (alias != null && !alias.equals(powder)) throw new Conflict();
            if (alias == null) {
                try (var q =
                        connection.prepareStatement(
                                "INSERT INTO"
                                    + " COSTI.POLVERI_CODICI_FORNITORE(fornitore,codice_sorgente,polvere_id)"
                                    + " VALUES (?,?,?)")) {
                    q.setString(1, row.key().supplier());
                    q.setString(2, row.code());
                    q.setLong(3, powder);
                    try {
                        q.executeUpdate();
                    } catch (SQLException e) {
                        if ("23505".equals(e.getSQLState())) throw new Conflict();
                        throw e;
                    }
                }
                aliases = new HashMap<>(aliases);
                aliases.put(row.source(), powder);
            }
            Stored expected = preview.existing().get(row.key());
            long id;
            if (expected == null) {
                try (var q =
                        connection.prepareStatement(
                                "INSERT INTO"
                                        + " COSTI.ORDINI_POLVERI(fornitore,numero_ordine,posizione)"
                                        + " VALUES (?,?,?)",
                                Statement.RETURN_GENERATED_KEYS)) {
                    q.setString(1, row.key().supplier());
                    q.setString(2, row.key().order());
                    q.setInt(3, row.key().position());
                    try {
                        q.executeUpdate();
                    } catch (SQLException e) {
                        if ("23505".equals(e.getSQLState())) throw new Conflict();
                        throw e;
                    }
                    try (var keys = q.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getLong(1);
                    }
                }
            } else {
                id = expected.id();
                lock(id);
                if (version(id) != expected.version()) throw new Conflict();
            }
            if (expected != null && expected.row().equals(row) && expected.powderId() == powder) {
                unchanged++;
                continue;
            }
            insert(
                    id,
                    expected == null ? 1 : expected.version() + 1,
                    powder,
                    row,
                    expected == null ? null : expected.overrideDate(),
                    expected != null && expected.excluded(),
                    "CSV");
            if (expected == null) created++;
            else updated++;
        }
        return new Report(created, updated, unchanged);
    }

    public void edit(Stored expected, LocalDate overrideDate, boolean excluded)
            throws SQLException {
        lock(expected.id());
        if (version(expected.id()) != expected.version()) throw new Conflict();
        if (Objects.equals(expected.overrideDate(), overrideDate)
                && expected.excluded() == excluded) return;
        insert(
                expected.id(),
                expected.version() + 1,
                expected.powderId(),
                expected.row(),
                overrideDate,
                excluded,
                "MODIFICA");
    }

    private void lock(long id) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "UPDATE COSTI.ORDINI_POLVERI SET posizione=posizione WHERE ordine_id=?")) {
            q.setLong(1, id);
            if (q.executeUpdate() != 1) throw new Conflict();
        }
    }

    private int version(long id) throws SQLException {
        try (var q =
                connection.prepareStatement(
                        "SELECT MAX(numero_versione) FROM COSTI.ORDINI_POLVERI_REVISIONI WHERE"
                                + " ordine_id=?")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                r.next();
                return r.getInt(1);
            }
        }
    }

    private void insert(
            long id,
            int version,
            long powder,
            Row row,
            LocalDate manual,
            boolean excluded,
            String origin)
            throws SQLException {
        if (version < 1) throw new IllegalArgumentException("Limite revisioni raggiunto.");
        try (var q =
                connection.prepareStatement(
                        "INSERT INTO"
                            + " COSTI.ORDINI_POLVERI_REVISIONI(ordine_id,numero_versione,polvere_id,codice_sorgente,descrizione,data_ordine,consegna_prevista,consegna_manuale,quantita_ordinata,quantita_consegnata,quantita_residua,prezzo_kg,escluso,origine)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            q.setLong(1, id);
            q.setInt(2, version);
            q.setLong(3, powder);
            q.setString(4, row.code());
            q.setString(5, row.description());
            q.setDate(6, java.sql.Date.valueOf(row.orderDate()));
            q.setDate(7, row.expected() == null ? null : java.sql.Date.valueOf(row.expected()));
            q.setDate(8, manual == null ? null : java.sql.Date.valueOf(manual));
            q.setBigDecimal(9, row.ordered());
            q.setBigDecimal(10, row.delivered());
            q.setBigDecimal(11, row.remaining());
            q.setBigDecimal(12, row.price());
            q.setBoolean(13, excluded);
            q.setString(14, origin);
            q.executeUpdate();
        }
    }

    private static LocalDate date(ResultSet r, int col) throws SQLException {
        var date = r.getDate(col);
        return date == null ? null : date.toLocalDate();
    }
}
