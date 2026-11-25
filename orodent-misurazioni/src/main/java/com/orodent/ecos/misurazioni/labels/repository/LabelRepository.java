package com.orodent.ecos.misurazioni.labels.repository;

import static com.orodent.ecos.misurazioni.labels.model.Labels.*;

import com.google.gson.JsonParser;
import com.orodent.ecos.documents.DocumentPaper;
import com.orodent.ecos.misurazioni.jobs.model.MeasurementJobs.*;
import com.orodent.ecos.misurazioni.jobs.repository.MeasurementJobsRepository;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/** Issued labels and their physical disc links are append-only. */
public final class LabelRepository {
    private final Connection c;

    public LabelRepository(Connection c) {
        this.c = c;
    }

    public Source source(Key key, LocalDate today) throws SQLException {
        Job job = new MeasurementJobsRepository(c).load(key, today).job();
        long disc = job.readings().getFirst().discId();
        Long assigned = null;
        LocalDate produced;
        String gtin, name, line;
        int revision;
        try (var q =
                c.prepareStatement(
                        "SELECT"
                            + " cp.etichetta_modello_id,a.gtin,a.versione_modifica,e.data_produzione,p.contenuto_piano"
                            + " FROM TON.DISCHI_PRODOTTI d JOIN TON.ARTICOLI a ON"
                            + " a.articolo_id=d.articolo_id JOIN TON.ESECUZIONI_PIANO e ON"
                            + " e.esecuzione_id=d.esecuzione_id JOIN TON.PIANI_PRODUZIONE p ON"
                            + " p.piano_id=d.piano_id LEFT JOIN TON.CONFIGURAZIONI_PRODOTTO cp ON"
                            + " cp.configurazione_id=d.specifica_prodotto_id WHERE d.disco_id=?")) {
            q.setLong(1, disc);
            try (var rs = q.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Disco non disponibile.");
                assigned = (Long) rs.getObject(1);
                gtin = rs.getString(2);
                revision = rs.getInt(3);
                produced = rs.getDate(4).toLocalDate();
                var product =
                        JsonParser.parseString(rs.getString(5))
                                .getAsJsonObject()
                                .getAsJsonObject("product");
                name =
                        product != null && product.has("name")
                                ? product.get("name").getAsString()
                                : job.name();
                line =
                        product != null && product.has("lineName")
                                ? product.get("lineName").getAsString()
                                : job.line();
            }
        }
        var templates = new ArrayList<Template>();
        String templateFilter =
                assigned == null
                        ? "stato='APPROVATA'"
                        : "modello_documento_id=? AND stato IN ('APPROVATA','RITIRATA')";
        try (var q =
                c.prepareStatement(
                        "SELECT modello_documento_id,codice,numero_versione,nome,contenuto_modello"
                                + " FROM TON.MODELLI_DOCUMENTO WHERE tipo='ETICHETTA' AND "
                                + templateFilter
                                + " ORDER BY codice,numero_versione DESC")) {
            if (assigned != null) q.setLong(1, assigned);
            try (var rs = q.executeQuery()) {
                while (rs.next())
                    templates.add(
                            new Template(
                                    rs.getLong(1),
                                    rs.getString(2),
                                    rs.getInt(3),
                                    rs.getString(4),
                                    rs.getString(5)));
            }
        }
        return new Source(
                job, assigned, templates, produced, gtin, name, line, revision, latest(key));
    }

    public Issued latest(Key key) throws SQLException {
        try (var q =
                c.prepareStatement(
                        "SELECT"
                            + " etichetta_id,modello_documento_id,dati,contenuto_html,larghezza_mm,altezza_mm,margine_mm"
                            + " FROM TON.ETICHETTE_EMESSE WHERE ciclo_id=? AND piano_id=? AND"
                            + " numero_riga=? ORDER BY etichetta_id DESC FETCH FIRST ROW ONLY")) {
            q.setLong(1, key.cycleId());
            q.setLong(2, key.planId());
            q.setInt(3, key.row());
            try (var rs = q.executeQuery()) {
                return rs.next()
                        ? new Issued(
                                rs.getLong(1),
                                rs.getLong(2),
                                rs.getString(3),
                                rs.getString(4),
                                new DocumentPaper(
                                        rs.getDouble(5), rs.getDouble(6), rs.getDouble(7)))
                        : null;
            }
        }
    }

    public Issued issue(Prepared prepared, Operator operator, LocalDate today) throws SQLException {
        Source canonical = source(prepared.source().job().key(), today);
        var expected = prepared.source();
        if (canonical.articleRevision() != expected.articleRevision()
                || !Objects.equals(canonical.assignedTemplate(), expected.assignedTemplate())
                || !canonical.job().readings().stream()
                        .map(Reading::discId)
                        .toList()
                        .equals(expected.job().readings().stream().map(Reading::discId).toList()))
            throw new IllegalStateException(
                    "I dati dell’articolo o del lavoro sono cambiati. Riapri la stampa etichetta.");
        Template actual =
                canonical.templates().stream()
                        .filter(t -> t.id() == prepared.template().id())
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Il modello non è più disponibile per questo"
                                                        + " lavoro."));
        if (!actual.content().equals(prepared.template().content()))
            throw new IllegalStateException("Il modello è cambiato. Riapri la stampa.");
        if (canonical.assignedTemplate() != null && canonical.assignedTemplate() != actual.id())
            throw new IllegalArgumentException(
                    "Usa la versione etichetta della specifica di produzione.");
        if (!com.orodent.ecos.misurazioni.labels.model.LabelData.payload(
                                canonical, prepared.expiry())
                        .equals(prepared.data())
                || !DocumentPaper.from(actual.content()).equals(prepared.paper()))
            throw new IllegalArgumentException(
                    "I dati dell’anteprima non corrispondono al lavoro. Riapri la stampa.");
        if (prepared.html() == null
                || prepared.html().length() > 16_000_000
                || prepared.data() == null
                || prepared.data().length() > 20_000)
            throw new IllegalArgumentException("Contenuto etichetta non valido.");
        long id;
        var key = canonical.job().key();
        try (var q =
                c.prepareStatement(
                        "INSERT INTO"
                            + " TON.ETICHETTE_EMESSE(modello_documento_id,ciclo_id,piano_id,numero_riga,dati,contenuto_html,larghezza_mm,altezza_mm,margine_mm,operatore_id,operatore_nome)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            q.setLong(1, actual.id());
            q.setLong(2, key.cycleId());
            q.setLong(3, key.planId());
            q.setInt(4, key.row());
            q.setString(5, prepared.data());
            q.setString(6, prepared.html());
            q.setDouble(7, prepared.paper().width());
            q.setDouble(8, prepared.paper().height());
            q.setDouble(9, prepared.paper().margin());
            q.setString(10, operator == null ? null : operator.id());
            q.setString(11, operator == null ? null : operator.name());
            q.executeUpdate();
            try (var keys = q.getGeneratedKeys()) {
                keys.next();
                id = keys.getLong(1);
            }
        }
        try (var q =
                c.prepareStatement(
                        "INSERT INTO TON.ETICHETTA_DISCHI(etichetta_id,disco_id) VALUES (?,?)")) {
            for (var reading : canonical.job().readings()) {
                q.setLong(1, id);
                q.setLong(2, reading.discId());
                q.addBatch();
            }
            q.executeBatch();
        }
        return new Issued(id, actual.id(), prepared.data(), prepared.html(), prepared.paper());
    }
}
