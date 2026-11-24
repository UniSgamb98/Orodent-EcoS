package com.orodent.ecos.ton.features.production.repository;

import static com.orodent.ecos.ton.features.production.model.ProductionExecution.*;

import com.orodent.ecos.ton.features.production.model.Production.State;

import java.sql.*;
import java.util.*;

/** All quantities and identities are read from the saved preparation, never the client request. */
public final class ProductionExecutionRepository {
    private final Connection c;

    public ProductionExecutionRepository(Connection c) {
        this.c = c;
    }

    public Workspace workspace(long planId) throws SQLException {
        var saved = new ProductionRepository(c).load(planId);
        if (saved.state() != State.PREPARATA)
            throw new IllegalArgumentException("Prima prepara la produzione.");
        var counts = new HashMap<Integer, Integer>();
        try (var q =
                c.prepareStatement(
                        "SELECT numero_riga,COUNT(*) FROM TON.DISCHI_PRODOTTI WHERE piano_id=?"
                            + " GROUP BY numero_riga")) {
            q.setLong(1, planId);
            try (var r = q.executeQuery()) {
                while (r.next()) counts.put(r.getInt(1), r.getInt(2));
            }
        }
        var rows = new ArrayList<Row>();
        int number = 0;
        for (var format : saved.plan().formats()) {
            number++;
            rows.add(new Row(number, format, counts.getOrDefault(number, 0)));
        }
        return new Workspace(saved, rows);
    }

    public Receipt register(Workspace expected, Request request) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "UPDATE TON.PIANI_PRODUZIONE SET versione_modifica=versione_modifica+1"
                            + " WHERE piano_id=? AND versione_modifica=? AND stato='PREPARATA'")) {
            q.setLong(1, expected.prepared().id());
            q.setInt(2, expected.prepared().token());
            if (q.executeUpdate() != 1)
                throw new IllegalArgumentException(
                        "La produzione è cambiata o è già stata registrata da un’altra postazione."
                            + " Aggiorna i dati.");
        }
        var current = workspace(expected.prepared().id());
        var available = new HashSet<Long>();
        current.rows().forEach(row -> available.add(row.format().article().id()));
        if (!available.containsAll(request.quantities().keySet()))
            throw new IllegalArgumentException(
                    "La richiesta contiene articoli estranei alla produzione.");
        for (var row : current.rows())
            if (request.quantities().getOrDefault(row.format().article().id(), 0) > row.remaining())
                throw new IllegalArgumentException(
                        row.format().article().code()
                                + ": la quantità supera i dischi ancora da registrare.");
        long execution;
        try (var q =
                c.prepareStatement(
                        "INSERT INTO TON.ESECUZIONI_PIANO(piano_id,data_produzione,note) VALUES"
                            + " (?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            q.setLong(1, current.prepared().id());
            q.setDate(2, java.sql.Date.valueOf(request.date()));
            q.setString(3, request.notes());
            q.executeUpdate();
            try (var keys = q.getGeneratedKeys()) {
                keys.next();
                execution = keys.getLong(1);
            }
        }
        int total = 0;
        try (var q =
                c.prepareStatement(
                        "INSERT INTO"
                            + " TON.DISCHI_PRODOTTI(esecuzione_id,piano_id,numero_riga,progressivo,articolo_id,prodotto_id,articolo_codice,prodotto_codice,linea_codice,colore,diametro_mm,altezza_mm,specifica_prodotto_id,specifica_articolo_id)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            var plan = current.prepared().plan();
            for (var row : current.rows()) {
                var article = row.format().article();
                int count = request.quantities().getOrDefault(article.id(), 0);
                for (int n = 1; n <= count; n++) {
                    q.setLong(1, execution);
                    q.setLong(2, current.prepared().id());
                    q.setInt(3, row.number());
                    q.setInt(4, row.produced() + n);
                    q.setLong(5, article.id());
                    q.setLong(6, plan.product().id());
                    q.setString(7, article.code());
                    q.setString(8, plan.product().code());
                    q.setString(9, plan.product().lineCode());
                    q.setString(10, Objects.requireNonNullElse(plan.product().color(), ""));
                    q.setBigDecimal(11, article.diameter());
                    q.setBigDecimal(12, article.height());
                    q.setObject(
                            13,
                            plan.schemaVersion() >= 2 ? article.specificationId() : null,
                            Types.BIGINT);
                    q.setObject(
                            14,
                            plan.schemaVersion() == 1 ? article.specificationId() : null,
                            Types.BIGINT);
                    q.addBatch();
                    total++;
                }
            }
            q.executeBatch();
        }
        return new Receipt(current.prepared(), total, execution);
    }
}
