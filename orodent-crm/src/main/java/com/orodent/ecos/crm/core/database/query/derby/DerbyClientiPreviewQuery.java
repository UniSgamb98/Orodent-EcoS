package com.orodent.ecos.crm.core.database.query.derby;

import com.orodent.ecos.crm.core.country.Countries;
import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.query.ClientiPreviewQuery;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class DerbyClientiPreviewQuery implements ClientiPreviewQuery {
    private static final Set<String> ORDER_COLUMNS =
            Set.of(
                    "C.RAGIONE_SOCIALE",
                    "C.TIPO_CLIENTE",
                    "REFERENTE",
                    "OPERATORE",
                    "C.STATO_TRATTATIVA",
                    "ULTIMO_CONTATTO",
                    "L.PROSSIMO_CONTATTO");
    private final Database database;

    public DerbyClientiPreviewQuery(Database database) {
        this.database = database;
    }

    @Override
    public ClientePreviewPage findPage(
            int offset,
            int pageSize,
            String searchText,
            UUID operatoreId,
            String tipoCliente,
            String statoTrattativa,
            String paese,
            String orderByColumn,
            boolean ascending,
            boolean myFavoritesOnly,
            boolean overdueOnly) {
        int start = Math.max(0, offset), size = Math.max(1, pageSize);
        List<String> conditions = new ArrayList<>();
        List<String> parameters = new ArrayList<>();
        if (operatoreId != null) {
            conditions.add("C.OPERATORE_ID = ?");
            parameters.add(operatoreId.toString());
        }
        addFilter(conditions, parameters, "C.TIPO_CLIENTE", tipoCliente);
        addFilter(conditions, parameters, "C.STATO_TRATTATIVA", statoTrattativa);
        addCountry(conditions, parameters, paese);
        if (myFavoritesOnly) {
            conditions.add(
                    "EXISTS (SELECT 1 FROM CLIENTI_PREFERITI F WHERE F.CLIENTE_ID=C.ID AND"
                        + " F.OPERATORE_ID=?)");
            parameters.add(database.operators().currentOperatoreId().toString());
        }
        if (overdueOnly) {
            conditions.add(
                    "EXISTS (SELECT 1 FROM INTERAZIONI D WHERE D.CLIENTE_ID=C.ID"
                            + " AND D.PROSSIMO_CONTATTO < CAST(? AS DATE) AND "
                            + latestCall("D")
                            + ")");
            parameters.add(LocalDate.now().toString());
        }
        if (searchText != null && !searchText.isBlank()) {
            conditions.add(
                    "(LOWER(C.RAGIONE_SOCIALE) LIKE ? OR LOWER(C.TIPO_CLIENTE) LIKE ? OR"
                        + " LOWER(C.STATO_TRATTATIVA) LIKE ? OR EXISTS (SELECT 1 FROM"
                        + " CONTATTI_CLIENTE CC WHERE CC.CLIENTE_ID=C.ID AND LOWER(CC.DESCRIZIONE)"
                        + " LIKE ?) OR EXISTS (SELECT 1 FROM TELEFONI_CLIENTE T WHERE"
                        + " T.CLIENTE_ID=C.ID AND LOWER(T.DESCRIZIONE) LIKE ?) OR EXISTS (SELECT 1"
                        + " FROM EMAIL_CLIENTE E WHERE E.CLIENTE_ID=C.ID AND LOWER(E.DESCRIZIONE)"
                        + " LIKE ?))");
            for (int i = 0; i < 6; i++)
                parameters.add("%" + searchText.strip().toLowerCase(Locale.ROOT) + "%");
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        try {
            long count;
            try (var q =
                    database.getConnection()
                            .prepareStatement("SELECT COUNT(*) FROM CLIENTI C" + where)) {
                bind(q, parameters);
                try (var rs = q.executeQuery()) {
                    rs.next();
                    count = rs.getLong(1);
                }
            }
            String order =
                    orderByColumn != null && ORDER_COLUMNS.contains(orderByColumn)
                            ? orderByColumn
                            : "C.RAGIONE_SOCIALE";
            String sql =
                    "SELECT C.ID, C.RAGIONE_SOCIALE, C.TIPO_CLIENTE, C.STATO_TRATTATIVA,"
                        + " R.REFERENTE, A.INDIRIZZO, CASE WHEN TRIM(COALESCE(O.NOME,'') || ' ' ||"
                        + " COALESCE(O.COGNOME,''))='' THEN COALESCE(O.USERNAME,'') ELSE"
                        + " TRIM(COALESCE(O.NOME,'') || ' ' || COALESCE(O.COGNOME,'')) END AS"
                        + " OPERATORE, I.ULTIMO_CONTATTO, L.PROSSIMO_CONTATTO, T.RECAPITO AS"
                        + " TELEFONO, E.RECAPITO AS EMAIL FROM CLIENTI C LEFT JOIN (SELECT"
                        + " CLIENTE_ID, MIN(DESCRIZIONE) AS REFERENTE FROM CONTATTI_CLIENTE GROUP"
                        + " BY CLIENTE_ID) R ON R.CLIENTE_ID=C.ID LEFT JOIN (SELECT CLIENTE_ID,"
                        + " COALESCE(MIN(CASE WHEN PRINCIPALE=1 THEN TRIM(COALESCE(INDIRIZZO,'') ||"
                        + " ' ' || COALESCE(NUMERO_CIVICO,'') || ' ' || COALESCE(CITTA,'')) END),"
                        + " MIN(TRIM(COALESCE(INDIRIZZO,'') || ' ' || COALESCE(NUMERO_CIVICO,'') ||"
                        + " ' ' || COALESCE(CITTA,'')))) AS INDIRIZZO FROM INDIRIZZI_CLIENTE GROUP"
                        + " BY CLIENTE_ID) A ON A.CLIENTE_ID=C.ID LEFT JOIN ECOS.OPERATORI O ON"
                        + " O.ID=C.OPERATORE_ID LEFT JOIN (SELECT CLIENTE_ID, MAX(DATA_CONTATTO) AS"
                        + " ULTIMO_CONTATTO FROM INTERAZIONI GROUP BY CLIENTE_ID) I ON"
                        + " I.CLIENTE_ID=C.ID LEFT JOIN INTERAZIONI L ON L.CLIENTE_ID=C.ID AND "
                            + latestCall("L")
                            + " "
                            + contactJoin("TELEFONI_CLIENTE", "T")
                            + contactJoin("EMAIL_CLIENTE", "E")
                            + where
                            + " ORDER BY "
                            + order
                            + (ascending ? " ASC" : " DESC")
                            + ", C.ID OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            List<ClientePreviewRecord> records = new ArrayList<>();
            try (var q = database.getConnection().prepareStatement(sql)) {
                bind(q, parameters);
                q.setInt(parameters.size() + 1, start);
                q.setInt(parameters.size() + 2, size);
                try (var rs = q.executeQuery()) {
                    while (rs.next())
                        records.add(
                                new ClientePreviewRecord(
                                        UUID.fromString(rs.getString("ID")),
                                        text(rs, "RAGIONE_SOCIALE"),
                                        text(rs, "TIPO_CLIENTE"),
                                        text(rs, "REFERENTE"),
                                        text(rs, "INDIRIZZO"),
                                        text(rs, "OPERATORE"),
                                        text(rs, "STATO_TRATTATIVA"),
                                        date(rs, "ULTIMO_CONTATTO"),
                                        List.of(),
                                        text(rs, "TELEFONO"),
                                        text(rs, "EMAIL"),
                                        date(rs, "PROSSIMO_CONTATTO")));
                }
            }
            var countries = loadCountries(records);
            var enriched =
                    records.stream()
                            .map(
                                    r ->
                                            new ClientePreviewRecord(
                                                    r.clienteId(),
                                                    r.ragioneSociale(),
                                                    r.tipoCliente(),
                                                    r.referente(),
                                                    r.indirizzo(),
                                                    r.operatore(),
                                                    r.statoTrattativa(),
                                                    r.ultimoContatto(),
                                                    countries.getOrDefault(
                                                            r.clienteId(), List.of()),
                                                    r.telefono(),
                                                    r.email(),
                                                    r.prossimoContatto()))
                            .toList();
            return new ClientePreviewPage(enriched, start, size, count);
        } catch (SQLException e) {
            throw new RuntimeException("Errore caricamento anagrafiche CRM.", e);
        }
    }

    private static String latestCall(String alias) {
        return "NOT EXISTS (SELECT 1 FROM INTERAZIONI X WHERE X.CLIENTE_ID="
                + alias
                + ".CLIENTE_ID AND ("
                + callDate("X")
                + ">"
                + callDate(alias)
                + " OR ("
                + callDate("X")
                + "="
                + callDate(alias)
                + " AND (X.CREATED_AT>"
                + alias
                + ".CREATED_AT OR (X.CREATED_AT="
                + alias
                + ".CREATED_AT AND X.ID>"
                + alias
                + ".ID)))))";
    }

    private static String callDate(String alias) {
        return "COALESCE(" + alias + ".DATA_CONTATTO, DATE(" + alias + ".CREATED_AT))";
    }

    private static String contactJoin(String table, String alias) {
        return "LEFT JOIN (SELECT CLIENTE_ID, COALESCE(MIN(CASE WHEN CONTATTO_ID IS NULL THEN"
                + " DESCRIZIONE END), MIN(DESCRIZIONE)) AS RECAPITO FROM "
                + table
                + " GROUP BY CLIENTE_ID) "
                + alias
                + " ON "
                + alias
                + ".CLIENTE_ID=C.ID ";
    }

    private static void addFilter(
            List<String> conditions, List<String> params, String column, String value) {
        if (value != null && !value.isBlank()) {
            conditions.add(column + " = ?");
            params.add(value.strip());
        }
    }

    private void addCountry(List<String> conditions, List<String> params, String country) {
        if (country == null || country.isBlank()) return;
        if (Countries.MISSING.equals(country)) {
            conditions.add(
                    "NOT EXISTS (SELECT 1 FROM INDIRIZZI_CLIENTE P WHERE P.CLIENTE_ID=C.ID AND"
                            + " TRIM(COALESCE(P.PAESE,''))<>'')");
            return;
        }
        String code = Countries.code(country).orElse(country);
        var values =
                new DerbyClientiFilterQuery(database)
                        .findPaesiCliente().stream()
                                .filter(v -> v != null && !v.isBlank())
                                .filter(
                                        v ->
                                                Countries.REVIEW.equals(code)
                                                        ? Countries.code(v).isEmpty()
                                                        : Countries.code(v)
                                                                .filter(code::equals)
                                                                .isPresent())
                                .map(String::strip)
                                .distinct()
                                .toList();
        if (values.isEmpty()) {
            conditions.add("1=0");
            return;
        }
        conditions.add(
                "EXISTS (SELECT 1 FROM INDIRIZZI_CLIENTE P WHERE P.CLIENTE_ID=C.ID AND"
                        + " TRIM(P.PAESE) IN ("
                        + String.join(",", Collections.nCopies(values.size(), "?"))
                        + "))");
        params.addAll(values);
    }

    private Map<UUID, List<String>> loadCountries(List<ClientePreviewRecord> records)
            throws SQLException {
        Map<UUID, List<String>> countries = new HashMap<>();
        if (records.isEmpty()) return countries;
        String sql =
                "SELECT CLIENTE_ID, PAESE FROM INDIRIZZI_CLIENTE WHERE CLIENTE_ID IN ("
                        + String.join(",", Collections.nCopies(records.size(), "?"))
                        + ")";
        try (var q = database.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < records.size(); i++)
                q.setString(i + 1, records.get(i).clienteId().toString());
            try (var rs = q.executeQuery()) {
                while (rs.next())
                    countries
                            .computeIfAbsent(
                                    UUID.fromString(rs.getString(1)), ignored -> new ArrayList<>())
                            .add(Objects.toString(rs.getString(2), ""));
            }
        }
        return countries;
    }

    private static void bind(PreparedStatement q, List<String> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) q.setString(i + 1, parameters.get(i));
    }

    private static String text(ResultSet rs, String col) throws SQLException {
        return Objects.toString(rs.getString(col), "");
    }

    private static LocalDate date(ResultSet rs, String col) throws SQLException {
        return rs.getDate(col) == null ? null : rs.getDate(col).toLocalDate();
    }
}
