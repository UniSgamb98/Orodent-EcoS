package com.orodent.ecos.ton.features.diskmodels.repository;

import static com.orodent.ecos.ton.features.diskmodels.model.DiskModels.*;

import com.orodent.ecos.ton.features.equipment.model.Equipment.Kind;
import com.orodent.ecos.ton.features.equipment.repository.EquipmentRepository;

import java.sql.*;
import java.util.*;

/** Per-model snapshots stay independent of subsequent equipment edits. */
public final class ModelMouldRepository {
    private final Connection c;

    public ModelMouldRepository(Connection c) {
        this.c = c;
    }

    public List<Mould> choices() throws SQLException {
        return new EquipmentRepository(c)
                .list(Kind.MOULDS, "", false).stream()
                        .map(
                                e ->
                                        new Mould(
                                                e.id(),
                                                e.revision(),
                                                e.code(),
                                                e.name(),
                                                e.dimension()))
                        .toList();
    }

    public Moulds load(long id) throws SQLException {
        var reference = rows("MODELLO_STAMPO_RIF", id);
        if (reference.isEmpty()) return null;
        return new Moulds(reference.getFirst(), rows("MODELLO_STAMPI_AMMESSI", id));
    }

    private List<Mould> rows(String table, long id) throws SQLException {
        var items = new ArrayList<Mould>();
        try (var q =
                c.prepareStatement(
                        "SELECT stampo_id,stampo_revisione,codice,nome,diametro_mm FROM TON."
                                + table
                                + " WHERE modello_id=? ORDER BY stampo_id")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                while (r.next())
                    items.add(
                            new Mould(
                                    r.getLong(1),
                                    r.getInt(2),
                                    r.getString(3),
                                    r.getString(4),
                                    r.getBigDecimal(5)));
            }
        }
        return items;
    }

    public void delete(long id) throws SQLException {
        for (String table : List.of("MODELLO_STAMPI_AMMESSI", "MODELLO_STAMPO_RIF"))
            try (var q = c.prepareStatement("DELETE FROM TON." + table + " WHERE modello_id=?")) {
                q.setLong(1, id);
                q.executeUpdate();
            }
    }

    public void insert(long id, Moulds moulds) throws SQLException {
        if (moulds == null) return;
        insert("MODELLO_STAMPO_RIF", id, List.of(moulds.reference()));
        insert("MODELLO_STAMPI_AMMESSI", id, moulds.admitted());
    }

    private void insert(String table, long id, List<Mould> entries) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "INSERT INTO TON."
                                + table
                                + "(modello_id,stampo_id,stampo_revisione,codice,nome,diametro_mm)"
                                + " VALUES (?,?,?,?,?,?)")) {
            for (var m : entries) {
                q.setLong(1, id);
                q.setLong(2, m.id());
                q.setInt(3, m.revision());
                q.setString(4, m.code());
                q.setString(5, m.name());
                q.setBigDecimal(6, m.diameter());
                q.addBatch();
            }
            q.executeBatch();
        }
    }

    public void validate(Moulds moulds, boolean approve) throws SQLException {
        if (moulds == null) throw invalid("Seleziona lo stampo di riferimento del modello.");
        var equipment = new EquipmentRepository(c);
        for (var m : moulds.all().stream().sorted(Comparator.comparingLong(Mould::id)).toList()) {
            try (var q =
                    c.prepareStatement(
                            "UPDATE TON.STAMPI SET versione_modifica=versione_modifica WHERE"
                                    + " stampo_id=?")) {
                q.setLong(1, m.id());
                if (q.executeUpdate() != 1)
                    throw invalid("Uno stampo selezionato non è più disponibile.");
            }
            var actual = equipment.load(Kind.MOULDS, m.id());
            var snapshot =
                    new Mould(
                            actual.id(),
                            actual.revision(),
                            actual.code(),
                            actual.name(),
                            actual.dimension());
            if (!m.equals(snapshot))
                throw invalid(
                        m.code()
                                + ": dati dello stampo aggiornati. Ricarica l’elenco e riseleziona"
                                + " lo stampo.");
            if (approve && !actual.active())
                throw invalid(
                        m.code()
                                + ": stampo archiviato. Rimuovilo dalla selezione prima di"
                                + " approvare.");
        }
    }
}
