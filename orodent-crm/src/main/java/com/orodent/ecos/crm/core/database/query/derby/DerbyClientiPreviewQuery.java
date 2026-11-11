package com.orodent.ecos.crm.core.database.query.derby;

import com.orodent.ecos.crm.core.database.Database;
import com.orodent.ecos.crm.core.database.query.ClientiPreviewQuery;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DerbyClientiPreviewQuery implements ClientiPreviewQuery {

    private static final Set<String> ALLOWED_ORDER_COLUMNS =
            Set.of(
                    "C.RAGIONE_SOCIALE",
                    "C.TIPO_CLIENTE",
                    "REFERENTE",
                    "OPERATORE",
                    "C.STATO_TRATTATIVA",
                    "ULTIMO_CONTATTO");

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
            String orderByColumn,
            boolean ascending) {
        int safeOffset = Math.max(0, offset);
        int safePageSize = Math.max(1, pageSize);
        String cleanSearchText = cleanSearchText(searchText);
        boolean hasSearch = !cleanSearchText.isBlank();
        String cleanTipoCliente = cleanFilterText(tipoCliente);
        String cleanStatoTrattativa = cleanFilterText(statoTrattativa);
        long totalRows =
                countAll(cleanSearchText, operatoreId, cleanTipoCliente, cleanStatoTrattativa);
        String sql =
                buildPreviewSql(
                        hasSearch,
                        operatoreId != null,
                        !cleanTipoCliente.isBlank(),
                        !cleanStatoTrattativa.isBlank(),
                        orderByColumn,
                        ascending);

        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            int parameterIndex =
                    bindFilterParameters(
                            statement,
                            cleanSearchText,
                            hasSearch,
                            operatoreId,
                            cleanTipoCliente,
                            cleanStatoTrattativa,
                            1);
            statement.setInt(parameterIndex, safeOffset);
            statement.setInt(parameterIndex + 1, safePageSize);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ClientePreviewRecord> previews = new ArrayList<>();
                while (resultSet.next()) {
                    previews.add(
                            new ClientePreviewRecord(
                                    getUuid(resultSet, "ID"),
                                    valueOrEmpty(resultSet.getString("RAGIONE_SOCIALE")),
                                    valueOrEmpty(resultSet.getString("TIPO_CLIENTE")),
                                    valueOrEmpty(resultSet.getString("REFERENTE")),
                                    valueOrEmpty(resultSet.getString("INDIRIZZO")),
                                    valueOrEmpty(resultSet.getString("OPERATORE")),
                                    valueOrEmpty(resultSet.getString("STATO_TRATTATIVA")),
                                    resultSet.getDate("ULTIMO_CONTATTO") == null
                                            ? null
                                            : resultSet.getDate("ULTIMO_CONTATTO").toLocalDate()));
                }
                return new ClientePreviewPage(previews, safeOffset, safePageSize, totalRows);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore caricamento anteprima clienti.", e);
        }
    }

    private String previewAggregateJoin(String tableName, String alias, String valueAlias) {
        return "LEFT JOIN ("
                + "SELECT CLIENTE_ID, MIN(DESCRIZIONE) AS "
                + valueAlias
                + " "
                + "FROM "
                + tableName
                + " "
                + "GROUP BY CLIENTE_ID"
                + ") "
                + alias
                + " ON "
                + alias
                + ".CLIENTE_ID = C.ID ";
    }

    private String buildPreviewSql(
            boolean hasSearch,
            boolean hasOperatoreFilter,
            boolean hasTipoClienteFilter,
            boolean hasStatoFilter,
            String orderByColumn,
            boolean ascending) {
        return previewSelectClause()
                + previewJoins()
                + filterWhereClause(
                        hasSearch, hasOperatoreFilter, hasTipoClienteFilter, hasStatoFilter)
                + orderByClause(orderByColumn, ascending)
                + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
    }

    private String previewSelectClause() {
        return "SELECT C.ID, C.RAGIONE_SOCIALE, C.TIPO_CLIENTE, C.STATO_TRATTATIVA,"
                + " COALESCE(R.REFERENTE, '') AS REFERENTE, COALESCE(A.INDIRIZZO, '') AS"
                + " INDIRIZZO, CASE WHEN TRIM(COALESCE(O.NOME, '') || ' ' || COALESCE(O.COGNOME,"
                + " '')) = '' THEN COALESCE(O.USERNAME, '') ELSE TRIM(COALESCE(O.NOME, '') || '"
                + " ' || COALESCE(O.COGNOME, '')) END AS OPERATORE, I.ULTIMO_CONTATTO FROM"
                + " CLIENTI C ";
    }

    private String previewJoins() {
        return previewAggregateJoin("CONTATTI_CLIENTE", "R", "REFERENTE")
                + previewAddressJoin()
                + "LEFT JOIN ECOS.OPERATORI O ON O.ID = C.OPERATORE_ID LEFT JOIN (SELECT"
                + " CLIENTE_ID, MAX(DATA_CONTATTO) AS ULTIMO_CONTATTO FROM INTERAZIONI GROUP BY"
                + " CLIENTE_ID) I ON I.CLIENTE_ID = C.ID ";
    }

    private String orderByClause(String orderByColumn, boolean ascending) {
        return "ORDER BY "
                + safeOrderColumn(orderByColumn)
                + (ascending ? " ASC" : " DESC")
                + ", C.ID ";
    }

    private String previewAddressJoin() {
        return "LEFT JOIN (SELECT CLIENTE_ID, COALESCE(MIN(CASE WHEN PRINCIPALE = 1 THEN INDIRIZZO"
                + " END), MIN(INDIRIZZO)) AS INDIRIZZO FROM INDIRIZZI_CLIENTE GROUP BY"
                + " CLIENTE_ID) A ON A.CLIENTE_ID = C.ID ";
    }

    private long countAll(
            String searchText, UUID operatoreId, String tipoCliente, String statoTrattativa) {
        boolean hasSearch = !searchText.isBlank();
        String sql =
                "SELECT COUNT(*) FROM CLIENTI C "
                        + filterWhereClause(
                                hasSearch,
                                operatoreId != null,
                                !tipoCliente.isBlank(),
                                !statoTrattativa.isBlank());
        try (PreparedStatement statement = database.getConnection().prepareStatement(sql)) {
            bindFilterParameters(
                    statement, searchText, hasSearch, operatoreId, tipoCliente, statoTrattativa, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Errore conteggio clienti.", e);
        }
    }

    private String filterWhereClause(
            boolean hasSearch,
            boolean hasOperatoreFilter,
            boolean hasTipoClienteFilter,
            boolean hasStatoTrattativaFilter) {
        if (!hasSearch
                && !hasOperatoreFilter
                && !hasTipoClienteFilter
                && !hasStatoTrattativaFilter) {
            return "";
        }

        List<String> conditions = new ArrayList<>();
        if (hasOperatoreFilter) {
            conditions.add("C.OPERATORE_ID = ?");
        }
        if (hasTipoClienteFilter) {
            conditions.add("C.TIPO_CLIENTE = ?");
        }
        if (hasStatoTrattativaFilter) {
            conditions.add("C.STATO_TRATTATIVA = ?");
        }
        if (hasSearch) {
            conditions.add(
                    "(LOWER(C.RAGIONE_SOCIALE) LIKE ? OR LOWER(C.TIPO_CLIENTE) LIKE ? OR"
                        + " LOWER(C.STATO_TRATTATIVA) LIKE ? OR EXISTS (SELECT 1 FROM"
                        + " CONTATTI_CLIENTE CC WHERE CC.CLIENTE_ID = C.ID AND"
                        + " LOWER(CC.DESCRIZIONE) LIKE ?) OR EXISTS (SELECT 1 FROM TELEFONI_CLIENTE"
                        + " T WHERE T.CLIENTE_ID = C.ID AND LOWER(T.DESCRIZIONE) LIKE ?) OR EXISTS"
                        + " (SELECT 1 FROM EMAIL_CLIENTE E WHERE E.CLIENTE_ID = C.ID AND"
                        + " LOWER(E.DESCRIZIONE) LIKE ?))");
        }
        return "WHERE " + String.join(" AND ", conditions) + " ";
    }

    private int bindFilterParameters(
            PreparedStatement statement,
            String searchText,
            boolean hasSearch,
            UUID operatoreId,
            String tipoCliente,
            String statoTrattativa,
            int startIndex)
            throws SQLException {
        int parameterIndex = startIndex;
        if (operatoreId != null) {
            statement.setString(parameterIndex++, operatoreId.toString());
        }
        if (!tipoCliente.isBlank()) {
            statement.setString(parameterIndex++, tipoCliente);
        }
        if (!statoTrattativa.isBlank()) {
            statement.setString(parameterIndex++, statoTrattativa);
        }
        if (!hasSearch) {
            return parameterIndex;
        }

        String pattern = "%" + searchText + "%";
        for (int i = 0; i < 6; i++) {
            statement.setString(parameterIndex++, pattern);
        }
        return parameterIndex;
    }

    private String cleanSearchText(String searchText) {
        return searchText == null ? "" : searchText.trim().toLowerCase();
    }

    private String cleanFilterText(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeOrderColumn(String orderByColumn) {
        if (orderByColumn != null && ALLOWED_ORDER_COLUMNS.contains(orderByColumn)) {
            return orderByColumn;
        }
        return "C.RAGIONE_SOCIALE";
    }

    private UUID getUuid(ResultSet resultSet, String column) throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
