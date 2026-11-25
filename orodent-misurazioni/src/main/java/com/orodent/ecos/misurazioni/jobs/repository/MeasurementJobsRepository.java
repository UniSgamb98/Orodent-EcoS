package com.orodent.ecos.misurazioni.jobs.repository;

import static com.orodent.ecos.misurazioni.jobs.model.MeasurementJobs.*;

import com.orodent.ecos.database.CubatureRuleVersions;
import com.orodent.ecos.misurazioni.model.MeasurementBatch.Measurement;

import java.sql.*;
import java.time.*;
import java.util.*;

/** Raw readings are append-only, including partial pairs, corrections and clearing a value. */
public final class MeasurementJobsRepository {
    private final Connection c;
    private static final String SELECT =
            """
SELECT d.*,c.ciclo_id,c.data_prevista,c.terminato_il,c.programma_id,c.forno_codice,c.forno_nome,
       l.codice AS lotto,COALESCE(p.descrizione,d.prodotto_codice) AS prodotto_nome,
       COALESCE(li.nome,d.linea_codice) AS linea_nome,
       COALESCE(m.calcolo_versione_id,cp.calcolo_versione_id) AS regola_versione,
       m.numero_revisione,m.micrometro,m.bilancia
FROM TON.DISCHI_PRODOTTI d
JOIN TON.CARICO_DISCHI a ON a.disco_id=d.disco_id
JOIN TON.CICLI_COTTURA c ON c.ciclo_id=a.ciclo_id
JOIN TON.LOTTI_COTTURA l ON l.lotto_id=a.lotto_id
JOIN TON.PRODOTTI p ON p.prodotto_id=d.prodotto_id
LEFT JOIN TON.LINEE li ON li.codice=d.linea_codice
LEFT JOIN TON.CONFIGURAZIONI_PRODOTTO cp ON cp.configurazione_id=d.specifica_prodotto_id
LEFT JOIN TON.DISCO_MISURE_REVISIONI m ON m.disco_id=d.disco_id
  AND NOT EXISTS(SELECT 1 FROM TON.DISCO_MISURE_REVISIONI n WHERE n.disco_id=m.disco_id AND n.numero_revisione>m.numero_revisione)
WHERE c.caricato_il IS NOT NULL AND c.stato<>'ANNULLATA'
""";

    public MeasurementJobsRepository(Connection c) {
        this.c = c;
    }

    public List<Job> list(LocalDate today) throws SQLException {
        return jobs(null).stream()
                .filter(j -> j.touched() || j.finished() != null && !j.finished().isAfter(today))
                .toList();
    }

    public Detail load(Key key, LocalDate today) throws SQLException {
        Job job =
                jobs(key).stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Lavoro non disponibile."));
        if (!job.touched() && (job.finished() == null || job.finished().isAfter(today)))
            throw new IllegalArgumentException(
                    "La fine prevista della cottura non è ancora stata raggiunta.");
        var rules =
                job.readings().stream()
                        .map(Reading::ruleVersionId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        String label =
                rules.isEmpty()
                        ? "Regola di cubaggio non collegata nella specifica storica."
                        : rules.stream()
                                .map(
                                        id -> {
                                            try {
                                                return CubatureRuleVersions.load(c, id).toString();
                                            } catch (SQLException e) {
                                                throw new IllegalStateException(e);
                                            }
                                        })
                                .collect(java.util.stream.Collectors.joining(" · "));
        return new Detail(job, label);
    }

    public Detail save(
            Detail expected, List<Measurement> values, Operator operator, LocalDate today)
            throws SQLException {
        if (values.size() != expected.job().readings().size())
            throw new IllegalArgumentException(
                    "Le misure devono corrispondere ai dischi del lavoro.");
        var changed = new ArrayList<Integer>();
        for (int i = 0; i < values.size(); i++) {
            Measurement value = values.get(i);
            if (value == null
                    || value.micrometer() == null
                    || value.scale() == null
                    || value.micrometer().length() > 4000
                    || value.scale().length() > 1000)
                throw new IllegalArgumentException("Misura troppo lunga o non valida.");
            if (!value.equals(expected.job().readings().get(i).value())) changed.add(i);
        }
        // Stable physical ID order makes concurrent batch corrections deterministic.
        changed.sort(Comparator.comparingLong(i -> expected.job().readings().get(i).discId()));
        for (int i : changed) {
            long id = expected.job().readings().get(i).discId();
            try (var q =
                    c.prepareStatement(
                            "UPDATE TON.DISCHI_PRODOTTI SET progressivo=progressivo WHERE"
                                    + " disco_id=?")) {
                q.setLong(1, id);
                if (q.executeUpdate() != 1) throw new Conflict();
            }
        }
        Detail actual = load(expected.job().key(), today);
        var byId = new HashMap<Long, Reading>();
        actual.job().readings().forEach(r -> byId.put(r.discId(), r));
        if (actual.job().readings().size() != values.size()) throw new Conflict();
        try (var q =
                c.prepareStatement(
                        "INSERT INTO"
                            + " TON.DISCO_MISURE_REVISIONI(disco_id,numero_revisione,micrometro,bilancia,calcolo_versione_id,operatore_id,operatore_nome)"
                            + " VALUES (?,?,?,?,?,?,?)")) {
            for (int i : changed) {
                Reading baseline = expected.job().readings().get(i),
                        canonical = byId.get(baseline.discId());
                if (canonical == null || canonical.revision() != baseline.revision())
                    throw new Conflict();
                q.setLong(1, canonical.discId());
                q.setInt(2, canonical.revision() + 1);
                q.setString(3, values.get(i).micrometer());
                q.setString(4, values.get(i).scale());
                q.setObject(5, canonical.ruleVersionId(), Types.BIGINT);
                q.setString(6, operator == null ? null : operator.id());
                q.setString(7, operator == null ? null : operator.name());
                q.addBatch();
            }
            q.executeBatch();
        }
        return load(expected.job().key(), today);
    }

    private List<Job> jobs(Key key) throws SQLException {
        var durations = new HashMap<Long, Long>();
        try (var q = c.createStatement();
                var r =
                        q.executeQuery(
                                "SELECT programma_id,SUM(CAST(durata_rampa_minuti AS"
                                        + " BIGINT)+durata_mantenimento_minuti) FROM"
                                        + " TON.PROGRAMMA_PASSI GROUP BY programma_id")) {
            while (r.next()) durations.put(r.getLong(1), r.getLong(2));
        }
        var groups = new LinkedHashMap<Key, List<Reading>>();
        var headers = new LinkedHashMap<Key, Job>();
        try (var q =
                c.prepareStatement(
                        SELECT
                                + (key == null
                                        ? ""
                                        : " AND c.ciclo_id=? AND d.piano_id=? AND d.numero_riga=?")
                                + " ORDER BY"
                                + " d.linea_codice,d.prodotto_codice,c.data_prevista,c.ciclo_id,d.piano_id,d.numero_riga,d.progressivo")) {
            if (key != null) {
                q.setLong(1, key.cycleId());
                q.setLong(2, key.planId());
                q.setInt(3, key.row());
            }
            try (var r = q.executeQuery()) {
                while (r.next()) {
                    var identity =
                            new Key(
                                    r.getLong("ciclo_id"),
                                    r.getLong("piano_id"),
                                    r.getInt("numero_riga"));
                    Number rule = (Number) r.getObject("regola_versione");
                    groups.computeIfAbsent(identity, k -> new ArrayList<>())
                            .add(
                                    new Reading(
                                            r.getLong("disco_id"),
                                            r.getInt("numero_revisione"),
                                            new Measurement(
                                                    Objects.requireNonNullElse(
                                                            r.getString("micrometro"), ""),
                                                    Objects.requireNonNullElse(
                                                            r.getString("bilancia"), "")),
                                            rule == null ? null : rule.longValue()));
                    if (!headers.containsKey(identity)) {
                        Long minutes = durations.get(r.getLong("programma_id"));
                        LocalDate end =
                                r.getTimestamp("terminato_il") != null
                                        ? r.getTimestamp("terminato_il")
                                                .toLocalDateTime()
                                                .toLocalDate()
                                        : minutes == null
                                                ? null
                                                : r.getDate("data_prevista")
                                                        .toLocalDate()
                                                        .plusDays((minutes + 1439) / 1440);
                        headers.put(
                                identity,
                                new Job(
                                        identity,
                                        r.getString("prodotto_codice"),
                                        r.getString("prodotto_nome"),
                                        r.getString("linea_nome"),
                                        r.getString("articolo_codice"),
                                        r.getBigDecimal("diametro_mm"),
                                        r.getBigDecimal("altezza_mm"),
                                        r.getString("lotto"),
                                        r.getString("forno_codice")
                                                + " · "
                                                + r.getString("forno_nome"),
                                        end,
                                        List.of()));
                    }
                }
            }
        }
        return headers.entrySet().stream()
                .map(
                        e -> {
                            var h = e.getValue();
                            return new Job(
                                    h.key(),
                                    h.product(),
                                    h.name(),
                                    h.line(),
                                    h.article(),
                                    h.diameter(),
                                    h.height(),
                                    h.lot(),
                                    h.furnace(),
                                    h.finished(),
                                    groups.get(e.getKey()));
                        })
                .toList();
    }
}
