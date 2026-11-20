package com.orodent.ecos.ton.features.diskmodels.repository;

import static com.orodent.ecos.ton.features.diskmodels.model.DiskModels.*;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Per-version groups, including frozen physical mould snapshots. Connection belongs to caller. */
public final class ModelDiameterRepository {
    private final Connection c;

    public ModelDiameterRepository(Connection c) {
        this.c = c;
    }

    public List<DiameterGroup> load(long id) throws SQLException {
        var admitted = new TreeMap<BigDecimal, List<Mould>>();
        try (var q =
                c.prepareStatement(
                        "SELECT"
                            + " diametro_articolo_mm,stampo_id,stampo_revisione,codice,nome,diametro_mm"
                            + " FROM TON.MODELLO_DIAM_STAMPI WHERE modello_id=? ORDER BY"
                            + " diametro_articolo_mm,stampo_id")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                while (r.next())
                    admitted.computeIfAbsent(r.getBigDecimal(1), k -> new ArrayList<>())
                            .add(mould(r, 2));
            }
        }
        var groups = new ArrayList<DiameterGroup>();
        try (var q =
                c.prepareStatement(
                        "SELECT"
                            + " diametro_articolo_mm,grammi_per_mm,stampo_id,stampo_revisione,codice,nome,diametro_mm"
                            + " FROM TON.MODELLO_DIAMETRI WHERE modello_id=? ORDER BY"
                            + " diametro_articolo_mm")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                while (r.next())
                    groups.add(
                            new DiameterGroup(
                                    r.getBigDecimal(1),
                                    r.getBigDecimal(2),
                                    new Moulds(
                                            mould(r, 3),
                                            admitted.getOrDefault(r.getBigDecimal(1), List.of()))));
            }
        }
        return List.copyOf(groups);
    }

    private Mould mould(ResultSet r, int start) throws SQLException {
        return new Mould(
                r.getLong(start),
                r.getInt(start + 1),
                r.getString(start + 2),
                r.getString(start + 3),
                r.getBigDecimal(start + 4));
    }

    public Map<Long, List<BigDecimal>> catalogueDiameters() throws SQLException {
        var groups = new HashMap<Long, List<BigDecimal>>();
        try (var q = c.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT modello_id,diametro_articolo_mm FROM TON.MODELLO_DIAMETRI"
                                    + " ORDER BY modello_id,diametro_articolo_mm")) {
            while (r.next())
                groups.computeIfAbsent(r.getLong(1), k -> new ArrayList<>())
                        .add(r.getBigDecimal(2));
        }
        return groups;
    }

    public void delete(long id) throws SQLException {
        for (String table : List.of("MODELLO_DIAM_STAMPI", "MODELLO_DIAMETRI"))
            try (var q = c.prepareStatement("DELETE FROM TON." + table + " WHERE modello_id=?")) {
                q.setLong(1, id);
                q.executeUpdate();
            }
    }

    public void insert(long id, List<DiameterGroup> groups) throws SQLException {
        try (var ref =
                        c.prepareStatement(
                                "INSERT INTO"
                                    + " TON.MODELLO_DIAMETRI(modello_id,diametro_articolo_mm,grammi_per_mm,stampo_id,stampo_revisione,codice,nome,diametro_mm)"
                                    + " VALUES (?,?,?,?,?,?,?,?)");
                var other =
                        c.prepareStatement(
                                "INSERT INTO"
                                    + " TON.MODELLO_DIAM_STAMPI(modello_id,diametro_articolo_mm,stampo_id,stampo_revisione,codice,nome,diametro_mm)"
                                    + " VALUES (?,?,?,?,?,?,?)")) {
            for (var g : groups) {
                ref.setLong(1, id);
                ref.setBigDecimal(2, g.articleDiameter());
                ref.setBigDecimal(3, g.grams());
                bind(ref, 4, g.moulds().reference());
                ref.addBatch();
                for (var m : g.moulds().admitted()) {
                    other.setLong(1, id);
                    other.setBigDecimal(2, g.articleDiameter());
                    bind(other, 3, m);
                    other.addBatch();
                }
            }
            ref.executeBatch();
            other.executeBatch();
        }
    }

    private void bind(PreparedStatement q, int start, Mould m) throws SQLException {
        q.setLong(start, m.id());
        q.setInt(start + 1, m.revision());
        q.setString(start + 2, m.code());
        q.setString(start + 3, m.name());
        q.setBigDecimal(start + 4, m.diameter());
    }
}
